package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.TripWord
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** `trips/{tripId}/words/{stepId}`, and under it the comments, as for an event (they do not expire). */
class FirestoreTripWordRepository(private val db: FirebaseFirestore) : TripWordRepository {

    private fun words(tripId: String) = db.collection("trips").document(tripId).collection("words")

    override fun observeWords(tripId: String): Flow<Map<String, TripWord>> = callbackFlow {
        val registration = words(tripId).addSnapshotListener { snapshot, error ->
            when {
                // Not a follower (any more): there is nothing to show, not a failure.
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    trySend(emptyMap())
                    close()
                }
                error != null -> close(error)
                snapshot != null -> trySend(snapshot.documents.mapNotNull { it.toWord() }.associateBy { it.stepId })
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeWord(tripId: String, stepId: String): Flow<TripWord?> = callbackFlow {
        val registration = words(tripId).document(stepId).addSnapshotListener { snapshot, error ->
            when {
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    trySend(null)
                    close()
                }
                error != null -> close(error)
                snapshot != null -> trySend(snapshot.takeIf { it.exists() }?.toWord())
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun saveWord(tripId: String, stepId: String, text: String, photoIds: List<String>, me: Member, isNew: Boolean) {
        val ref = words(tripId).document(stepId)
        if (isNew) {
            ref.set(
                mapOf(
                    "text" to text,
                    "photoIds" to photoIds,
                    "authorId" to me.id,
                    "authorName" to me.firstName,
                    "hearts" to emptyMap<String, Boolean>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } else {
            // Only the text and the photos: the hearts are other people's.
            ref.update(mapOf("text" to text, "photoIds" to photoIds, "updatedAt" to FieldValue.serverTimestamp())).await()
        }
    }

    override suspend fun deleteWord(tripId: String, stepId: String) {
        words(tripId).document(stepId).delete().await()
    }

    override suspend fun setHeart(tripId: String, stepId: String, memberId: String, hearted: Boolean) {
        // The key is a member id, not a path: FieldPath keeps it whole.
        val value: Any = if (hearted) true else FieldValue.delete()
        words(tripId).document(stepId).update(FieldPath.of("hearts", memberId), value).await()
    }

    override fun comments(tripId: String, stepId: String): EventCommentRepository =
        FirestoreEventCommentRepository(db, parent = "trips/$tripId/words", expires = false)

    private fun DocumentSnapshot.toWord(): TripWord? {
        val text = getString("text") ?: return null
        val authorId = getString("authorId") ?: return null
        return TripWord(
            stepId = id,
            text = text,
            photoIds = (get("photoIds") as? List<*>).orEmpty().filterIsInstance<String>(),
            authorId = authorId,
            authorName = getString("authorName").orEmpty(),
            heartIds = (get("hearts") as? Map<*, *>).orEmpty().filter { it.value == true }.keys.filterIsInstance<String>().toSet(),
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.toInstant(),
        )
    }
}
