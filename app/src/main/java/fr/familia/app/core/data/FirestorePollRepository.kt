package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.DateAnswer
import fr.familia.app.core.model.Poll
import fr.familia.app.core.model.PollVote
import java.time.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestorePollRepository(private val db: FirebaseFirestore) : PollRepository {

    private val polls get() = db.collection("polls")

    override fun observeMine(memberId: String): Flow<List<Poll>> = callbackFlow {
        val registration = polls.whereArrayContains("viewerIds", memberId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    // Sorted here: ordering in the query would need a composite index.
                    trySend(snapshot.documents.mapNotNull { it.toPoll() }.sortedByDescending { it.createdAt ?: Instant.MAX })
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observePoll(id: String): Flow<Poll?> = callbackFlow {
        val registration = polls.document(id).addSnapshotListener { snapshot, error ->
            if (error != null) trySend(null) else if (snapshot != null) trySend(snapshot.takeIf { it.exists() }?.toPoll())
        }
        awaitClose { registration.remove() }
    }

    override fun observeVotes(pollId: String): Flow<List<PollVote>> = callbackFlow {
        val registration = polls.document(pollId).collection("votes").addSnapshotListener { snapshot, error ->
            if (error != null) trySend(emptyList()) else if (snapshot != null) trySend(snapshot.documents.mapNotNull { it.toVote() })
        }
        awaitClose { registration.remove() }
    }

    override suspend fun create(
        kind: Poll.Kind,
        question: String,
        options: List<String>,
        multiple: Boolean,
        askedIds: List<String>,
        viewerIds: List<String>,
        closesAt: Instant?,
        authorId: String,
        authorName: String,
    ): String {
        val ref = polls.document()
        ref.set(
            mapOf(
                "kind" to kind.key,
                "question" to question,
                "options" to options,
                "multiple" to (kind == Poll.Kind.Choice && multiple),
                "askedIds" to askedIds,
                "viewerIds" to viewerIds,
                "eventId" to null,
                "closesAt" to closesAt?.let { Timestamp(it.epochSecond, it.nano) },
                "closed" to false,
                "chosen" to null,
                "createdEventId" to null,
                "authorId" to authorId,
                "authorName" to authorName,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return ref.id
    }

    override suspend fun vote(pollId: String, vote: PollVote) {
        val poll = polls.document(pollId).get().await().toPoll() ?: return
        val data = mutableMapOf<String, Any>(
            "memberId" to vote.memberId,
            "votedBy" to vote.votedBy,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (poll.kind == Poll.Kind.Date) data["answers"] = vote.answers.map { it.key } else data["choices"] = vote.choices
        polls.document(pollId).collection("votes").document(vote.memberId).set(data).await()
    }

    override suspend fun close(pollId: String) {
        polls.document(pollId).update(mapOf("closed" to true, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun chooseDate(pollId: String, index: Int, eventId: String) {
        polls.document(pollId).update(
            mapOf("closed" to true, "chosen" to index, "createdEventId" to eventId, "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    override suspend fun delete(pollId: String) {
        polls.document(pollId).delete().await()
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toPoll(): Poll? {
        val kind = Poll.Kind.entries.firstOrNull { it.key == getString("kind") } ?: return null
        val question = getString("question") ?: return null
        return Poll(
            id = id,
            kind = kind,
            question = question,
            options = (get("options") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            multiple = getBoolean("multiple") == true,
            askedIds = (get("askedIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            viewerIds = (get("viewerIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            eventId = getString("eventId"),
            closesAt = getTimestamp("closesAt")?.toInstant(),
            closed = getBoolean("closed") == true,
            chosen = getLong("chosen")?.toInt(),
            createdEventId = getString("createdEventId"),
            authorId = getString("authorId") ?: return null,
            authorName = getString("authorName").orEmpty(),
            createdAt = getTimestamp("createdAt")?.toInstant(),
        )
    }

    private fun DocumentSnapshot.toVote(): PollVote? = PollVote(
        memberId = getString("memberId") ?: id,
        answers = (get("answers") as? List<*>)?.mapNotNull { a -> DateAnswer.entries.firstOrNull { it.key == a } }.orEmpty(),
        choices = (get("choices") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty(),
        votedBy = getString("votedBy").orEmpty(),
    )
}
