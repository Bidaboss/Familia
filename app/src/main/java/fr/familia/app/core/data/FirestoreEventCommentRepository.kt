package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.EventComment
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.eventCommentOrder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

class FirestoreEventCommentRepository(private val db: FirebaseFirestore) : EventCommentRepository {

    private fun event(eventId: String) = db.collection("events").document(eventId)
    private fun comments(eventId: String) = event(eventId).collection("comments")

    override fun observe(eventId: String, limit: Int): Flow<CommentPage> = callbackFlow {
        // One more than shown tells whether « Voir plus » has something to show.
        val registration = comments(eventId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong() + 1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    val all = snapshot.documents.mapNotNull { it.toComment() }.sortedWith(eventCommentOrder)
                    trySend(CommentPage(all.takeLast(limit), hasMore = all.size > limit))
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun add(eventId: String, text: String, me: Member) {
        val expireAt = Instant.now().plus(EventComment.KEPT_DAYS, ChronoUnit.DAYS)
        comments(eventId).document().set(
            mapOf(
                "text" to text,
                "authorId" to me.id,
                "authorName" to me.firstName,
                "createdAt" to FieldValue.serverTimestamp(),
                "expireAt" to Timestamp(Date.from(expireAt)),
            ),
        ).await()
    }

    override suspend fun edit(eventId: String, commentId: String, text: String) {
        comments(eventId).document(commentId)
            .update(mapOf("text" to text, "editedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override suspend fun delete(eventId: String, commentId: String) {
        comments(eventId).document(commentId).delete().await()
    }

    override fun observeMuted(eventId: String, memberId: String): Flow<Boolean> = callbackFlow {
        val registration = event(eventId).collection("commentMutes").document(memberId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.exists() == true)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setMuted(eventId: String, memberId: String, muted: Boolean) {
        val ref = event(eventId).collection("commentMutes").document(memberId)
        if (muted) ref.set(mapOf("mutedAt" to FieldValue.serverTimestamp())).await() else ref.delete().await()
    }

    private fun DocumentSnapshot.toComment(): EventComment? {
        val text = getString("text") ?: return null
        val authorId = getString("authorId") ?: return null
        return EventComment(
            id = id,
            text = text,
            authorId = authorId,
            authorName = getString("authorName").orEmpty(),
            // A comment just sent offline has no server time yet: the estimate keeps it last.
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.NONE)?.toDate()?.toInstant(),
            editedAt = getTimestamp("editedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.toInstant(),
        )
    }
}
