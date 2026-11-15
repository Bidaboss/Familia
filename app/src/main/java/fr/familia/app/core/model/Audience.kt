package fr.familia.app.core.model

/**
 * The one place that says who an event concerns (PRD, cartes AVIS-7 et AVIS-8). The notifications
 * (functions/src/logic.ts, same rule) and the « Mon foyer » filter of the planning both use it, so
 * a future « cercle » only changes this function. Never test « tous les membres » elsewhere.
 *
 * An event concerns [me] when it is for the whole family, or when its target is in my scope: me,
 * a member or a child of my household, my household itself, or a child I am a responsible adult
 * for (in their parentIds, even in another household).
 */
fun concerns(target: EventTarget, me: Member, family: Family): Boolean = when (target) {
    EventTarget.ForFamily -> true
    is EventTarget.ForHousehold -> target.householdId == me.householdId
    is EventTarget.ForSelf -> isInMyScope(target.memberId, me, family)
    is EventTarget.ForChild -> isInMyScope(target.memberId, me, family)
}

private fun isInMyScope(memberId: String, me: Member, family: Family): Boolean {
    if (memberId == me.id) return true
    val member = family.members.firstOrNull { it.id == memberId } ?: return false
    return member.householdId == me.householdId || me.id in member.parentIds
}
