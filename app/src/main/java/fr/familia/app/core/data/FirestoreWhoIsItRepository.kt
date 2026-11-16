package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.WhoIsItAnswer
import fr.familia.app.core.model.WhoIsItGuess
import fr.familia.app.core.model.WhoIsItPost
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * `whoIsIt/{postId}`, with `answer/main` and `guesses/{memberId}` that the rules open only to the
 * author and to those who guessed: a refusal there means « not yet », never an error.
 */
class FirestoreWhoIsItRepository(private val db: FirebaseFirestore) : WhoIsItRepository {

    private fun post(id: String) = db.collection(WHO_IS_IT).document(id)

    override fun observeLatest(count: Int): Flow<List<WhoIsItPost>> = callbackFlow {
        val registration = db.collection(WHO_IS_IT).orderBy("createdAt", Query.Direction.DESCENDING).limit(count.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error) else if (snapshot != null) trySend(snapshot.documents.mapNotNull { it.toPost() })
            }
        awaitClose { registration.remove() }
    }

    override fun observePost(id: String): Flow<WhoIsItPost?> = callbackFlow {
        val registration = post(id).addSnapshotListener { snapshot, error ->
            when {
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> trySend(null)
                error != null -> close(error)
                else -> trySend(snapshot?.takeIf { it.exists() }?.toPost())
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeAnswer(id: String): Flow<WhoIsItAnswer?> = callbackFlow {
        val registration = post(id).collection(ANSWER).document(MAIN).addSnapshotListener { snapshot, error ->
            when {
                // Not guessed yet: the answer stays hidden. The guess reopens the listener.
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> trySend(null)
                error != null -> close(error)
                else -> trySend(snapshot?.takeIf { it.exists() }?.let { doc ->
                    WhoIsItAnswer(doc.getString("who").orEmpty(), doc.getLong("year")?.toInt(), doc.getString("personId"))
                })
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeGuesses(id: String): Flow<List<WhoIsItGuess>> = callbackFlow {
        val registration = post(id).collection(GUESSES).addSnapshotListener { snapshot, error ->
            when {
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> trySend(emptyList())
                error != null -> close(error)
                snapshot != null -> trySend(
                    snapshot.documents.map { doc ->
                        WhoIsItGuess(doc.id, doc.getString("name").orEmpty(), doc.getString("who").orEmpty(), doc.getLong("year")?.toInt())
                    }.sortedBy { it.name.lowercase() },
                )
            }
        }
        awaitClose { registration.remove() }
    }

    override fun newPostId(): String = db.collection(WHO_IS_IT).document().id

    override suspend fun publish(post: WhoIsItPost, answer: WhoIsItAnswer) {
        val batch = db.batch()
        batch.set(
            post(post.id),
            mapOf("authorId" to post.authorId, "authorName" to post.authorName, "hint" to post.hint, "createdAt" to FieldValue.serverTimestamp()),
        )
        batch.set(post(post.id).collection(ANSWER).document(MAIN), answer.toMap())
        batch.commit().await()
    }

    override suspend fun setAnswer(id: String, answer: WhoIsItAnswer) {
        post(id).collection(ANSWER).document(MAIN).set(answer.toMap()).await()
    }

    override suspend fun guess(id: String, guess: WhoIsItGuess) {
        post(id).collection(GUESSES).document(guess.memberId).set(
            mapOf("who" to guess.who, "year" to guess.year?.toLong(), "name" to guess.name, "createdAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    override suspend fun delete(id: String) {
        post(id).delete().await()
    }

    private fun WhoIsItAnswer.toMap(): Map<String, Any?> = buildMap {
        put("who", who)
        put("year", year?.toLong())
        personId?.let { put("personId", it) }
    }

    private fun DocumentSnapshot.toPost(): WhoIsItPost? {
        val authorId = getString("authorId") ?: return null
        return WhoIsItPost(id, authorId, getString("authorName").orEmpty(), getString("hint").orEmpty(), getTimestamp("createdAt")?.toDate()?.toInstant())
    }

    private companion object {
        const val WHO_IS_IT = "whoIsIt"
        const val ANSWER = "answer"
        const val MAIN = "main"
        const val GUESSES = "guesses"
    }
}
