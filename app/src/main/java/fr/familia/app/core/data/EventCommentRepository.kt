package fr.familia.app.core.data

import fr.familia.app.core.model.EventComment
import fr.familia.app.core.model.Member
import kotlinx.coroutines.flow.Flow

/** A page of comments: [comments] oldest first, [hasMore] when older ones wait behind « Voir plus ». */
data class CommentPage(val comments: List<EventComment>, val hasMore: Boolean)

/**
 * events/{eventId}/comments and events/{eventId}/commentMutes (PRD, récit 28). See
 * docs/data-model.md.
 */
interface EventCommentRepository {
    /** The [limit] most recent comments of an event, oldest first, updated live. */
    fun observe(eventId: String, limit: Int): Flow<CommentPage>

    /** Adds a comment signed by [me]. */
    suspend fun add(eventId: String, text: String, me: Member)

    /** Its author changes the text. */
    suspend fun edit(eventId: String, commentId: String, text: String)

    suspend fun delete(eventId: String, commentId: String)

    /** True when [memberId] chose « Ne plus être prévenu » for this event. */
    fun observeMuted(eventId: String, memberId: String): Flow<Boolean>

    suspend fun setMuted(eventId: String, memberId: String, muted: Boolean)
}
