package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Reaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.Date

/** Good news live in `goodNews/{id}`. See docs/data-model.md for the fields and the rights. */
class FirestoreGoodNewsRepository(private val db: FirebaseFirestore) : GoodNewsRepository {

    override fun observeLatest(count: Int): Flow<List<GoodNews>> = callbackFlow {
        val registration = db.collection(GOOD_NEWS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(count.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toGoodNews() })
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observeBetween(from: Instant, to: Instant): Flow<List<GoodNews>> = callbackFlow {
        val registration = db.collection(GOOD_NEWS)
            .whereGreaterThanOrEqualTo("createdAt", Timestamp(Date.from(from)))
            .whereLessThan("createdAt", Timestamp(Date.from(to)))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toGoodNews() })
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observePost(id: String): Flow<GoodNews?> = callbackFlow {
        val registration = db.collection(GOOD_NEWS).document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(null)
            } else if (snapshot != null) {
                trySend(snapshot.takeIf { it.exists() }?.toGoodNews())
            }
        }
        awaitClose { registration.remove() }
    }

    override fun newId(): String = db.collection(GOOD_NEWS).document().id

    override suspend fun publish(news: GoodNews) {
        db.collection(GOOD_NEWS).document(news.id).set(
            mapOf(
                "text" to news.text,
                "photoIds" to news.photoIds,
                "authorId" to news.authorId,
                "authorName" to news.authorName,
                "forMemberId" to news.forMemberId,
                "householdId" to news.householdId,
                "mentionIds" to news.mentionIds,
                "reactions" to emptyMap<String, String>(),
                "hiddenByIds" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ) + listOfNotNull(news.forChallenge?.let { "forChallenge" to it }),
        ).await()
    }

    override suspend fun edit(id: String, text: String, mentionIds: List<String>) {
        db.collection(GOOD_NEWS).document(id).update(
            mapOf("text" to text, "mentionIds" to mentionIds, "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    override suspend fun react(id: String, memberId: String, reaction: Reaction?) {
        // Works offline: the reaction shows at once and reaches the server later.
        db.collection(GOOD_NEWS).document(id)
            .update("reactions.$memberId", reaction?.firestoreValue ?: FieldValue.delete())
            .await()
    }

    override suspend fun setHidden(id: String, memberId: String, hidden: Boolean) {
        val change = if (hidden) FieldValue.arrayUnion(memberId) else FieldValue.arrayRemove(memberId)
        db.collection(GOOD_NEWS).document(id).update("hiddenByIds", change).await()
    }

    override suspend fun remove(id: String) {
        db.collection(GOOD_NEWS).document(id).delete().await()
    }

    private companion object {
        const val GOOD_NEWS = "goodNews"
    }
}

/** Null when a field is missing or of the wrong type, so one bad document never breaks the feed. */
private fun DocumentSnapshot.toGoodNews(): GoodNews? {
    val text = getString("text") ?: return null
    // Only the « Défi réussi » (récit 35) and the « Joyeux anniversaire » (récit 36) of Familia come without a photo.
    val challengeMonth = getString("challengeMonth")
    val birthdayOf = getString("birthdayOf")
    val photoIds = (get("photoIds") as? List<*>)?.filterIsInstance<String>()?.takeIf { it.isNotEmpty() || challengeMonth != null || birthdayOf != null } ?: return null
    val reactions = (get("reactions") as? Map<*, *>).orEmpty().mapNotNull { (who, value) ->
        val reaction = Reaction.fromFirestore(value)
        if (who is String && reaction != null) who to reaction else null
    }.toMap()
    return GoodNews(
        id = id,
        text = text,
        photoIds = photoIds,
        authorId = getString("authorId") ?: return null,
        authorName = getString("authorName").orEmpty(),
        forMemberId = getString("forMemberId") ?: return null,
        householdId = getString("householdId").orEmpty(),
        mentionIds = (get("mentionIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        reactions = reactions,
        hiddenByIds = (get("hiddenByIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        // An estimate while the server time is pending, so a new good news shows at the top.
        createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant(),
        updatedAt = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant(),
        challengeMonth = challengeMonth,
        forChallenge = getString("forChallenge"),
        birthdayOf = birthdayOf,
    )
}
