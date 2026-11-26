package fr.familia.app.feature.trips.place

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.feature.trips.detail.TripPathColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** « Dim. 11 », as on a chip of a day. */
fun shortDayLabel(date: LocalDate): String = frenchDate(date, "EEE d").replaceFirstChar { it.titlecase() }

/** « Lun. 12 avril · J2 »: the day and its number in the stay. */
fun stayDayLabel(date: LocalDate, number: Int): String =
    frenchDate(date, "EEE d MMMM").replaceFirstChar { it.titlecase() } + " · J$number"

/** « 5 nuits, dim. 11 → ven. 16 ». */
@Composable
fun nightsLabel(checkIn: LocalDate, checkOut: LocalDate): String {
    val nights = ChronoUnit.DAYS.between(checkIn, checkOut).toInt().coerceAtLeast(1)
    return pluralStringResource(R.plurals.place_nights, nights, nights, frenchDate(checkIn, "EEE d"), frenchDate(checkOut, "EEE d"))
}

/**
 * « Quel jour pour « Baie de Bonne Anse » ? » (récit 122, maquette A): one box of 56 dp per day of
 * the stay, three by row, the chosen one dark, and « Pas encore de jour (à caser) » under them.
 * [onChoose] gets the day, or null for « À caser ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayChoiceSheet(
    name: String,
    destination: String,
    days: List<LocalDate>,
    chosen: LocalDate?,
    onChoose: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.place_day_question, name.trim()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (destination.isNotBlank()) {
                Text(stringResource(R.string.place_day_during, destination), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DayGrid(days, chosen) { onChoose(it) }
            OutlinedButton(
                onClick = { onChoose(null) },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { selected = chosen == null },
            ) { Text(stringResource(R.string.place_day_none), fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * « Quelles nuits ? » for an accommodation (récit 122): the day of arrival, then the morning it is left,
 * both in the stay, and « Tout le séjour ». [onChoose] gets both days, or nulls for the whole stay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightsChoiceSheet(
    name: String,
    start: LocalDate,
    end: LocalDate,
    checkIn: LocalDate,
    checkOut: LocalDate,
    onChoose: (LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val last = if (end.isAfter(start)) end else start.plusDays(1)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.place_nights_question, name.trim()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(nightsLabel(checkIn, checkOut), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.place_nights_arrive), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            // A new arrival after the departure moves the departure to the next morning.
            DayGrid(stayDays(start, last.minusDays(1)), checkIn) { day ->
                onChoose(day, if (checkOut.isAfter(day)) checkOut else day.plusDays(1))
            }
            Text(stringResource(R.string.place_nights_leave), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            DayGrid(stayDays(checkIn.plusDays(1), last), checkOut) { day -> onChoose(checkIn, day) }
            OutlinedButton(
                onClick = { onChoose(null, null) },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.place_nights_whole), fontWeight = FontWeight.Bold) }
        }
    }
}

/** The days, three by row, each a box of 56 dp; the chosen one dark. */
@Composable
private fun DayGrid(days: List<LocalDate>, chosen: LocalDate?, onChoose: (LocalDate) -> Unit) {
    days.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { day ->
                val isChosen = day == chosen
                val modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics { selected = isChosen }
                if (isChosen) {
                    Button(
                        onClick = { onChoose(day) },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TripPathColors.Ink, contentColor = Color.White),
                        modifier = modifier,
                    ) { Text(shortDayLabel(day), fontWeight = FontWeight.Bold) }
                } else {
                    OutlinedButton(
                        onClick = { onChoose(day) },
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = modifier,
                    ) { Text(shortDayLabel(day), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
