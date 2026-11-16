package fr.familia.app.feature.polls

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Poll
import fr.familia.app.core.model.isOwner
import fr.familia.app.feature.events.answerableMembers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * The rights of firebase/firestore.rules (section Sondages), as the app shows them: hiding a button
 * is never the only protection.
 */

/** A child with the app answers but does not ask (PRD, droits d'un enfant). */
fun canAskPoll(me: Member): Boolean = me.profile == MemberProfile.Adult

/** Close, choose the date or delete: the author, or the admin principal. */
fun canManagePoll(poll: Poll, me: Member, family: Family): Boolean = poll.authorId == me.id || family.isOwner(me)

/** Whose answer [me] gives on [poll]: themselves, then their children, when they are asked. */
fun pollAnswerers(poll: Poll, me: Member, family: Family): List<Member> =
    answerableMembers(me, family).filter { it.id in poll.askedIds }

/** The end date is a day: answers are taken until its midnight, in Paris (where the family lives). */
fun closesAtFor(day: LocalDate): Instant = day.plusDays(1).atStartOfDay(PARIS).toInstant()

/** The last day answers are taken, for a poll that ends at [closesAt]. */
fun lastDayOf(closesAt: Instant): LocalDate = closesAt.atZone(PARIS).toLocalDate().minusDays(1)

val PARIS: ZoneId = ZoneId.of("Europe/Paris")
