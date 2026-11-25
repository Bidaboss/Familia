package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant

/** The four J'aime of the gazette (PRD 119 f): 💛 is the plain « J'aime », the others by a long press. */
val GazetteReactions = listOf("💛", "🎉", "👏", "🔥")

/** One of the 2 latest comments of a card, as the server keeps them on its thread. */
data class GazetteLastComment(val id: String, val authorId: String, val authorName: String, val text: String, val at: Instant?)

/**
 * The J'aime and the comments of one card of the gazette (gazetteThreads/{threadId}, récits 115
 * and 116): who reacted and how, how many comments, the 2 latest. Empty while nobody reacted nor
 * commented (the document does not exist yet).
 */
data class GazetteThread(
    val reactions: Map<String, String> = emptyMap(),
    val commentCount: Int = 0,
    val last: List<GazetteLastComment> = emptyList(),
) {
    fun reactionOf(memberId: String): String? = reactions[memberId]
}

/** The thread of a card: its key with « / » turned into « : » (the rules read its kind there). */
fun gazetteThreadId(cardKey: String): String = cardKey.replace('/', ':')

/** See docs/data-model.md, « La gazette : J'aime et commentaires des cartes ». */
interface GazetteThreadRepository {
    fun observe(threadId: String): Flow<GazetteThread>

    /** Sets my J'aime to [reaction], or removes it (null). */
    suspend fun react(threadId: String, memberId: String, reaction: String?)
}

class FirestoreGazetteThreadRepository(private val db: FirebaseFirestore) : GazetteThreadRepository {
    private fun thread(threadId: String) = db.collection("gazetteThreads").document(threadId)

    override fun observe(threadId: String): Flow<GazetteThread> = callbackFlow {
        val registration = thread(threadId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.toThread() ?: GazetteThread())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun react(threadId: String, memberId: String, reaction: String?) {
        require(reaction == null || reaction in GazetteReactions)
        val ref = thread(threadId)
        if (reaction != null) {
            // set + merge creates the thread at the first J'aime, and changes only my own entry.
            ref.set(mapOf("reactions" to mapOf(memberId to reaction)), SetOptions.merge()).await()
        } else {
            ref.update(FieldPath.of("reactions", memberId), FieldValue.delete()).await()
        }
    }
}

internal fun DocumentSnapshot.toThread(): GazetteThread {
    if (!exists()) return GazetteThread()
    @Suppress("UNCHECKED_CAST")
    val reactions = (get("reactions") as? Map<String, Any?>).orEmpty()
        .mapNotNull { (id, value) -> (value as? String)?.takeIf { it in GazetteReactions }?.let { id to it } }
        .toMap()
    @Suppress("UNCHECKED_CAST")
    val last = (get("last") as? List<Map<String, Any?>>).orEmpty().mapNotNull { row ->
        val id = row["id"] as? String ?: return@mapNotNull null
        GazetteLastComment(
            id = id,
            authorId = row["authorId"] as? String ?: "",
            authorName = row["authorName"] as? String ?: "",
            text = row["text"] as? String ?: "",
            at = (row["createdAt"] as? Timestamp)?.toDate()?.toInstant(),
        )
    }
    return GazetteThread(reactions, (getLong("commentCount") ?: 0L).toInt(), last)
}
