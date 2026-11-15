package fr.familia.app.core.model

/*
 * Who manages what (PRD, « Périmètre d'un chef de famille », carte DEV-8). The admin principal
 * manages the whole family; a chef de famille ([Member.isAdmin]) only the members of their own
 * household. The security rules check the same thing (firebase/firestore.rules), so hiding a
 * button is never the only protection. [me] should be read from the live [Family].
 */

fun Family.isOwner(me: Member): Boolean = ownerId != null && ownerId == me.id

/** The admin principal, or a chef de famille of [householdId]. */
fun Family.managesHousehold(me: Member, householdId: String): Boolean =
    isOwner(me) || (me.isAdmin && me.householdId == householdId)

/** True when [me] may add members (to their own household for a chef). */
fun Family.canAddMembers(me: Member): Boolean = isOwner(me) || me.isAdmin

/** True when [me] may edit [member]: the admin principal anyone, a chef the members of their household. */
fun Family.canEditMember(me: Member, member: Member): Boolean =
    isOwner(me) || (me.isAdmin && member.householdId == me.householdId && member.id != ownerId)
