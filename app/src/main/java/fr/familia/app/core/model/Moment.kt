package fr.familia.app.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * « Pensez à moi » (PRD, récit 43): an important moment of [forMemberId] on [day]. [pending] while a
 * teenager of 15 or more has not accepted what a parent noted. [startedAt] is set by Familia on
 * the morning of the day, which opens the encouragements for 24 h.
 */
data class Moment(
    val id: String,
    val forMemberId: String,
    val authorId: String,
    val authorName: String,
    val title: String,
    val day: LocalDate,
    val pending: Boolean = false,
    val startedAt: Instant? = null,
) {
    /** The day: encouragements may be written (the rules say the same). */
    fun isOpen(now: Instant): Boolean = !pending && startedAt != null && startedAt.isAfter(now.minus(Duration.ofHours(24)))

    companion object {
        const val TITLE_MAX = 80

        /** From 15 years old, a teenager notes or accepts their moments themselves. */
        fun isTeenOrOlder(member: Member, today: LocalDate): Boolean {
            val birth = member.birthDate ?: return false
            return !birth.plusYears(15).isAfter(today)
        }
    }
}

/** An encouragement: one per member and per moment (momentCheers/{momentId}-{authorId}). */
data class MomentCheer(
    val momentId: String,
    val forMemberId: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val createdAt: Instant? = null,
) {
    val id: String get() = "$momentId-$authorId"

    companion object {
        const val TEXT_MAX = 280
    }
}
