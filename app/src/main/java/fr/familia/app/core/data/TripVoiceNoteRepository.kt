package fr.familia.app.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import fr.familia.app.core.model.Member
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.time.Instant

/**
 * A voice note of a trip (récit 104, règle 35, DESIGN-90 A): recorded during the trip under a
 * destination, at most [TripVoiceNotes.MAX_SECONDS] and [TripVoiceNotes.MAX_BYTES]. It goes with
 * its step (server) and with its trip.
 */
data class TripVoiceNote(
    val id: String,
    val authorId: String,
    val authorName: String,
    val stepId: String,
    val seconds: Int,
    val createdAt: Instant?,
)

object TripVoiceNotes {
    /** As the rules allow (firestore.rules and storage.rules). */
    const val MAX_SECONDS = 120
    const val MAX_BYTES = 2L * 1024 * 1024
    const val CONTENT_TYPE = "audio/mp4"

    /** Where the file of a note is in Storage. */
    fun path(tripId: String, authorId: String, noteId: String): String = "trips/$tripId/voice/$authorId/$noteId.m4a"
}

interface TripVoiceNoteRepository {
    /** The voice notes of the trip, oldest first, updated live; none when they cannot be read. */
    fun observe(tripId: String): Flow<List<TripVoiceNote>>

    /** Sends the file, then writes the note under the destination [stepId]. */
    suspend fun add(tripId: String, me: Member, stepId: String, file: File, seconds: Int)

    /** Where the phone plays it from; null when it is gone. */
    suspend fun playUrl(tripId: String, note: TripVoiceNote): String?

    /** Removes the record; the server removes the file. */
    suspend fun delete(tripId: String, note: TripVoiceNote)
}

class FirestoreTripVoiceNoteRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : TripVoiceNoteRepository {

    private fun notes(tripId: String) = db.collection("trips").document(tripId).collection("voiceNotes")

    override fun observe(tripId: String): Flow<List<TripVoiceNote>> = callbackFlow {
        val registration = notes(tripId).orderBy("createdAt", Query.Direction.ASCENDING).addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    trySend(emptyList())
                    close()
                } else {
                    close(error)
                }
                return@addSnapshotListener
            }
            trySend(
                snapshot?.documents.orEmpty().mapNotNull { doc ->
                    TripVoiceNote(
                        id = doc.id,
                        authorId = doc.getString("authorId") ?: return@mapNotNull null,
                        authorName = doc.getString("authorName").orEmpty(),
                        stepId = doc.getString("stepId") ?: return@mapNotNull null,
                        seconds = doc.getLong("seconds")?.toInt() ?: return@mapNotNull null,
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.toInstant(),
                    )
                },
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun add(tripId: String, me: Member, stepId: String, file: File, seconds: Int) {
        require(file.length() in 1..TripVoiceNotes.MAX_BYTES && seconds in 1..TripVoiceNotes.MAX_SECONDS)
        val ref = notes(tripId).document()
        val metadata = StorageMetadata.Builder().setContentType(TripVoiceNotes.CONTENT_TYPE).build()
        storage.reference.child(TripVoiceNotes.path(tripId, me.id, ref.id)).putBytes(file.readBytes(), metadata).await()
        ref.set(
            mapOf(
                "authorId" to me.id,
                "authorName" to me.firstName,
                "stepId" to stepId,
                "seconds" to seconds.toLong(),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun playUrl(tripId: String, note: TripVoiceNote): String? = try {
        storage.reference.child(TripVoiceNotes.path(tripId, note.authorId, note.id)).downloadUrl.await().toString()
    } catch (e: StorageException) {
        null
    }

    override suspend fun delete(tripId: String, note: TripVoiceNote) {
        notes(tripId).document(note.id).delete().await()
    }
}
