package fr.familia.app.feature.trips.list

import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * One trip of the list, with the first names of its participants, the members themselves for
 * their faces, and whether the one who looks takes part (« Comptes » is for them).
 */
data class TripSummary(
    val trip: Trip,
    val participants: List<String>,
    val people: List<Member> = emptyList(),
    val isParticipant: Boolean = false,
    /** Where the trip goes (its first step with a position), for the globe of the list (récit 99). */
    val place: fr.familia.app.feature.trips.map.GeoPoint? = null,
    /** The name of that place (« La Palmyre »), for its photo on the card of the next trip (règles 10, 12). */
    val placeName: String? = null,
)

/**
 * What the list « Voyages » shows (récit 80, DESIGN-52 B): one trip in the landscape, the trip
 * under way first (the one that started first when two households travel at once), else the next
 * one. The other trips under way go under « En ce moment », the trips to come under « Ensuite »,
 * by date.
 */
data class TripsPathPlan(
    val hero: TripSummary?,
    val heroOngoing: Boolean,
    val now: List<TripSummary>,
    val next: List<TripSummary>,
)

fun tripsPathPlan(ongoing: List<TripSummary>, upcoming: List<TripSummary>): TripsPathPlan {
    val under = ongoing.sortedWith(compareBy({ it.trip.startDate }, { it.trip.title }))
    val coming = upcoming.sortedWith(compareBy({ it.trip.startDate }, { it.trip.title }))
    val hero = under.firstOrNull() ?: coming.firstOrNull()
    return TripsPathPlan(
        hero = hero,
        heroOngoing = under.isNotEmpty(),
        now = under.drop(1),
        next = if (under.isNotEmpty()) coming else coming.drop(1),
    )
}

/** Days from [today] to the start of [trip]: 0 on the day it starts. */
fun daysUntil(trip: Trip, today: LocalDate): Int = ChronoUnit.DAYS.between(today, trip.startDate).toInt()

/** How many days [trip] lasts, the first and the last one counted. */
fun tripLength(trip: Trip): Int = ChronoUnit.DAYS.between(trip.startDate, trip.endDate).toInt() + 1

/** Which day of [trip] [today] is: 1 on the first day. */
fun tripDayNumber(trip: Trip, today: LocalDate): Int = ChronoUnit.DAYS.between(trip.startDate, today).toInt() + 1

/**
 * The two ends of the dates, short: « dim. 11 » and « ven. 16 avril 2027 » in one month, the
 * month on both sides across two months, the year on both sides across two years. Without
 * [weekday]: « 3 » and « 10 juillet 2027 ». The first of a month is written « 1er ».
 */
fun tripRangeEnds(trip: Trip, weekday: Boolean): Pair<String, String> {
    val start = trip.startDate
    val end = trip.endDate
    val day = if (weekday) "EEE d" else "d"
    val startPattern = when {
        start.year != end.year -> "$day MMMM yyyy"
        start.month != end.month -> "$day MMMM"
        else -> day
    }
    return french(start, startPattern) to french(end, "$day MMMM yyyy")
}

/** « Août 2026 », the month a past trip started. */
fun tripMonthLabel(trip: Trip): String = french(trip.startDate, "MMMM yyyy").replaceFirstChar { it.titlecase(Locale.FRANCE) }

private fun french(date: LocalDate, pattern: String): String {
    val p = if (date.dayOfMonth == 1) pattern.replace(Regex("(?<![d'])d(?![d'])"), "d'er'") else pattern
    return date.format(DateTimeFormatter.ofPattern(p, Locale.FRANCE))
}
