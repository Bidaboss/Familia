package fr.familia.app.feature.goodnews

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.isOwner
import fr.familia.app.core.model.managesHousehold

/*
 * Who may do what with a « Bonne nouvelle » (PRD, récit 32, carte AVIS-12). The security rules
 * check the same thing (firebase/firestore.rules), so hiding a button is never the only protection.
 */

private fun Family.member(id: String) = members.firstOrNull { it.id == id }

private fun Family.isParentOf(me: Member, memberId: String) = member(memberId)?.parentIds?.contains(me.id) == true

/** Whose news [me] may publish: their own, plus for an adult the children of their household. */
fun goodNewsTargets(me: Member, family: Family): List<Member> {
    if (me.profile != MemberProfile.Adult) return listOf(me)
    val children = family.members
        .filter { it.profile != MemberProfile.Adult && it.householdId == me.householdId }
        .sortedBy { it.firstName.lowercase() }
    return listOf(me) + children
}

/** The members [me] may name: everyone else, in alphabetical order. */
fun mentionCandidates(me: Member, family: Family): List<Member> =
    family.members.filter { it.id != me.id }.sortedBy { it.firstName.lowercase() }

fun canEditGoodNews(news: GoodNews, me: Member): Boolean = news.authorId == me.id

/**
 * The author, the responsible adults of the author or of the child, a chef there, the admin principal.
 * The « Défi réussi » of Familia (récit 35) has no author nor household: the admin principal alone.
 */
fun canRemoveGoodNews(news: GoodNews, me: Member, family: Family): Boolean =
    if (news.challengeMonth != null) family.isOwner(me) else news.authorId == me.id ||
        family.isParentOf(me, news.authorId) ||
        (news.forMemberId != news.authorId && family.isParentOf(me, news.forMemberId)) ||
        family.managesHousehold(me, news.householdId)
