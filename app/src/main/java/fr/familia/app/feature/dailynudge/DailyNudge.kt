package fr.familia.app.feature.dailynudge

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Member
import java.time.Duration
import java.time.Instant

/**
 * « Le petit geste du jour » (PRD, récit 43): one small thing the family would enjoy, shown as a
 * single line of « À faire pour moi » on the Accueil. Never a notification, never a reproach. It
 * leaves out what « À faire » already asks (an answer, a coup de main, a poll).
 */
sealed interface DailyNudge {
    /** « La photo de Camille attend ta réaction »: a recent good news of someone else, not reacted to yet. */
    data class ReactToPhoto(val goodNewsId: String, val firstName: String) : DailyNudge
}

/** How far back a good news still makes a gesture: older ones are left alone. */
val NUDGE_WINDOW: Duration = Duration.ofDays(3)

/**
 * The gesture for [me], or null when there is nothing to suggest. Only a good news with photos,
 * by and for someone else, published in the last [NUDGE_WINDOW], that I have not reacted to nor
 * hidden; the newest one. Familia's own news (challenge, birthday) are left out.
 */
fun pickDailyNudge(news: List<GoodNews>, me: Member, family: Family, now: Instant): DailyNudge? {
    val since = now.minus(NUDGE_WINDOW)
    val pick = news
        .asSequence()
        .filter { it.challengeMonth == null && it.birthdayOf == null && it.photoIds.isNotEmpty() }
        .filter { it.authorId != me.id && it.forMemberId != me.id }
        .filter { me.id !in it.reactions && !it.isHiddenFor(me.id) }
        .filter { val at = it.createdAt; at != null && at.isAfter(since) && !at.isAfter(now) }
        .sortedByDescending { it.createdAt }
        .firstOrNull() ?: return null
    val person = family.members.firstOrNull { it.id == pick.forMemberId } ?: return null
    return DailyNudge.ReactToPhoto(pick.id, person.firstName)
}
