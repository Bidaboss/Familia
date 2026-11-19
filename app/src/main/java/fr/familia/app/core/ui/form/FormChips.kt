package fr.familia.app.core.ui.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.ui.frenchDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * A choice of a form in a pill (DESIGN-50): outlined, dark when chosen, 56 dp high. It is a radio
 * button for TalkBack, inside a [ChipGroup]. [description] replaces the text when it says too
 * little (« Mer. 25 » is read « mercredi 25 novembre »).
 */
@Composable
fun FormChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.inverseSurface else colors.surface,
        contentColor = if (selected) colors.inverseOnSurface else colors.onSurface,
        border = if (selected) null else BorderStroke(1.5.dp, colors.outlineVariant),
        modifier = modifier
            .heightIn(min = 56.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

/** Pills that go to the line when they no longer fit (large text): one radio group for TalkBack. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Today and the 3 days after it: the days of [DayChips]. */
fun quickDays(today: LocalDate): List<LocalDate> = (0L..3L).map(today::plusDays)

/**
 * The day in pills (DESIGN-50): « Aujourd'hui », « Demain », the 2 days after (« Mer. 25 »), and
 * « Autre jour », which opens the calendar. A day chosen in the calendar shows as its own pill,
 * chosen, before « Autre jour ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayChips(
    date: LocalDate?,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var calendar by rememberSaveable { mutableStateOf(false) }
    val quick = quickDays(today)
    ChipGroup(modifier) {
        quick.forEachIndexed { index, day ->
            val label = when (index) {
                0 -> stringResource(R.string.day_today)
                1 -> stringResource(R.string.day_tomorrow)
                else -> shortDay(day)
            }
            FormChip(label, selected = date == day, onClick = { onPick(day) }, description = if (index > 1) frenchDate(day, "EEEE d MMMM") else null)
        }
        if (date != null && date !in quick) {
            FormChip(shortDay(date, withMonth = true), selected = true, onClick = { calendar = true }, description = frenchDate(date, "EEEE d MMMM yyyy"))
        }
        FormChip(
            stringResource(R.string.form_other_day),
            selected = false,
            onClick = { calendar = true },
            icon = Icons.Outlined.CalendarMonth,
        )
    }
    if (calendar) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = (date ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { calendar = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis -> onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) }
                    calendar = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { calendar = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

/** « Mer. 25 », or « Ven. 4 déc. » for a day further away. */
private fun shortDay(day: LocalDate, withMonth: Boolean = false): String =
    frenchDate(day, if (withMonth) "EEE d MMM" else "EEE d").replaceFirstChar { it.titlecase(Locale.FRANCE) }
