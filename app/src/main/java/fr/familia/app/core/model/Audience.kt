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

/** The circle of every household and content written before the circles (PRD, cercles). */
const val MAIN_CIRCLE = "principal"

/** The circles [circleIds] name: « principal » when they name none (no migration needed). */
fun circlesOf(circleIds: List<String>?): List<String> = circleIds?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: listOf(MAIN_CIRCLE)

/**
 * Whether a member of [memberCircles] is in the audience of a content of [contentCircles]: with the
 * circles off, always; with them on (étape 2, switch « circles »), when they share one. The same rule
 * as functions/src/audience.ts and inAudience in firebase/firestore.rules.
 */
fun inAudience(memberCircles: List<String>?, contentCircles: List<String>?, circlesOn: Boolean): Boolean {
    if (!circlesOn) return true
    val mine = circlesOf(memberCircles).toSet()
    return circlesOf(contentCircles).any { it in mine }
}

/**
 * « Toute la famille »: the members a content for everyone reaches (PRD, cercles, étape 1). Today
 * every member of [family]; étape 2 keeps those of the circles chosen. Never `family.members` for that.
 */
fun familyAudience(family: Family): List<Member> = family.members

/**
 * Whether [memberId] reads [list], as the rule canSeeList of firebase/firestore.rules: « Mes envies »
 * for all, the ideas of the family for all but their person, the others for their viewers, and for
 * the beneficiary once revealed.
 */
fun canSeeList(list: GiftList, memberId: String): Boolean =
    list.kind == GiftListKind.Wishes
        // The ideas of the family stay hidden from their person, « Voir comme » included (récit 9 ter).
        || (list.kind == GiftListKind.Ideas && list.beneficiaryId != memberId)
        || memberId in list.viewerIds || (list.beneficiaryId == memberId && list.revealed)
