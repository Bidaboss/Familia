package fr.familia.app.feature.gifts

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile

/*
 * Who does what with the gift lists (PRD, récits 7 to 9). The security rules check the same
 * thing (firebase/firestore.rules, « Listes et cadeaux »), so hiding a button is never the only
 * protection. Keep both in step.
 */

/** [me] keeps the lists of [member]: their own, or those of a child without the app they are responsible for. */
fun keepsListsOf(me: Member, member: Member): Boolean =
    member.id == me.id || (member.profile == MemberProfile.ChildWithoutApp && me.id in member.parentIds)

/** The members whose own lists and wishes [me] keeps: themselves first, then their children without the app. */
fun keptMembers(me: Member, family: Family): List<Member> =
    listOf(me) + family.members
        .filter { it.id != me.id && keepsListsOf(me, it) }
        .sortedBy { it.firstName.lowercase() }

/**
 * True while [me] is the beneficiary and the list is not revealed: the others' ideas, the
 * reservations and the parts stay hidden.
 */
fun isKeptFrom(list: GiftList, me: Member): Boolean = list.beneficiaryId == me.id && !list.revealed

/** The creator manages a list; whoever keeps the beneficiary's lists also manages their own list and wishes. */
fun canManageList(list: GiftList, me: Member, family: Family): Boolean {
    if (list.revealed) return false
    if (list.creatorId == me.id) return true
    if (list.kind == GiftListKind.Surprise || list.kind == GiftListKind.Ideas) return false
    val beneficiary = family.members.firstOrNull { it.id == list.beneficiaryId } ?: return false
    return keepsListsOf(me, beneficiary)
}

/** An item added by [me] is shown to the beneficiary only when [me] keeps their lists, and never on a surprise list. */
fun addsVisibleItem(list: GiftList, me: Member, family: Family): Boolean {
    if (list.kind == GiftListKind.Surprise || list.kind == GiftListKind.Ideas) return false
    val beneficiary = family.members.firstOrNull { it.id == list.beneficiaryId } ?: return false
    return keepsListsOf(me, beneficiary)
}

/** Anyone who sees the list may reserve, except its beneficiary. A child never joins a joint gift (carte PRODUIT-19). */
fun canReserve(list: GiftList, me: Member): Boolean = !list.revealed && list.beneficiaryId != me.id

fun canShare(list: GiftList, me: Member): Boolean = canReserve(list, me) && me.profile == MemberProfile.Adult

/**
 * « Partager » a list outside the app (récit 31, partie 2): only one's own « Mes envies », never the
 * « Idées de la famille », a list of an occasion or a revealed list. A child with the app shares their
 * own, as their adults do; those of a child without the app, their adults only. The message carries the name and the
 * link, never an item, a reservation or a part: see [fr.familia.app.core.share.wishesShareContent].
 */
fun canShareWishesOutside(list: GiftList, me: Member, family: Family): Boolean {
    if (list.kind != GiftListKind.Wishes || list.revealed) return false
    val beneficiary = family.members.firstOrNull { it.id == list.beneficiaryId } ?: return false
    return when (beneficiary.profile) {
        MemberProfile.Adult -> beneficiary.id == me.id
        // Only a first name and a link go out, never a photo: a child with the app shares their own.
        MemberProfile.ChildWithApp -> beneficiary.id == me.id || me.id in beneficiary.parentIds
        MemberProfile.ChildWithoutApp -> me.id in beneficiary.parentIds
    }
}

/**
 * Viewers of an own list always include its beneficiary and the responsible adults of a child;
 * a surprise list never includes its beneficiary. [creator] always sees what they make.
 */
fun normalizedViewers(
    kind: GiftListKind,
    beneficiary: Member,
    creator: Member,
    chosen: Set<String>,
): List<String> {
    val viewers = chosen.toMutableSet()
    viewers += creator.id
    when (kind) {
        GiftListKind.Own -> {
            viewers += beneficiary.id
            viewers += beneficiary.parentIds
        }
        GiftListKind.Surprise -> viewers -= beneficiary.id
        GiftListKind.Wishes, GiftListKind.Ideas -> return emptyList()
    }
    return viewers.sorted()
}

/** The viewers [me] cannot uncheck on a list of [kind] for [beneficiary]. */
fun lockedViewers(kind: GiftListKind, beneficiary: Member, me: Member): Set<String> = when (kind) {
    GiftListKind.Own -> setOf(me.id, beneficiary.id) + beneficiary.parentIds
    GiftListKind.Surprise -> setOf(me.id)
    GiftListKind.Wishes, GiftListKind.Ideas -> emptySet()
}
