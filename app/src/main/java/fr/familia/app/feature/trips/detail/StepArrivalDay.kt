package fr.familia.app.feature.trips.detail

import fr.familia.app.core.model.TripStep
import java.time.LocalDate

/**
 * When « Bien arrivé » makes sense (DESIGN-102 A): on the day of the step, from its departure to
 * its arrival, and the day after so that a late arrival or a forgotten tap can still be said.
 * Before, the trip only shows its countdown; long after, there is nothing left to say.
 */
object StepArrivalDay {
    /** True when [today] falls on the days of [step], or the day after its arrival. */
    fun isOpen(step: TripStep, today: LocalDate): Boolean {
        val last = maxOf(step.arriveDate ?: step.departDate, step.departDate).plusDays(1)
        return !today.isBefore(step.departDate) && !today.isAfter(last)
    }

    /** True once the step has started: its word (récit 88) is not written before the departure. */
    fun hasStarted(step: TripStep, today: LocalDate): Boolean = !today.isBefore(step.departDate)

    /** The step of « Prochaine étape »: the first one not arrived yet whose day is today, or none. */
    fun stepOfToday(steps: List<TripStep>, today: LocalDate): TripStep? = steps
        .filter { it.arrival == null && isOpen(it, today) }
        .minWithOrNull(compareBy({ it.departDate }, { it.departTime }))
}
