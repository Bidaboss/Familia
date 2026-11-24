package fr.familia.app.feature.trips.detail

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * « Le trajet » (règle 16, DESIGN-68 A): the trip as a line, from « Le voyage commence » to « Le
 * voyage se termine ». A destination is a stay (a step « Séjour »): it starts on its day and ends on
 * its end date, else on the next stay, else on the return. Its nights are the days between. The
 * transports (vol, train, voiture) and the activities sit between the destinations, in the order of
 * their day.
 */
sealed interface TimelineRow {
    val step: TripStep

    /** A destination, numbered 1 to n as on the map, with its nights. */
    data class Destination(override val step: TripStep, val number: Int, val start: LocalDate, val end: LocalDate) : TimelineRow {
        val nights: Int get() = ChronoUnit.DAYS.between(start, end).toInt()
    }

    /** A transport or an activity, with the distance from the destination before when both have a point. */
    data class Move(override val step: TripStep, val km: Int?) : TimelineRow
}

/** The whole line, its nights planned against the nights of the trip. */
data class TripTimeline(val rows: List<TimelineRow>, val planned: Int, val total: Int) {
    val destinations: List<TimelineRow.Destination> get() = rows.filterIsInstance<TimelineRow.Destination>()

    /** The nights with no stay: information, never an error (spec, « 1 nuit sans séjour »). */
    val missing: Int get() = (total - planned).coerceAtLeast(0)
}

/** The nights of [trip]: from the departure day to the return day. */
fun tripNights(trip: Trip): Int = ChronoUnit.DAYS.between(trip.startDate, trip.endDate).toInt().coerceAtLeast(0)

fun tripTimeline(trip: Trip, steps: List<TripStep>): TripTimeline {
    val sorted = steps.sortedWith(compareBy<TripStep>({ it.departDate }, { it.departTime }, { if (it.type == StepType.Lodging) 1 else 0 }))
    val stays = sorted.filter { it.type == StepType.Lodging }
    val rows = mutableListOf<TimelineRow>()
    var last: TripStep? = null
    sorted.forEach { step ->
        if (step.type == StepType.Lodging) {
            val index = stays.indexOf(step)
            val next = stays.getOrNull(index + 1)?.departDate
            val end = (step.arriveDate?.takeIf { it > step.departDate } ?: next ?: trip.endDate).coerceAtLeast(step.departDate)
            rows += TimelineRow.Destination(step, index + 1, step.departDate, end)
        } else {
            rows += TimelineRow.Move(step, distanceKm(last, step))
        }
        if (step.hasPosition) last = step
    }
    val planned = rows.filterIsInstance<TimelineRow.Destination>().sumOf { it.nights }
    return TripTimeline(rows, planned, tripNights(trip))
}

private fun distanceKm(from: TripStep?, to: TripStep): Int? {
    if (from == null || !from.hasPosition || !to.hasPosition) return null
    return flightKm(from.lat!!, from.lng!!, to.lat!!, to.lng!!).roundToInt()
}

/** Why a « + » or a « − » cannot be done. */
enum class NightsBlock { AtReturn, OneNight }

/** The steps to write after one night more ([delta] = 1) or less (-1) at [stay], or why not. */
sealed interface NightsChange {
    data class Steps(val changed: List<TripStep>) : NightsChange
    data class Blocked(val why: NightsBlock) : NightsChange
}

/**
 * [delta] nights more or less at [stay] (DESIGN-68 A; the departure chosen on the page of a destination,
 * fiche F5): its end moves by that many days, and every step after it
 * moves the same way, so the rest of the trip keeps its length. Never past the return of the trip,
 * never under one night (to have none, the stay is removed).
 */
fun changeNights(trip: Trip, steps: List<TripStep>, stay: TripStep, delta: Int): NightsChange {
    require(delta != 0)
    val destination = tripTimeline(trip, steps).destinations.firstOrNull { it.step.id == stay.id }
        ?: return NightsChange.Blocked(NightsBlock.OneNight)
    if (delta < 0 && destination.nights + delta < 1) return NightsChange.Blocked(NightsBlock.OneNight)
    val after = steps.filter { it.id != stay.id && it.departDate >= destination.end }
    val lastDay = (after.map { it.arriveDate ?: it.departDate } + destination.end).max()
    if (delta > 0 && lastDay.plusDays(delta.toLong()) > trip.endDate) return NightsChange.Blocked(NightsBlock.AtReturn)
    val days = delta.toLong()
    val moved = after.map { it.copy(departDate = it.departDate.plusDays(days), arriveDate = it.arriveDate?.plusDays(days)) }
    val changedStay = stay.copy(arriveDate = destination.end.plusDays(days))
    return NightsChange.Steps(listOf(changedStay) + moved)
}

/**
 * The text in each round of the map, for the placed steps in their order: the number of the
 * destination in « Le trajet », a tick once arrived, nothing for a transport or the departure.
 */
fun mapLabels(placed: List<TripStep>, line: TripTimeline): List<String> {
    val numbers = line.destinations.associate { it.step.id to it.number.toString() }
    return placed.map { step -> if (step.arrival != null) "✓" else numbers[step.id].orEmpty() }
}

/**
 * The last day [stay] may end on (fiche F5, « Départ »): the steps after it move with it, and the
 * last of them stays on or before the return of the trip.
 */
fun latestDeparture(trip: Trip, steps: List<TripStep>, stay: TripStep): java.time.LocalDate? {
    val destination = tripTimeline(trip, steps).destinations.firstOrNull { it.step.id == stay.id } ?: return null
    val after = steps.filter { it.id != stay.id && it.departDate >= destination.end }
    val lastDay = (after.map { it.arriveDate ?: it.departDate } + destination.end).max()
    return destination.end.plusDays(ChronoUnit.DAYS.between(lastDay, trip.endDate).coerceAtLeast(0))
}
