package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import fr.familia.app.core.model.HelpKind
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.HelpStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime

/** Requests live in `helpRequests/{id}`. See docs/data-model.md for the fields and the rights. */
class FirestoreHelpRequestRepository(
    private val db: FirebaseFirestore,
    private val journal: SendJournal? = null,
) : HelpRequestRepository {

    private val refusals = MutableSharedFlow<String?>(extraBufferCapacity = 4)

    override val lateRefusals: Flow<String?> = refusals.asSharedFlow()

    override fun observeMine(memberId: String): Flow<List<HelpRequest>> = callbackFlow {
        // The rules only let a member read the requests where they are in viewerIds.
        val registration = db.collection(REQUESTS)
            .whereArrayContains("viewerIds", memberId)
            // Told again once a write made offline reaches the server, for « En attente du réseau ».
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toRequest() })
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observeRequest(id: String): Flow<HelpRequest?> = callbackFlow {
        val registration = db.collection(REQUESTS).document(id).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                // Out of reach (no longer warned): shown as missing rather than as an error.
                trySend(null)
            } else if (snapshot != null) {
                trySend(snapshot.takeIf { it.exists() }?.toRequest())
            }
        }
        awaitClose { registration.remove() }
    }

    override fun newRequestId(): String = db.collection(REQUESTS).document().id

    override suspend fun saveRequest(request: HelpRequest, isNew: Boolean) {
        val ref = db.collection(REQUESTS).document(request.id)
        val fields = mapOf(
            "kind" to request.kind.firestoreValue,
            "title" to request.title,
            "date" to request.date.toString(),
            "startTime" to request.startTime?.hhmm(),
            "endTime" to request.endTime?.hhmm(),
            "place" to request.place,
            "note" to request.note,
            "photo" to request.photo,
            "notifyIds" to request.notifyIds,
            "viewerIds" to listOf(request.authorId) + request.notifyIds,
            "declinedBy" to request.declinedBy,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isNew) {
            journal.track(SendEntry(ref.path, SendKind.HelpRequest, request.title, request.authorId)) {
                ref.set(
                    fields + mapOf(
                        "authorId" to request.authorId,
                        "authorName" to request.authorName,
                        "forMemberId" to request.forMemberId,
                        "status" to HelpStatus.Open.firestoreValue,
                        "volunteerId" to null,
                        "volunteerName" to null,
                        "thankedAt" to null,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
        } else {
            ref.update(fields).await()
        }
    }

    override suspend fun take(id: String, volunteerId: String, volunteerName: String, note: String?) {
        val ref = db.collection(REQUESTS).document(id)
        val fields = mapOf(
            "status" to HelpStatus.Taken.firestoreValue,
            "volunteerId" to volunteerId,
            "volunteerName" to volunteerName,
            "volunteerNote" to note,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        // Online, a transaction reads the server first: of two volunteers only the first wins, and
        // the other hears it at once. Without network, or on one too slow, the same write waits in
        // the cache and goes by itself, once, when the network is back; the rules (isHelpTake: still
        // open) refuse it then if someone took the request meanwhile, so never two volunteers.
        try {
            val done = withTimeoutOrNull(TAKE_ONLINE_MILLIS) {
                runTake(ref, fields)
                true
            }
            if (done == null) queueTake(ref, fields)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Firestore may wrap the exception thrown inside the transaction.
            generateSequence<Throwable>(e) { it.cause }.filterIsInstance<AlreadyTakenException>().firstOrNull()?.let { throw it }
            if (!e.isOffline()) throw e
            queueTake(ref, fields)
        }
    }

    private suspend fun runTake(ref: DocumentReference, fields: Map<String, Any?>) {
        db.runTransaction { tx ->
            val snapshot = tx.get(ref)
            if (snapshot.getString("status") != HelpStatus.Open.firestoreValue) {
                throw AlreadyTakenException(snapshot.getString("volunteerName"))
            }
            tx.update(ref, fields)
        }.await()
    }

    /** « Je m'en occupe » kept on this phone: open as last seen here, it waits for the network. */
    private suspend fun queueTake(ref: DocumentReference, fields: Map<String, Any?>) {
        val cached = runCatching { ref.get(Source.CACHE).await() }.getOrNull()
        if (cached != null && cached.exists() && cached.getString("status") != HelpStatus.Open.firestoreValue) {
            throw AlreadyTakenException(cached.getString("volunteerName"))
        }
        // Not awaited: the task ends only once the server has it. The request shows « En attente ».
        // A transaction cut by the timeout of take() still runs and may win first: this write is then
        // refused too (isHelpTake wants an open request). Not a bug: the volunteer is the same, so
        // nothing is told, and the cache goes back to what the server holds.
        val myId = fields["volunteerId"]
        ref.update(fields).addOnFailureListener { e ->
            if (e !is FirebaseFirestoreException || e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) return@addOnFailureListener
            ref.get(Source.CACHE).addOnCompleteListener { read ->
                val now = if (read.isSuccessful) read.result else null
                // Removed meanwhile: nothing to tell.
                if (now != null && !now.exists()) return@addOnCompleteListener
                if (now?.getString("volunteerId") != myId) refusals.tryEmit(now?.getString("volunteerName"))
            }
        }
    }

    override suspend fun withdraw(id: String) = update(
        id,
        "status" to HelpStatus.Open.firestoreValue,
        "volunteerId" to null,
        "volunteerName" to null,
        "volunteerNote" to null,
    )

    override suspend fun setVolunteerNote(id: String, note: String?) = update(id, "volunteerNote" to note)

    override suspend fun decline(id: String, memberId: String) = update(id, "declinedBy" to memberId)

    override suspend fun markUnavailable(id: String, memberId: String) = update(id, "unavailableIds" to FieldValue.arrayUnion(memberId))

    override suspend fun thank(id: String) = update(id, "thankedAt" to FieldValue.serverTimestamp())

    override suspend fun cancel(id: String) = update(id, "status" to HelpStatus.Cancelled.firestoreValue)

    private suspend fun update(id: String, vararg fields: Pair<String, Any?>) {
        db.collection(REQUESTS).document(id)
            .update(mapOf(*fields) + ("updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    /** Null for a document that does not have the expected shape (never shown rather than crash). */
    private fun DocumentSnapshot.toRequest(): HelpRequest? {
        val date = getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val notifyIds = (get("notifyIds") as? List<*>)?.filterIsInstance<String>() ?: return null
        return HelpRequest(
            id = id,
            kind = HelpKind.fromFirestore(getString("kind")) ?: HelpKind.Other,
            title = getString("title").orEmpty(),
            date = date,
            startTime = getString("startTime")?.toTime(),
            endTime = getString("endTime")?.toTime(),
            place = getString("place"),
            note = getString("note"),
            authorId = getString("authorId") ?: return null,
            authorName = getString("authorName").orEmpty(),
            forMemberId = getString("forMemberId") ?: return null,
            notifyIds = notifyIds,
            status = HelpStatus.fromFirestore(getString("status")) ?: return null,
            volunteerId = getString("volunteerId"),
            volunteerName = getString("volunteerName"),
            volunteerNote = getString("volunteerNote"),
            declinedBy = getString("declinedBy"),
            unavailableIds = (get("unavailableIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            thankedAt = getTimestamp("thankedAt")?.toInstant(),
            createdAt = getTimestamp("createdAt")?.toInstant(),
            pending = metadata.hasPendingWrites(),
            photo = getString("photo"),
        )
    }

    private fun String.toTime(): LocalTime? = runCatching { LocalTime.parse(this) }.getOrNull()

    private fun LocalTime.hhmm(): String = LocalTime.of(hour, minute).toString() // Always HH:mm.

    private companion object {
        const val REQUESTS = "helpRequests"

        /** Past this, a network too slow for the transaction is treated as none: the write waits in the cache. */
        const val TAKE_ONLINE_MILLIS = 5_000L
    }
}

/** The client is offline: Firestore cannot reach the server for a read or a transaction. */
private fun Throwable.isOffline(): Boolean =
    generateSequence(this) { it.cause }.any { it is FirebaseFirestoreException && it.code == FirebaseFirestoreException.Code.UNAVAILABLE }
