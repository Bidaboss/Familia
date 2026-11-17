package fr.familia.app.feature.polls

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import fr.familia.app.core.model.Poll
import fr.familia.app.core.ui.formatDay
import fr.familia.app.core.ui.formatTime
import fr.familia.app.core.ui.frenchDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** « Samedi 19 décembre, 12 h 30 » for a date option, the text itself for a free poll. */
fun optionLabel(poll: Poll, index: Int, today: LocalDate): String {
    val option = poll.options.getOrNull(index).orEmpty()
    if (poll.kind != Poll.Kind.Date) return option
    val (day, time) = Poll.dateOption(option) ?: return option
    val dayLabel = formatDay(day, today)
    return if (time == null) dayLabel else "$dayLabel, ${formatTime(time)}"
}

/** « sam. 19/12 », the header of a column of the answers table. */
fun shortDateLabel(poll: Poll, index: Int): String {
    val day = Poll.dateOption(poll.options.getOrNull(index).orEmpty())?.first ?: return ""
    return day.format(shortFormatter)
}

@Composable
fun kindLabel(kind: Poll.Kind): String = stringResource(
    when (kind) {
        Poll.Kind.Date -> R.string.poll_kind_date
        Poll.Kind.Choice -> R.string.poll_kind_choice
    },
)

/** « 19 décembre », for « Réponses jusqu'au … ». */
fun closingDayLabel(day: LocalDate): String = frenchDate(day, "d MMMM")

private val shortFormatter = DateTimeFormatter.ofPattern("EEE d/MM", Locale.FRANCE)
