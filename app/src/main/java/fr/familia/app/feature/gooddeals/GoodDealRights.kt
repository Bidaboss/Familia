package fr.familia.app.feature.gooddeals

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GoodDeal
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.isOwner
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * Who may do what with a bon plan (PRD, récit 45). The security rules check the same thing
 * (firebase/firestore.rules, goodDeals), so hiding a button is never the only protection.
 */

/** The adults publish; a child sees everything and may say « Ça m'intéresse ». */
fun canPublishGoodDeal(me: Member): Boolean = me.profile == MemberProfile.Adult

/** The author and the admin principal change or remove a post. */
fun canManageGoodDeal(deal: GoodDeal, me: Member, family: Family): Boolean = deal.authorId == me.id || family.isOwner(me)

/** A bon plan lasts 7 days unless the author chooses (récit 45). */
const val DEFAULT_DEAL_DAYS = 7L

/** And 3 months at most; the rules allow 92 days from the moment it is written. */
const val MAX_DEAL_DAYS = 90L

/** A bon plan chosen to end on [day] ends at midnight that night, Paris time. */
fun dealEnd(day: LocalDate, zone: ZoneId = PARIS): Instant = day.plusDays(1).atStartOfDay(zone).toInstant()

/** The last day of a bon plan ending at [endsAt], as the screens show it. */
fun dealLastDay(endsAt: Instant, zone: ZoneId = PARIS): LocalDate = endsAt.minus(Duration.ofSeconds(1)).atZone(zone).toLocalDate()

/** The days a bon plan may end: from today to 3 months on. */
fun dealDayAllowed(day: LocalDate, today: LocalDate): Boolean = !day.isBefore(today) && !day.isAfter(today.plusDays(MAX_DEAL_DAYS))

val PARIS: ZoneId = ZoneId.of("Europe/Paris")
