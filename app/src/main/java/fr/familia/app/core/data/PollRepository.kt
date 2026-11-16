package fr.familia.app.core.data

import fr.familia.app.core.model.Poll
import fr.familia.app.core.model.PollVote
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** polls/{pollId} and their votes (PRD, récits 2 bis et 2 ter). See docs/data-model.md. */
interface PollRepository {
    /** The polls [memberId] sees (viewerIds), newest first, updated live. */
    fun observeMine(memberId: String): Flow<List<Poll>>

    fun observePoll(id: String): Flow<Poll?>

    fun observeVotes(pollId: String): Flow<List<PollVote>>

    /** Asks a new poll and returns its id. */
    suspend fun create(
        kind: Poll.Kind,
        question: String,
        options: List<String>,
        multiple: Boolean,
        askedIds: List<String>,
        viewerIds: List<String>,
        closesAt: Instant?,
        authorId: String,
        authorName: String,
    ): String

    /** The vote of [memberId], written by [votedBy] (themselves or one of their adults). */
    suspend fun vote(pollId: String, vote: PollVote)

    suspend fun close(pollId: String)

    /** Chooses the date [index] and links the event just made for it. */
    suspend fun chooseDate(pollId: String, index: Int, eventId: String)

    suspend fun delete(pollId: String)
}
