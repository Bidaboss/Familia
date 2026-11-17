package fr.familia.app.feature.lending

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.LendItem
import fr.familia.app.core.model.LendStatus
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.isOwner

/*
 * Who may do what with a thing to lend (PRD, récit 51). The security rules check the same thing
 * (firebase/firestore.rules, lendItems), so hiding a button is never the only protection.
 */

/** The adults add things; a child sees the list and never asks. */
fun canAddLendItem(me: Member): Boolean = me.profile == MemberProfile.Adult

/** The owner and the admin principal change or remove a thing. */
fun canManageLendItem(item: LendItem, me: Member, family: Family): Boolean = item.ownerId == me.id || family.isOwner(me)

/** « Je voudrais l'emprunter »: an adult, not the owner, on a free thing. */
fun canAskLendItem(item: LendItem, me: Member): Boolean =
    me.profile == MemberProfile.Adult && item.ownerId != me.id && item.status == LendStatus.Available
