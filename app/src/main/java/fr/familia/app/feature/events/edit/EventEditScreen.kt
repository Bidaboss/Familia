package fr.familia.app.feature.events.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.formatTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Event form (create, edit, delete). Stateless: every change goes through [onChange]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    state: EventEditUiState,
    onChange: ((EventForm) -> EventForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val hasChanges = (state as? EventEditUiState.Editing)?.hasChanges == true
    val back: () -> Unit = {
        if (hasChanges) {
            confirmLeave = true
        } else {
            onBack()
        }
    }
    BackHandler(enabled = hasChanges) { confirmLeave = true }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.event_leave_title)) },
            text = { Text(stringResource(R.string.event_leave_body)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.member_leave_keep)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) {
                    Text(stringResource(R.string.member_leave_discard))
                }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if ((state as? EventEditUiState.Editing)?.isNew == false) R.string.event_edit_title else R.string.event_add_title,
                        ),
                    )
                },
                navigationIcon = {
                    BackButton(onClick = back)
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                EventEditUiState.Loading, EventEditUiState.Done ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                EventEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.EventBusy,
                    title = stringResource(R.string.event_unavailable_title),
                    body = stringResource(R.string.event_unavailable_body),
                )
                is EventEditUiState.Editing -> EventFormContent(state, onChange, onSave, onDelete)
            }
        }
    }
}

@Composable
private fun EventFormContent(
    state: EventEditUiState.Editing,
    onChange: ((EventForm) -> EventForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val form = state.form
    val errors = state.errors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = form.title,
            onValueChange = { value -> onChange { it.copy(title = value) } },
            label = { Text(stringResource(R.string.event_title)) },
            isError = EventFormError.TitleMissing in errors || EventFormError.TitleTooLong in errors,
            supportingText = errorText(errors, EventFormError.TitleMissing, EventFormError.TitleTooLong),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        DateTimeSection(form, EventFormError.DateMissing in errors, onChange)

        OutlinedTextField(
            value = form.place,
            onValueChange = { value -> onChange { it.copy(place = value) } },
            label = { Text(stringResource(R.string.event_place)) },
            isError = EventFormError.PlaceTooLong in errors,
            supportingText = errorText(errors, EventFormError.PlaceTooLong),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.note,
            onValueChange = { value -> onChange { it.copy(note = value) } },
            label = { Text(stringResource(R.string.event_note)) },
            isError = EventFormError.NoteTooLong in errors,
            supportingText = errorText(errors, EventFormError.NoteTooLong),
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.event_for_whom),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Column(Modifier.selectableGroup()) {
                state.targets.forEach { choice ->
                    val selected = form.target == choice.target
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(selected = selected, role = Role.RadioButton) {
                                onChange { it.copy(target = choice.target) }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(targetLabel(choice), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            errorText(errors, EventFormError.TargetMissing)?.invoke()
        }

        if (state.saveFailed) {
            Text(
                stringResource(R.string.event_save_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = onSave,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.member_save))
            }
        }
        if (!state.isNew) DeleteButton(form.title, enabled = !state.isSaving, onDelete)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeSection(form: EventForm, dateMissing: Boolean, onChange: ((EventForm) -> EventForm) -> Unit) {
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var timePicker by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.event_when),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        OutlinedButton(onClick = { datePicker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(form.date?.format(dateFormatter) ?: stringResource(R.string.event_pick_date))
        }
        if (dateMissing) errorText(setOf(EventFormError.DateMissing), EventFormError.DateMissing)?.invoke()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { timePicker = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(form.time?.let(::formatTime) ?: stringResource(R.string.event_pick_time))
            }
            if (form.time != null) {
                TextButton(onClick = { onChange { it.copy(time = null) } }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.event_no_time))
                }
            }
        }
    }
    if (datePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = (form.date ?: LocalDate.now()).toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis -> onChange { it.copy(date = millis.toUtcDate()) } }
                    datePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { datePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }
    if (timePicker) {
        val initial = form.time ?: LocalTime.of(12, 0)
        val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timePicker = false },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    onChange { it.copy(time = LocalTime.of(pickerState.hour, pickerState.minute)) }
                    timePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { timePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun DeleteButton(title: String, enabled: Boolean, onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { confirm = true },
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.event_delete)) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.event_delete_title, title.trim())) },
            text = { Text(stringResource(R.string.event_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete() }) {
                    Text(stringResource(R.string.event_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_keep)) }
            },
        )
    }
}

/** "Toute la famille", "Foyer Martin", "Moi", "Noé"… */
@Composable
fun targetLabel(choice: TargetChoice): String = when (choice.target) {
    EventTarget.ForFamily -> stringResource(R.string.event_for_family)
    is EventTarget.ForHousehold -> choice.name ?: stringResource(R.string.event_for_household)
    is EventTarget.ForSelf -> choice.name ?: stringResource(R.string.event_for_me)
    is EventTarget.ForChild -> choice.name ?: stringResource(R.string.event_for_child)
}

@Composable
private fun errorText(errors: Set<EventFormError>, vararg kinds: EventFormError): (@Composable () -> Unit)? {
    val error = kinds.firstOrNull { it in errors } ?: return null
    val message = stringResource(
        when (error) {
            EventFormError.TitleMissing -> R.string.error_event_title_missing
            EventFormError.TitleTooLong -> R.string.error_event_title_too_long
            EventFormError.DateMissing -> R.string.error_event_date_missing
            EventFormError.PlaceTooLong -> R.string.error_event_place_too_long
            EventFormError.NoteTooLong -> R.string.error_event_note_too_long
            EventFormError.TargetMissing -> R.string.error_event_target_missing
        },
    )
    return { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.FRANCE)

// The date picker works in UTC milliseconds.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Fictional data for previews and screenshots. */
internal fun previewEventState(isNew: Boolean = true, errors: Set<EventFormError> = emptySet()) = EventEditUiState.Editing(
    form = EventForm(
        title = "Repas de Noël",
        date = LocalDate.of(2026, 12, 24),
        time = LocalTime.of(19, 30),
        place = "Chez les parents",
        target = EventTarget.ForFamily,
    ),
    isNew = isNew,
    targets = listOf(
        TargetChoice(EventTarget.ForFamily, null),
        TargetChoice(EventTarget.ForHousehold("h1"), "Foyer Martin"),
        TargetChoice(EventTarget.ForSelf("a"), null),
        TargetChoice(EventTarget.ForChild("c"), "Noé"),
    ),
    errors = errors,
)

@FamiliaPreviews
@Composable
private fun EventEditScreenPreview() {
    FamiliaTheme { Surface { EventEditScreen(previewEventState(isNew = false), {}, {}, {}, {}) } }
}
