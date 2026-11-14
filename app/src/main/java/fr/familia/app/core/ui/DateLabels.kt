package fr.familia.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "10 h 00", the French way, with non-breaking spaces so it never splits over two lines. */
fun formatTime(time: LocalTime): String = "${time.hour}\u00A0h\u00A0${time.minute.toString().padStart(2, '0')}"

/** "Samedi 21 novembre", with the year only when it is not the current one. */
fun formatDay(date: LocalDate, today: LocalDate): String =
    date.format(if (date.year == today.year) dayFormatter else dayYearFormatter)
        .replaceFirstChar { it.titlecase(Locale.FRANCE) }

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

private val dayFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)
private val dayYearFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE)
