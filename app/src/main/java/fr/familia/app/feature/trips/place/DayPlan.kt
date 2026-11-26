package fr.familia.app.feature.trips.place

import fr.familia.app.core.model.TripPlace
import fr.familia.app.core.model.TripPlaceKind
import java.time.LocalDate
import java.time.LocalTime

/** An accommodation of a stay with its nights: from [checkIn] to the morning of [checkOut]. */
data class LodgingNights(val place: TripPlace, val checkIn: LocalDate, val checkOut: LocalDate)

/** One day of a stay (« Lun. 12 avril · J2 »): its restaurants and spots, by hour. */
data class PlanDay(val date: LocalDate, val number: Int, val places: List<TripPlace>)

/**
 * A stay laid out by day (récit 122, DESIGN-GAZETTE-1 A): its accommodations first with their nights,
 * one section per day of the stay with its restaurants and spots, then « À caser » ([toPlace]): the
 * places with no day, or a day the stay no longer has.
 */
data class DayPlan(val lodgings: List<LodgingNights>, val days: List<PlanDay>, val toPlace: List<TripPlace>)

/** The days of a stay from [start] to [end], both included (« dim. 11 → ven. 16 » is six days). */
fun stayDays(start: LocalDate, end: LocalDate): List<LocalDate> =
    generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end.coerceAtLeast(start)) }.toList()

/**
 * The nights of an accommodation in a stay from [start] to [end]: its own when they fit in it, else
 * the whole stay (a stay of the same day keeps one night, as a stay is at least one).
 */
fun lodgingNights(place: TripPlace, start: LocalDate, end: LocalDate): LodgingNights {
    val last = if (end.isAfter(start)) end else start.plusDays(1)
    val checkIn = place.checkIn?.takeIf { !it.isBefore(start) && it.isBefore(last) } ?: start
    val checkOut = place.checkOut?.takeIf { it.isAfter(checkIn) && !it.isAfter(last) } ?: last
    return LodgingNights(place, checkIn, checkOut)
}

/** The places of the destination [stepId], laid out on its stay from [start] to [end]. */
fun dayPlan(stepId: String, start: LocalDate, end: LocalDate, places: List<TripPlace>): DayPlan {
    val mine = places.filter { it.stepId == stepId }
    val byHour = compareBy<TripPlace>({ it.arriveTime ?: LocalTime.MAX }, { it.name.lowercase() })
    val lodgings = mine.filter { it.kind == TripPlaceKind.Lodging }
        .map { lodgingNights(it, start, end) }
        .sortedWith(compareBy<LodgingNights>({ it.checkIn }, { it.place.name.lowercase() }))
    val others = mine.filter { it.kind != TripPlaceKind.Lodging }
    val dates = stayDays(start, end)
    val days = dates.mapIndexed { index, date -> PlanDay(date, index + 1, others.filter { it.day == date }.sortedWith(byHour)) }
    val toPlace = others.filter { it.day == null || it.day !in dates }
        .sortedWith(compareBy<TripPlace>({ it.kind.ordinal }).then(byHour))
    return DayPlan(lodgings, days, toPlace)
}
