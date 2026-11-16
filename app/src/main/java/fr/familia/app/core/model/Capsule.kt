package fr.familia.app.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * A message for later (PRD, récit 39), in capsules/{capsuleId}: a text and up to 3 photos, for
 * chosen members, sealed until [openAt] (Paris day). Only its author reads it until then; the
 * server copies [recipientIds] into [openedFor] that day, and the recipients read it from then on.
 * A capsule never changes; its author may remove it.
 */
data class Capsule(
    val id: String,
    val text: String,
    val photoCount: Int,
    val recipientIds: List<String>,
    val openAt: LocalDate,
    val openedFor: List<String>,
    val openedAt: Instant?,
    val authorId: String,
    /** Kept so the capsule stays signed once its author has left. */
    val authorName: String,
    val createdAt: Instant?,
) {
    val isOpened: Boolean get() = openedFor.isNotEmpty()

    /** Storage path of photo [n] (1 to [photoCount]), or of its thumbnail. */
    fun photoPath(n: Int, thumbnail: Boolean = false): String =
        "capsules/$authorId/$id/$n${if (thumbnail) "-thumb" else ""}.jpg"

    companion object {
        const val MAX_TEXT = 4_000
        const val MAX_PHOTOS = 3

        /** The earliest and latest opening days the rules accept, written on [today]. */
        fun earliestOpening(today: LocalDate): LocalDate = today.plusMonths(1)
        fun latestOpening(today: LocalDate): LocalDate = today.plusYears(5)
    }
}

/**
 * What an author who left the family still has sealed (PRD rév. 184): only a count, written by
 * the server. The admin principal never reads the capsules themselves, and may ask for their
 * deletion ([deleteAsked]); the server then deletes them.
 */
data class CapsuleLeftover(
    val memberId: String,
    val memberName: String,
    val count: Int,
    val deleteAsked: Boolean,
)
