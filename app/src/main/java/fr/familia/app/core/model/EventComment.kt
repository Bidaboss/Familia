package fr.familia.app.core.model

import java.time.Instant

/**
 * A comment under an event (PRD, récit 28), in events/{eventId}/comments/{commentId}. Firestore
 * deletes it 12 months after it was written (expireAt, TTL), and with its event.
 */
data class EventComment(
    val id: String,
    val text: String,
    val authorId: String,
    /** Kept so the comment stays signed once its author has left. */
    val authorName: String,
    /** Null just after this phone sent it, until the server time arrives. */
    val createdAt: Instant?,
    /** Set when its author changed the text. */
    val editedAt: Instant? = null,
) {
    companion object {
        const val MAX_TEXT = 500

        /** Comments shown at first, then 30 more with each « Voir plus ». */
        const val PAGE = 30

        /** How long Firestore keeps a comment (the rules accept 360 to 370 days). */
        const val KEPT_DAYS = 365L
    }
}

/** Oldest first; a comment just sent from this phone, with no server time yet, comes last. */
val eventCommentOrder: Comparator<EventComment> =
    compareBy<EventComment> { it.createdAt ?: Instant.MAX }.thenBy { it.id }

/** Its author changes the text. */
fun canEditComment(comment: EventComment, me: Member): Boolean = comment.authorId == me.id

/**
 * Its author removes it, as do the admin principal and a chef de famille of the author's household
 * (the security rules check the same). Once the author has left, only the admin principal.
 */
fun Family.canRemoveComment(comment: EventComment, me: Member): Boolean {
    if (comment.authorId == me.id || isOwner(me)) return true
    val author = members.firstOrNull { it.id == comment.authorId } ?: return false
    return me.isAdmin && me.householdId == author.householdId
}
