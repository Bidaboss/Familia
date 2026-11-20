package fr.familia.app.core.model

import java.time.LocalDate
import java.time.LocalTime

/** How often a series comes back (PRD, récit 54). */
enum class RepeatFreq { Weekly, Monthly, Yearly }

/** « Le 12 de chaque mois » or « le 2e dimanche de chaque mois ». */
enum class MonthlyBy { Date, Weekday }

/**
 * The rule of a series, as the security rules check it: [interval] 2 only for every 2 weeks,
 * [monthlyBy] only for a monthly one, an end at [until] or after [count] times, or never.
 */
data class SeriesRule(
    val freq: RepeatFreq,
    val interval: Int = 1,
    val monthlyBy: MonthlyBy? = null,
    val start: LocalDate,
    val until: LocalDate? = null,
    val count: Int? = null,
) {
    /**
     * The days of the rule in order, from [start] up to [to], as the server counts them
     * (functions/src/series.ts): a 31 or a 29 February only where it exists, a 5th Sunday only in the
     * months that have one. [count] counts them all, even those taken out.
     */
    fun days(to: LocalDate): List<LocalDate> {
        val last = until?.takeIf { it < to } ?: to
        val nth = (start.dayOfMonth + 6) / 7
        val out = mutableListOf<LocalDate>()
        for (k in 0 until 2000) {
            val next: LocalDate? = when (freq) {
                RepeatFreq.Weekly -> start.plusWeeks(k.toLong() * interval)
                RepeatFreq.Monthly -> {
                    val month = start.withDayOfMonth(1).plusMonths(k.toLong())
                    if (monthlyBy == MonthlyBy.Weekday) nthWeekday(month, start, nth) else dayIn(month, start.dayOfMonth)
                }
                RepeatFreq.Yearly -> dayIn(start.withDayOfMonth(1).plusYears(k.toLong()), start.dayOfMonth)
            }
            if (next == null) continue
            if (next > last) break
            out += next
            if (count != null && out.size >= count) break
        }
        return out
    }

    private fun dayIn(month: LocalDate, day: Int): LocalDate? = if (day <= month.lengthOfMonth()) month.withDayOfMonth(day) else null

    private fun nthWeekday(month: LocalDate, like: LocalDate, nth: Int): LocalDate? {
        val shift = (like.dayOfWeek.value - month.dayOfWeek.value + 7) % 7
        return dayIn(month, 1 + shift + (nth - 1) * 7)
    }
}

/**
 * eventSeries/{seriesId}: what repeats, the texts, the time and the target of an event, and how
 * ([rule]). The server writes its occurrences as ordinary events for the 12 months to come.
 * [exdates]: the days taken out one by one. [applyFrom]: « Celle-ci et les suivantes », the first
 * day the texts apply to.
 */
data class EventSeries(
    val id: String,
    val title: String,
    val time: LocalTime?,
    val place: String?,
    val note: String?,
    val target: EventTarget,
    val authorId: String,
    val authorName: String,
    val rule: SeriesRule,
    val exdates: List<LocalDate> = emptyList(),
    val applyFrom: LocalDate? = null,
    /** As on [Event] (récit 74): the server copies them to each day. */
    val placeKind: PlaceKind = PlaceKind.Other,
    val placeHouseholdId: String? = null,
    /** The members named « @Prénom » in [note] (Kévin, 19/11). */
    val mentionIds: List<String> = emptyList(),
) {
    /** The days still to come before [day] (from [today]), which a change from [day] must not touch. */
    fun comingBefore(day: LocalDate, today: LocalDate): List<LocalDate> =
        if (day <= today) emptyList() else rule.days(day.minusDays(1)).filter { it >= today && it !in exdates }

    companion object {
        const val MAX_EXDATES = 100
        const val MAX_COUNT = 200
    }
}

/** What the form offers under « Se répète ». */
enum class RepeatChoice { None, Weekly, Biweekly, MonthlyDate, MonthlyWeekday, Yearly }

/** How a series ends. */
sealed interface RepeatEnd {
    data object Never : RepeatEnd
    data class OnDay(val day: LocalDate) : RepeatEnd
    data class After(val times: Int) : RepeatEnd
}

/** The rule of [choice] from [start], or null for « Ne se répète pas ». */
fun ruleFor(choice: RepeatChoice, end: RepeatEnd, start: LocalDate): SeriesRule? {
    val until = (end as? RepeatEnd.OnDay)?.day
    val count = (end as? RepeatEnd.After)?.times
    return when (choice) {
        RepeatChoice.None -> null
        RepeatChoice.Weekly -> SeriesRule(RepeatFreq.Weekly, 1, null, start, until, count)
        RepeatChoice.Biweekly -> SeriesRule(RepeatFreq.Weekly, 2, null, start, until, count)
        RepeatChoice.MonthlyDate -> SeriesRule(RepeatFreq.Monthly, 1, MonthlyBy.Date, start, until, count)
        RepeatChoice.MonthlyWeekday -> SeriesRule(RepeatFreq.Monthly, 1, MonthlyBy.Weekday, start, until, count)
        RepeatChoice.Yearly -> SeriesRule(RepeatFreq.Yearly, 1, null, start, until, count)
    }
}

/** The choice and the end the form shows for [rule]. */
fun choiceOf(rule: SeriesRule): Pair<RepeatChoice, RepeatEnd> {
    val choice = when (rule.freq) {
        RepeatFreq.Weekly -> if (rule.interval == 2) RepeatChoice.Biweekly else RepeatChoice.Weekly
        RepeatFreq.Monthly -> if (rule.monthlyBy == MonthlyBy.Weekday) RepeatChoice.MonthlyWeekday else RepeatChoice.MonthlyDate
        RepeatFreq.Yearly -> RepeatChoice.Yearly
    }
    val end = rule.until?.let { RepeatEnd.OnDay(it) } ?: rule.count?.let { RepeatEnd.After(it) } ?: RepeatEnd.Never
    return choice to end
}
