package fr.familia.app.feature.helprequests

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.HelpStatus
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.isOwner
import java.time.LocalDate

/*
 * Who may do what with a « coup de main » (spec, « Règles et droits »). The security rules check
 * the same thing (firebase/firestore.rules), so hiding a button is never the only protection.
 */

private fun Member.isAdult() = profile == MemberProfile.Adult

private fun Family.member(id: String) = members.firstOrNull { it.id == id }

private fun Family.isParentOf(me: Member, memberId: String) = member(memberId)?.parentIds?.contains(me.id) == true

/** For whom [me] may ask: themselves, plus their children for an adult, anyone for the admin principal. */
fun helpTargets(me: Member, family: Family): List<Member> {
    val others = when {
        family.isOwner(me) -> family.members.filter { it.id != me.id }
        me.isAdult() -> family.members.filter { it.id != me.id && it.parentIds.contains(me.id) }
        else -> emptyList()
    }
    return listOf(me) + others.sortedBy { it.firstName.lowercase() }
}

/** The people who can be warned (récit 14): the adults, never the author. */
fun notifiableAdults(me: Member, family: Family): List<Member> =
    family.members.filter { it.isAdult() && it.id != me.id }.sortedBy { it.firstName.lowercase() }

/** The responsible adults of the person helped, always warned for a child (récit 14), except the author. */
fun requiredNotifyIds(authorId: String, forMemberId: String, family: Family): Set<String> {
    val helped = family.member(forMemberId) ?: return emptySet()
    if (helped.isAdult()) return emptySet()
    return helped.parentIds.filter { it != authorId }.toSet()
}

/** Modify, widen, cancel, « Merci »: the admin principal, the author, and an adult responsible for the author or the child helped. */
fun canManageHelp(request: HelpRequest, me: Member, family: Family): Boolean =
    family.isOwner(me) ||
        request.authorId == me.id ||
        (me.isAdult() && (family.isParentOf(me, request.authorId) || family.isParentOf(me, request.forMemberId)))

/** « Je m'en occupe »: an adult who was warned, never the author, on an open request. */
fun canVolunteer(request: HelpRequest, me: Member): Boolean =
    request.status == HelpStatus.Open && me.isAdult() && me.id in request.notifyIds && me.id != request.authorId

/** « Je ne peux pas » (carte PRODUIT-22): the only person warned, once. */
fun canDecline(request: HelpRequest, me: Member): Boolean =
    request.status == HelpStatus.Open && request.notifyIds == listOf(me.id) && request.declinedBy == null

fun canWithdraw(request: HelpRequest, me: Member): Boolean =
    request.status == HelpStatus.Taken && request.volunteerId == me.id

/** « Merci » from the day of the request on (récit 17), once. */
fun canThank(request: HelpRequest, me: Member, family: Family, today: LocalDate): Boolean =
    request.status == HelpStatus.Taken && request.thankedAt == null && request.date <= today &&
        canManageHelp(request, me, family)

/**
 * Whether the tab still lists [request] (récit 15): until the day after its date. The person who
 * can say « Merci » keeps a taken request a week longer, so that they can still say it.
 */
fun isListed(request: HelpRequest, me: Member, family: Family, today: LocalDate): Boolean = when {
    request.status == HelpStatus.Cancelled -> false
    request.date >= today -> true
    canThank(request, me, family, today) -> request.date >= today.minusDays(THANKS_DAYS)
    else -> false
}

/**
 * The badge of the tab: open requests where [me] was warned and can still help, and that [me] has
 * not seen yet in the « Coups de main » tab ([seenIds], retour [BUG] du 18/11).
 */
fun badgeCount(requests: List<HelpRequest>, me: Member, today: LocalDate, seenIds: Set<String> = emptySet()): Int =
    requests.count { needsMe(it, me, today) && it.id !in seenIds }

/** The seen requests that no longer need [me]: forgotten, so that open again they ring again. */
fun seenToForget(requests: List<HelpRequest>, me: Member, today: LocalDate, seenIds: Set<String>): Set<String> =
    requests.filter { it.id in seenIds && !needsMe(it, me, today) }.map { it.id }.toSet()

private const val THANKS_DAYS = 7L
