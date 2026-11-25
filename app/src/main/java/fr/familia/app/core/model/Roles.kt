package fr.familia.app.core.model

/*
 * Who manages what (PRD, « Périmètre d'un chef de famille », carte DEV-8; récit 114, C2). The
 * admin principal manages the whole family and its members; a chef de famille ([Member.isAdmin])
 * renames their own household and manages its events. The security rules check the same thing (firebase/firestore.rules), so hiding a
 * button is never the only protection. [me] should be read from the live [Family].
 */

fun Family.isOwner(me: Member): Boolean = ownerId != null && ownerId == me.id

/** The admin principal, or a chef de famille of [householdId]: renaming it, its events and news. */
fun Family.managesHousehold(me: Member, householdId: String): Boolean =
    isOwner(me) || (me.isAdmin && me.householdId == householdId)

/** True when [me] may add members: the admin principal only, the family rarely grows. */
fun Family.canAddMembers(me: Member): Boolean = isOwner(me)

/**
 * True when [me] may edit [member]: the admin principal only (récit 114, C2). A chef de famille no
 * longer edits the members of their household, children included; they only rename it.
 */
fun Family.canEditMember(me: Member, member: Member): Boolean = isOwner(me)

/**
 * True when [me] may change the photo of [member] (PRD, récit 3 bis): their own, a child of their
 * household for an adult, or anyone for the admin principal. Chefs have no extra right here.
 */
fun Family.canChangePhoto(me: Member, member: Member): Boolean =
    me.id == member.id ||
        isOwner(me) ||
        (member.profile != MemberProfile.Adult && me.profile == MemberProfile.Adult && me.householdId == member.householdId)
