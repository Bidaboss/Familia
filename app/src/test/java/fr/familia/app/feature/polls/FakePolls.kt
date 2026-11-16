package fr.familia.app.feature.polls

import fr.familia.app.core.data.PollRepository
import fr.familia.app.core.model.Poll
import fr.familia.app.core.model.PollVote
import fr.familia.app.core.model.pollViewers
import fr.familia.app.feature.events.TestFamily
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** A fictional poll by [authorId], asking [askedIds]. */
fun poll(
    id: String,
    authorId: String = "adult1",
    askedIds: List<String> = listOf("admin1", "adult1", "adult2", "kid1", "kid2"),
    kind: Poll.Kind = Poll.Kind.Date,
    options: List<String> = if (kind == Poll.Kind.Date) listOf("2026-12-19", "2026-12-20 12:30") else listOf("Pizza", "Crêpes", "Raclette"),
    multiple: Boolean = false,
    closesAt: Instant? = null,
    closed: Boolean = false,
    chosen: Int? = null,
) = Poll(
    id, kind, "Question $id", options, multiple, askedIds, pollViewers(authorId, askedIds, TestFamily.family.members), null, closesAt, closed, chosen,
    null, authorId, authorId.replaceFirstChar { it.uppercase() }, Instant.parse("2026-11-20T10:00:00Z"),
)

class FakePolls(initial: List<Poll> = emptyList(), private val failWith: Exception? = null) : PollRepository {
    val polls = MutableStateFlow(initial)
    val votes = MutableStateFlow<Map<String, List<PollVote>>>(emptyMap())
    val created = mutableListOf<Poll>()
    val chosen = mutableListOf<Triple<String, Int, String>>()

    override fun observeMine(memberId: String): Flow<List<Poll>> = polls.map { list -> list.filter { memberId in it.viewerIds } }
    override fun observePoll(id: String): Flow<Poll?> = polls.map { list -> list.firstOrNull { it.id == id } }
    override fun observeVotes(pollId: String): Flow<List<PollVote>> = votes.map { it[pollId].orEmpty() }

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
        failWith?.let { throw it }
        val poll = Poll("p-new", kind, question, options, multiple, askedIds, viewerIds, null, closesAt, false, null, null, authorId, authorName, null)
        created += poll
        polls.value = polls.value + poll
        return poll.id
    }

    override suspend fun vote(pollId: String, vote: PollVote) {
        failWith?.let { throw it }
        val list = votes.value[pollId].orEmpty().filter { it.memberId != vote.memberId } + vote
        votes.value = votes.value + (pollId to list)
    }

    override suspend fun close(pollId: String) = change(pollId) { it.copy(closed = true) }

    override suspend fun chooseDate(pollId: String, index: Int, eventId: String) {
        chosen += Triple(pollId, index, eventId)
        change(pollId) { it.copy(closed = true, chosen = index, createdEventId = eventId) }
    }

    override suspend fun delete(pollId: String) {
        failWith?.let { throw it }
        polls.value = polls.value.filter { it.id != pollId }
    }

    private fun change(id: String, block: (Poll) -> Poll) {
        failWith?.let { throw it }
        polls.value = polls.value.map { if (it.id == id) block(it) else it }
    }
}
