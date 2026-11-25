package fr.familia.app.feature.trips

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.model.isOwner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/*
 * The rights of the trips (PRD, récits 11, 12 and 14), as the app shows them. The security rules
 * check the same thing on the server (firebase/firestore.rules), except the household of the
 * children taking part, which only the app checks (see the rules).
 */

/** The creator, or the admin principal, edits and deletes the trip and chooses who follows it. */
fun canManageTrip(trip: Trip, me: Member, family: Family): Boolean =
    trip.creatorId == me.id || family.isOwner(me)

/**
 * The members who can be ticked as followers of a trip, all ticked for a new one. Today the whole
 * family; when the family becomes several circles, this is the one place to switch to the shared
 * audience function of core/model.
 */
fun tripAudience(family: Family): List<Member> = family.members

/** Only an adult creates a trip (PRD rév. 131). A child takes part once an adult adds them. */
fun canCreateTrip(me: Member): Boolean = me.profile == MemberProfile.Adult

/** Each participant adds and edits the steps and says « bien arrivé ». */
fun isParticipant(trip: Trip, me: Member): Boolean = me.id in trip.participantIds

/**
 * « Comptes » (récit 16 bis, règle 40 du récit 112): only when the participants come from several
 * households. A trip of one household has nobody to settle up with, so its accounts never show.
 */
fun hasSeveralHouseholds(participants: List<Member>): Boolean =
    participants.map { it.householdId }.distinct().size > 1

/** A participant removes a step; the admin principal too, to tidy up. */
fun canDeleteStep(trip: Trip, me: Member, family: Family): Boolean = isParticipant(trip, me) || family.isOwner(me)

/**
 * Who [me] may add as a participant (récit 11, PRD rév. 190): anyone of the family, of every
 * household, children included, with the app or not, since several households often leave
 * together. [me] comes first, then by first name.
 */
fun participantChoices(me: Member, family: Family): List<Member> =
    listOf(me) + family.members.filter { it.id != me.id }.sortedBy { it.firstName.lowercase() }

/** Where a trip is in time: the planning and the list say « En cours » for an ongoing one. */
enum class TripPhase { Upcoming, Ongoing, Past }

fun tripPhase(trip: Trip, today: LocalDate): TripPhase = when {
    today < trip.startDate -> TripPhase.Upcoming
    today > trip.endDate -> TripPhase.Past
    else -> TripPhase.Ongoing
}

/** How long after the planned arrival a participant who said nothing is reminded (récit 14). */
const val REMINDER_DELAY_HOURS = 3L

/**
 * When to remind the participants of [step]: 3 hours after its planned arrival, in the phone's time
 * zone. Null when there is nothing to remind: already arrived, no arrival time, or already past.
 */
fun reminderTime(step: TripStep, zone: ZoneId, now: Instant): Instant? {
    if (step.arrival != null) return null
    val time = step.arriveTime ?: return null
    val day = step.arriveDate ?: step.departDate
    val remindAt = ZonedDateTime.of(day, time, zone).plusHours(REMINDER_DELAY_HOURS).toInstant()
    return remindAt.takeIf { it.isAfter(now) }
}

/** First names of [ids] in the family, in that order; members who left are skipped. */
fun firstNames(ids: List<String>, family: Family): List<String> {
    val byId = family.members.associateBy { it.id }
    return ids.mapNotNull { byId[it]?.firstName }
}

/**
 * Who goes, short enough for one line under the title (E-4): two names, then how many others.
 * « Lou et Sam » stays whole; « Lou, Sam, Noé, Inès » gives the first two and 2 for « et 2 autres ».
 */
fun headNames(names: List<String>, shown: Int = 2): Pair<List<String>, Int> =
    if (names.size <= shown) names to 0 else names.take(shown) to (names.size - shown)

/** "Lou", "Lou et Sam", "Lou, Sam et Noé". */
fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    else -> names.dropLast(1).joinToString(", ") + " et " + names.last()
}
