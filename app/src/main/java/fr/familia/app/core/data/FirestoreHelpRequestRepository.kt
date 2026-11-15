package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.HelpKind
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.HelpStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime

/** Requests live in `helpRequests/{id}`. See docs/data-model.md for the fields and the rights. */
class FirestoreHelpRequestRepository(private val db: FirebaseFirestore) : HelpRequestRepository {

    override fun observeMine(memberId: String): Flow<List<HelpRequest>> = callbackFlow {
        // The rules only let a member read the requests where they are in viewerIds.
        val registration = db.collection(REQUESTS)
            .whereArrayContains("viewerIds", memberId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toRequest() })
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observeRequest(id: String): Flow<HelpRequest?> = callbackFlow {
        val registration = db.collection(REQUESTS).document(id).addSnapshotListener { snapshot, error ->
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
            "notifyIds" to request.notifyIds,
            "viewerIds" to listOf(request.authorId) + request.notifyIds,
            "declinedBy" to request.declinedBy,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isNew) {
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
            ).await()
        } else {
            ref.update(fields).await()
        }
    }

    override suspend fun take(id: String, volunteerId: String, volunteerName: String) {
        val ref = db.collection(REQUESTS).document(id)
        // A transaction only runs online: it reads the server, so two volunteers can never both win.
        try {
            runTake(ref, volunteerId, volunteerName)
        } catch (e: Exception) {
            // Firestore may wrap the exception thrown inside the transaction.
            generateSequence<Throwable>(e) { it.cause }.filterIsInstance<AlreadyTakenException>().firstOrNull()?.let { throw it }
            throw e
        }
    }

    private suspend fun runTake(ref: com.google.firebase.firestore.DocumentReference, volunteerId: String, volunteerName: String) {
        db.runTransaction { tx ->
            val snapshot = tx.get(ref)
            if (snapshot.getString("status") != HelpStatus.Open.firestoreValue) {
                throw AlreadyTakenException(snapshot.getString("volunteerName"))
            }
            tx.update(
                ref,
                mapOf(
                    "status" to HelpStatus.Taken.firestoreValue,
                    "volunteerId" to volunteerId,
                    "volunteerName" to volunteerName,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
        }.await()
    }

    override suspend fun withdraw(id: String) = update(
        id,
        "status" to HelpStatus.Open.firestoreValue,
        "volunteerId" to null,
        "volunteerName" to null,
    )

    override suspend fun decline(id: String, memberId: String) = update(id, "declinedBy" to memberId)

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
            declinedBy = getString("declinedBy"),
            thankedAt = getTimestamp("thankedAt")?.toInstant(),
            createdAt = getTimestamp("createdAt")?.toInstant(),
        )
    }

    private fun String.toTime(): LocalTime? = runCatching { LocalTime.parse(this) }.getOrNull()

    private fun LocalTime.hhmm(): String = LocalTime.of(hour, minute).toString() // Always HH:mm.

    private companion object {
        const val REQUESTS = "helpRequests"
    }
}
