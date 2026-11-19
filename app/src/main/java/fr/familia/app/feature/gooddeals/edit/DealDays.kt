package fr.familia.app.feature.gooddeals.edit

import java.time.DayOfWeek
import java.time.LocalDate

/** The quick last days of a bon plan (DESIGN-51), in the order of the pills. */
enum class DealDay { Today, Weekend, InAWeek }

/** The day of a quick pill: today, the coming Sunday (today on a Sunday), or in 7 days. */
fun DealDay.dayFrom(today: LocalDate): LocalDate = when (this) {
    DealDay.Today -> today
    DealDay.Weekend -> today.plusDays(((DayOfWeek.SUNDAY.value - today.dayOfWeek.value + 7) % 7).toLong())
    DealDay.InAWeek -> today.plusDays(7)
}

/** The quick pill that [day] matches, the first one when two give the same day; null for another day. */
fun dealDayOf(day: LocalDate?, today: LocalDate): DealDay? =
    day?.let { d -> DealDay.entries.firstOrNull { it.dayFrom(today) == d } }
