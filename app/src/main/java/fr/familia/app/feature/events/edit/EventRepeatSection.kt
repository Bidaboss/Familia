package fr.familia.app.feature.events.edit

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.RepeatChoice
import fr.familia.app.core.model.RepeatEnd
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * « Se répète » and « Se termine » (PRD, récit 54). One line each, touching it opens the choices,
 * like « Pour qui ». An event of a series cannot stop repeating here: that is « Supprimer », then
 * « Celle-ci et les suivantes ».
 */
@Composable
internal fun RepeatSection(
    form: EventForm,
    isOccurrence: Boolean,
    endInvalid: Boolean,
    onChange: ((EventForm) -> EventForm) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val date = form.date
    val choices = RepeatChoice.entries.filter { !(isOccurrence && it == RepeatChoice.None) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceLine(
            label = stringResource(R.string.event_repeat),
            value = repeatLabel(form.repeat, date),
            enabled = date != null,
            onClick = { picking = true },
        )
        if (date == null) {
            Text(
                stringResource(R.string.event_repeat_date_first),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (form.repeat != RepeatChoice.None && date != null) {
            EndSection(form, endInvalid, onChange)
            Text(
                stringResource(R.string.event_repeat_each_own),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (picking && date != null) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.event_repeat)) },
            text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    choices.forEach { choice ->
                        RadioLine(repeatLabel(choice, date), selected = form.repeat == choice) {
                            onChange { it.copy(repeat = choice) }
                            picking = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndSection(form: EventForm, invalid: Boolean, onChange: ((EventForm) -> EventForm) -> Unit) {
    var datePicker by rememberSaveable { mutableStateOf(false) }
    val start = form.date ?: return
    val end = form.end
    Column(Modifier.selectableGroup()) {
        Text(
            stringResource(R.string.event_repeat_ends),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RadioLine(stringResource(R.string.event_repeat_end_never), selected = end == RepeatEnd.Never) {
            onChange { it.copy(end = RepeatEnd.Never) }
        }
        RadioLine(
            if (end is RepeatEnd.OnDay) stringResource(R.string.event_repeat_end_on, end.day.format(dateFormatter))
            else stringResource(R.string.event_repeat_end_on_pick),
            selected = end is RepeatEnd.OnDay,
        ) { datePicker = true }
        RadioLine(stringResource(R.string.event_repeat_end_after_pick), selected = end is RepeatEnd.After) {
            if (end !is RepeatEnd.After) onChange { it.copy(end = RepeatEnd.After(DEFAULT_TIMES)) }
        }
        if (end is RepeatEnd.After) {
            OutlinedTextField(
                value = end.times.takeIf { it > 0 }?.toString().orEmpty(),
                onValueChange = { text ->
                    val times = text.filter(Char::isDigit).take(3).toIntOrNull() ?: 0
                    onChange { it.copy(end = RepeatEnd.After(times)) }
                },
                label = { Text(stringResource(R.string.event_repeat_end_times)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (invalid) {
            Text(
                stringResource(R.string.error_event_repeat_end),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (datePicker) {
        val initial = (end as? RepeatEnd.OnDay)?.day ?: start.plusMonths(3)
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis -> onChange { it.copy(end = RepeatEnd.OnDay(millis.toUtcDate())) } }
                    datePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

/**
 * « Cette fois seulement » or « Celle-ci et les suivantes », before saving or deleting one event of
 * a series. Two large buttons of 56 dp, then « Annuler ».
 */
@Composable
internal fun SeriesScopeDialog(
    @StringRes title: Int,
    @StringRes body: Int,
    onPick: (SeriesScope) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { onPick(SeriesScope.ThisOnly) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.event_scope_this))
                }
                OutlinedButton(onClick = { onPick(SeriesScope.ThisAndFollowing) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.event_scope_following))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ChoiceLine(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        if (enabled) Text(stringResource(R.string.event_for_whom_change), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** « Chaque semaine, le mardi », « Chaque mois, le 2e dimanche », « Chaque année, le 12 mars »… */
@Composable
internal fun repeatLabel(choice: RepeatChoice, date: LocalDate?): String {
    if (date == null || choice == RepeatChoice.None) return stringResource(R.string.event_repeat_none)
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRANCE)
    return when (choice) {
        RepeatChoice.None -> stringResource(R.string.event_repeat_none)
        RepeatChoice.Weekly -> stringResource(R.string.event_repeat_weekly, weekday)
        RepeatChoice.Biweekly -> stringResource(R.string.event_repeat_biweekly, weekday)
        RepeatChoice.MonthlyDate -> stringResource(R.string.event_repeat_monthly_date, date.dayOfMonth)
        RepeatChoice.MonthlyWeekday -> {
            val nth = (date.dayOfMonth + 6) / 7
            val rank = if (nth == 1) stringResource(R.string.event_repeat_first) else stringResource(R.string.event_repeat_nth, nth)
            stringResource(R.string.event_repeat_monthly_weekday, rank, weekday)
        }
        RepeatChoice.Yearly -> stringResource(R.string.event_repeat_yearly, date.format(dayMonth))
    }
}

private const val DEFAULT_TIMES = 10
private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)
