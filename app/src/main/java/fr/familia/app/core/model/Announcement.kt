package fr.familia.app.core.model

import java.time.Instant

/**
 * « Envoyer une notification » (PRD récit 69): a message of the admin principal to some members of
 * the family, one way only. announcements/{id}: written once by the admin principal, read by them
 * and by its recipients; the server sends it and writes back [delivery].
 */
data class Announcement(
    val id: String,
    val title: String,
    val text: String,
    val urgent: Boolean,
    val recipientIds: List<String>,
    val authorId: String,
    val authorName: String,
    val createdAt: Instant?,
    val delivery: Delivery = Delivery.Pending,
) {
    /** What the server did with it (announcements.ts). */
    enum class Delivery(val key: String?) {
        /** Not handled yet: the server has not read it. */
        Pending(null),
        Sent("sent"),
        /** Written between 22 h and 8 h without « Urgent »: it leaves at 8 h. */
        Morning("morning"),
        /** Module off: nothing left. */
        Refused("refused"),
        ;

        companion object {
            fun fromKey(key: String?): Delivery = entries.firstOrNull { it.key != null && it.key == key } ?: Pending
        }
    }

    companion object {
        const val MAX_TITLE = 60
        const val MAX_TEXT = 300

        /** « Déjà envoyées » shows the last ones only. */
        const val HISTORY = 50L
    }
}

/** What the form lets through: a title and a message within their limits, and at least one recipient. */
fun canSendAnnouncement(title: String, text: String, recipients: Set<String>): Boolean =
    title.trim().length in 1..Announcement.MAX_TITLE &&
        text.trim().length in 1..Announcement.MAX_TEXT &&
        recipients.isNotEmpty()

/**
 * The members the admin principal can write to: the [familyAudience] but not themselves, my
 * household first then household by household.
 */
fun announcementCandidates(family: Family, me: Member): List<Member> =
    familyAudience(family).filter { it.id != me.id }

/** Newest first; one being sent (no server time yet) on top. */
fun announcementOrder(all: List<Announcement>): List<Announcement> =
    all.sortedWith(compareByDescending<Announcement> { it.createdAt ?: Instant.MAX }.thenBy { it.id })

/** From 22 h to 8 h in Paris, a message without « Urgent » waits for 8 h (isQuietHour of prefs.ts). */
fun isAnnouncementNight(at: Instant): Boolean {
    val hour = at.atZone(java.time.ZoneId.of("Europe/Paris")).hour
    return hour >= 22 || hour < 8
}

/** Whether the message leaves now, or at 8 h. */
fun leavesAtEight(urgent: Boolean, at: Instant): Boolean = !urgent && isAnnouncementNight(at)
