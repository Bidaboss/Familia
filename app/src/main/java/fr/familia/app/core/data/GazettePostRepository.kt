package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.GazettePost
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.Date

/** Les publications de la gazette (gazettePosts, récit 117). See docs/data-model.md. */
interface GazettePostRepository {
    /** Those published since [since], newest first, [limit] at most, updated live (offline cache too). */
    fun observeSince(since: Instant, limit: Int): Flow<List<GazettePost>>

    /** One publication, updated live; null once removed or malformed. */
    fun observePost(id: String): Flow<GazettePost?>

    /** A fresh id for a publication or a photo not saved yet. */
    fun newId(): String

    /** Writes a new publication, once its photos are in Storage. */
    suspend fun publish(post: GazettePost)

    /** Its author changes the text and the members named (« modifié »). */
    suspend fun edit(id: String, text: String, mentionIds: List<String>)

    /** Removes it: the server then removes its photos, its J'aime and its comments. */
    suspend fun remove(id: String)
}

class FirestoreGazettePostRepository(private val db: FirebaseFirestore) : GazettePostRepository {
    private fun posts() = db.collection(POSTS)

    override fun observeSince(since: Instant, limit: Int): Flow<List<GazettePost>> = callbackFlow {
        val registration = posts()
            .whereGreaterThanOrEqualTo("createdAt", Timestamp(Date.from(since)))
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            // Told again once a publication sent offline reaches the server.
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toGazettePost() })
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observePost(id: String): Flow<GazettePost?> = callbackFlow {
        val registration = posts().document(id).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                trySend(null)
            } else if (snapshot != null) {
                trySend(snapshot.takeIf { it.exists() }?.toGazettePost())
            }
        }
        awaitClose { registration.remove() }
    }

    override fun newId(): String = posts().document().id

    override suspend fun publish(post: GazettePost) {
        posts().document(post.id).set(
            mapOf(
                "authorId" to post.authorId,
                "authorName" to post.authorName,
                "text" to post.text,
                "photoIds" to post.photoIds,
                "mentionIds" to post.mentionIds,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun edit(id: String, text: String, mentionIds: List<String>) {
        posts().document(id).update(
            mapOf("text" to text, "mentionIds" to mentionIds, "editedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    override suspend fun remove(id: String) {
        posts().document(id).delete().await()
    }

    private companion object {
        const val POSTS = "gazettePosts"
    }
}

/** Null when a field is missing or of the wrong type, so one bad document never breaks the gazette. */
internal fun DocumentSnapshot.toGazettePost(): GazettePost? {
    val text = getString("text") ?: return null
    val photoIds = (get("photoIds") as? List<*>)?.filterIsInstance<String>() ?: return null
    if (text.isBlank() && photoIds.isEmpty()) return null
    return GazettePost(
        id = id,
        authorId = getString("authorId") ?: return null,
        authorName = getString("authorName").orEmpty(),
        text = text,
        photoIds = photoIds,
        mentionIds = (get("mentionIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        // An estimate while the server time is pending, so a new publication shows at the top.
        createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant(),
        editedAt = getTimestamp("editedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant(),
        pending = metadata.hasPendingWrites() && getTimestamp("createdAt") == null,
    )
}
