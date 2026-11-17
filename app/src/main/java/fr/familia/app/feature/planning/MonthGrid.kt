package fr.familia.app.feature.planning

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** How the Planning tab shows the days: the list by default, the month grid as a choice (design principles). */
enum class PlanningMode { List, Month, Week }

/** The Monday of the week of [date], as in France. */
fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

/**
 * Cells of a month grid, week by week, starting on Monday as in France. Null cells pad the first
 * and last weeks so that every row has seven cells.
 */
fun monthCells(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value - DayOfWeek.MONDAY.value)
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val trailing = (7 - (leading + days.size) % 7) % 7
    return List(leading) { null } + days + List(trailing) { null }
}

/** Months the grid can show: from the current one to a year ahead, the span of the birthdays. */
const val MONTHS_AHEAD = 12
