package fr.familia.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import fr.familia.app.core.data.FAMILY_ZONE
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "10 h 00", the French way, with non-breaking spaces so it never splits over two lines. */
fun formatTime(time: LocalTime): String = "${time.hour}\u00A0h\u00A0${time.minute.toString().padStart(2, '0')}"

/** "Samedi 21 novembre", "Dimanche 1er novembre", with the year only when it is not the current one. */
fun formatDay(date: LocalDate, today: LocalDate): String =
    frenchDate(date, if (date.year == today.year) DAY else DAY_YEAR).replaceFirstChar { it.titlecase(Locale.FRANCE) }

/** The day of the month alone in a pattern: a lone « d », not « dd » and not in quotes. */
private val LONE_DAY = Regex("(?<![d'])d(?![d'])")

/**
 * [date] written with [pattern] in French, the first of the month as « 1er », as French writes
 * it: « dimanche 1er novembre », « le 1er décembre ». Lower case, as java.time gives it.
 */
fun frenchDate(date: LocalDate, pattern: String): String {
    val p = if (date.dayOfMonth == 1) pattern.replace(LONE_DAY, "d'er'") else pattern
    return date.format(DateTimeFormatter.ofPattern(p, Locale.FRANCE))
}

/** [at] as a day of Paris, written with [pattern] as [frenchDate] does: the same day on every phone. */
fun frenchDate(at: Instant, pattern: String): String = frenchDate(at.atZone(FAMILY_ZONE).toLocalDate(), pattern)

/** « 21 novembre à 19 h 30 », « 1er décembre à 8 h 05 », the day and the hour of Paris. */
fun frenchDateTime(at: Instant, pattern: String = "d MMMM"): String {
    val paris = at.atZone(FAMILY_ZONE)
    return "${frenchDate(paris.toLocalDate(), pattern)} à ${formatTime(paris.toLocalTime())}"
}

/** Day header of the planning: "Aujourd'hui, samedi 14 novembre", "Demain, …" or "Lundi 16 novembre". */
@Composable
fun dayTitle(date: LocalDate, today: LocalDate): String {
    val day = formatDay(date, today)
    return when (date) {
        today -> stringResource(R.string.day_today_with_date, day.lowercase(Locale.FRANCE))
        today.plusDays(1) -> stringResource(R.string.day_tomorrow_with_date, day.lowercase(Locale.FRANCE))
        else -> day
    }
}

/** Short form for a list row: "Aujourd'hui", "Demain" or "Samedi 21 novembre". */
@Composable
fun dayShortLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.day_today)
    today.plusDays(1) -> stringResource(R.string.day_tomorrow)
    else -> formatDay(date, today)
}

private const val DAY = "EEEE d MMMM"
private const val DAY_YEAR = "EEEE d MMMM yyyy"
