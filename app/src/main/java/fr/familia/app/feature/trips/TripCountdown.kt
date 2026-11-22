package fr.familia.app.feature.trips

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Where a trip stands, counted in nights (récit 95, DESIGN-68): « 4 nuits avant le départ »,
 * « Demain », « C'est le jour J », then « Jour 2 sur 6 ». Nothing once the trip is over.
 */
sealed interface TripCountdown {
    data class NightsBefore(val nights: Int) : TripCountdown
    data object Tomorrow : TripCountdown
    data object DepartureDay : TripCountdown
    data class During(val day: Int, val of: Int) : TripCountdown
    data object Over : TripCountdown
}

fun tripCountdown(start: LocalDate, end: LocalDate, today: LocalDate): TripCountdown {
    val untilStart = ChronoUnit.DAYS.between(today, start).toInt()
    return when {
        untilStart > 1 -> TripCountdown.NightsBefore(untilStart)
        untilStart == 1 -> TripCountdown.Tomorrow
        untilStart == 0 -> TripCountdown.DepartureDay
        today <= end -> TripCountdown.During(
            day = ChronoUnit.DAYS.between(start, today).toInt() + 1,
            of = ChronoUnit.DAYS.between(start, end).toInt() + 1,
        )
        else -> TripCountdown.Over
    }
}
