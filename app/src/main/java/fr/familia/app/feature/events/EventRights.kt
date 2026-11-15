package fr.familia.app.feature.events

import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.isOwner

/*
 * The PRD "Droits par profil" for events, as the app shows them. The security rules check the same
 * thing on the server (firebase/firestore.rules), so hiding a button is never the only protection.
 */

private fun Family.isParentOf(me: Member, memberId: String): Boolean =
    members.any { it.id == memberId && me.id in it.parentIds }

/**
 * Who [me] may create an event for, in display order: the whole family, households, themselves,
 * then children. The admin principal may pick any household and any child; a chef de famille has
 * the rights of an adult here.
 */
fun allowedTargets(me: Member, family: Family): List<EventTarget> {
    val children = family.members.filter { it.profile != MemberProfile.Adult }.sortedBy { it.firstName.lowercase() }
    return when {
        family.isOwner(me) -> buildList {
            add(EventTarget.ForFamily)
            family.households.sortedBy { it.name.lowercase() }.forEach { add(EventTarget.ForHousehold(it.id)) }
            add(EventTarget.ForSelf(me.id))
            children.forEach { add(EventTarget.ForChild(it.id)) }
        }
        me.profile == MemberProfile.Adult -> buildList {
            add(EventTarget.ForFamily)
            add(EventTarget.ForHousehold(me.householdId))
            add(EventTarget.ForSelf(me.id))
            children.filter { me.id in it.parentIds }.forEach { add(EventTarget.ForChild(it.id)) }
        }
        else -> listOf(EventTarget.ForSelf(me.id))
    }
}

/**
 * True when [me] may edit or delete [event] (PRD, récit 5): the admin principal all, everyone their
 * own, a chef de famille those written by a member of their household, parents their children's.
 */
fun canManage(event: Event, me: Member, family: Family): Boolean =
    family.isOwner(me) ||
        event.authorId == me.id ||
        (me.isAdmin && family.members.any { it.id == event.authorId && it.householdId == me.householdId }) ||
        (
            me.profile == MemberProfile.Adult &&
                (
                    family.isParentOf(me, event.authorId) ||
                        (event.target is EventTarget.ForChild && family.isParentOf(me, event.target.memberId))
                    )
            )

/**
 * Whose answer [me] may give to "qui vient" (PRD, récit 4): themselves first, then, for an adult,
 * each child they are a responsible adult of. The admin principal and chefs have no more rights here: an answer is personal.
 */
fun answerableMembers(me: Member, family: Family): List<Member> =
    listOf(me) + if (me.profile == MemberProfile.Adult) {
        family.members.filter { me.id in it.parentIds }.sortedBy { it.firstName.lowercase() }
    } else {
        emptyList()
    }

/** The author's current first name, or the one saved on the event once they have been removed. */
fun authorDisplayName(event: Event, family: Family): String =
    family.members.firstOrNull { it.id == event.authorId }?.firstName ?: event.authorName

/** The name a target refers to (household or child), or null for oneself and the whole family. */
fun targetName(target: EventTarget, family: Family): String? = when (target) {
    is EventTarget.ForHousehold -> family.households.firstOrNull { it.id == target.householdId }?.name
    is EventTarget.ForChild -> family.members.firstOrNull { it.id == target.memberId }?.firstName
    is EventTarget.ForSelf -> family.members.firstOrNull { it.id == target.memberId }?.firstName
    EventTarget.ForFamily -> null
}
