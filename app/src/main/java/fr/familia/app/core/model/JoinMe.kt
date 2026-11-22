package fr.familia.app.core.model

import fr.familia.app.core.place.Place
import java.time.Duration
import java.time.Instant

/**
 * A « Rejoignez-moi » (PRD, récit 33): a place sent by [authorId] to [recipientIds], their position
 * of the moment or a place they chose, with an optional [note]. It is never changed; it goes when
 * its author takes it back, and on its own 24 h later.
 */
data class JoinMe(
    val id: String,
    val authorId: String,
    val authorName: String,
    val recipientIds: List<String>,
    val place: Place,
    val note: String?,
    /** Null for a moment after sending offline: the server has not stamped it yet. */
    val createdAt: Instant?,
    val expireAt: Instant,
) {
    /** Firestore deletes it up to a day after [expireAt]: the app hides it on time. */
    fun isActive(now: Instant): Boolean = now.isBefore(expireAt)

    companion object {
        const val MAX_NOTE = 200
        val LIFETIME: Duration = Duration.ofHours(2)
    }
}
