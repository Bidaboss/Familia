package fr.familia.app.feature.trips.detail

import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import java.time.LocalDate

/**
 * The days of the illustrated path (R1 bis, maquette R6): the strip at the top lists every day of
 * the trip, the path below shows a few of them around the chosen one.
 */
object TripDays {
    /** Stops drawn on the path at once, as on the maquette. */
    const val PATH_STOPS = 4

    /** Every day of [trip], first to last. A trip saved the wrong way round still has its first day. */
    fun of(trip: Trip): List<LocalDate> {
        val end = maxOf(trip.startDate, trip.endDate)
        return generateSequence(trip.startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    }

    /** The day shown first: today during the trip, else its first day before, its last day after. */
    fun firstShown(days: List<LocalDate>, today: LocalDate): LocalDate = when {
        today.isBefore(days.first()) -> days.first()
        today.isAfter(days.last()) -> days.last()
        else -> today
    }

    /**
     * The steps of [day]: those under way that day. A step outside the dates of the trip goes to
     * its first or last day, so that no step is ever lost from the path.
     */
    fun stepsOf(steps: List<TripStep>, day: LocalDate, days: List<LocalDate>): List<TripStep> = steps.filter { step ->
        val from = step.departDate.coerceIn(days.first(), days.last())
        val to = maxOf(step.arriveDate ?: step.departDate, step.departDate).coerceIn(days.first(), days.last())
        !day.isBefore(from) && !day.isAfter(to)
    }

    /** The indices of the days drawn on the path: [PATH_STOPS] at most, the chosen one third when it can. */
    fun window(count: Int, chosen: Int): IntRange {
        val size = minOf(PATH_STOPS, count)
        val first = (chosen - 2).coerceIn(0, count - size)
        return first until first + size
    }
}
