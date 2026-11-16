package fr.familia.app.feature.trips.step

import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.StepType
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.trips.TripDateButton
import fr.familia.app.feature.trips.TripSectionTitle
import fr.familia.app.feature.trips.TripTimeButton
import fr.familia.app.feature.trips.stepTypeIcon
import fr.familia.app.feature.trips.stepTypeLabel
import java.time.LocalDate
import java.time.LocalTime

/** Step form (create, edit, delete). Stateless: every change goes through [onChange]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepEditScreen(
    state: StepEditUiState,
    onChange: ((StepForm) -> StepForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val editing = state as? StepEditUiState.Editing
    val hasChanges = editing?.hasChanges == true
    BackHandler(enabled = hasChanges) { confirmLeave = true }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.trip_leave_title)) },
            text = { Text(stringResource(R.string.trip_leave_body)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.trip_leave_keep)) } },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) { Text(stringResource(R.string.member_leave_discard)) }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing?.isNew == false) R.string.trip_step_edit_title else R.string.trip_step_add_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = { if (hasChanges) confirmLeave = true else onBack() }) },
                actions = { HelpButton(ScreenHelp.TripStepEdit) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                StepEditUiState.Loading, StepEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                StepEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.CardTravel,
                    title = stringResource(R.string.trip_step_unavailable_title),
                    body = stringResource(R.string.trip_step_unavailable_body),
                )
                is StepEditUiState.Editing -> FormContent(state, onChange, onSave, onDelete)
            }
        }
    }
}

@Composable
private fun FormContent(
    state: StepEditUiState.Editing,
    onChange: ((StepForm) -> StepForm) -> Unit,
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
        Column {
            TripSectionTitle(stringResource(R.string.trip_step_field_type))
            Column(Modifier.selectableGroup()) {
                StepType.entries.forEach { type ->
                    val selected = form.type == type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(selected = selected, role = Role.RadioButton) { onChange { it.copy(type = type) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Icon(stepTypeIcon(type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stepTypeLabel(type), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        OutlinedTextField(
            value = form.place,
            onValueChange = { value -> onChange { it.copy(place = value) } },
            label = { Text(stringResource(R.string.trip_step_field_place)) },
            isError = StepFormError.PlaceMissing in errors || StepFormError.PlaceTooLong in errors,
            supportingText = errorText(errors, StepFormError.PlaceMissing, StepFormError.PlaceTooLong),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TripSectionTitle(stringResource(R.string.trip_step_field_depart))
            TripDateButton(form.departDate, stringResource(R.string.event_pick_date), onPick = { day ->
                onChange { it.copy(departDate = day, arriveDate = it.arriveDate?.takeIf { a -> a >= day }) }
            })
            errorText(errors, StepFormError.DepartMissing)?.invoke()
            TripTimeButton(form.departTime, stringResource(R.string.event_pick_time), onPick = { time -> onChange { it.copy(departTime = time) } })
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TripSectionTitle(stringResource(R.string.trip_step_field_arrive))
            Text(
                stringResource(R.string.trip_step_field_arrive_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TripDateButton(
                form.arriveDate ?: form.departDate,
                stringResource(R.string.event_pick_date),
                onPick = { day -> onChange { it.copy(arriveDate = day) } },
                initial = form.departDate,
            )
            TripTimeButton(form.arriveTime, stringResource(R.string.event_pick_time), onPick = { time -> onChange { it.copy(arriveTime = time) } })
            errorText(errors, StepFormError.ArriveBeforeDepart)?.invoke()
        }

        OutlinedTextField(
            value = form.note,
            onValueChange = { value -> onChange { it.copy(note = value) } },
            label = { Text(stringResource(R.string.event_note)) },
            isError = StepFormError.NoteTooLong in errors,
            supportingText = errorText(errors, StepFormError.NoteTooLong),
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.saveFailed) {
            Text(stringResource(R.string.trip_save_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            if (state.isSaving) {
                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.member_save))
            }
        }
        if (state.canDelete) DeleteStepButton(form.place, enabled = !state.isSaving, onDelete)
    }
}

@Composable
private fun DeleteStepButton(place: String, enabled: Boolean, onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { confirm = true },
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.trip_step_delete)) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.trip_step_delete_title, place.trim())) },
            text = { Text(stringResource(R.string.trip_step_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete() }) {
                    Text(stringResource(R.string.event_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_keep)) } },
        )
    }
}

@Composable
private fun errorText(errors: Set<StepFormError>, vararg kinds: StepFormError): (@Composable () -> Unit)? {
    val error = kinds.firstOrNull { it in errors } ?: return null
    val message = stringResource(
        when (error) {
            StepFormError.PlaceMissing -> R.string.error_step_place_missing
            StepFormError.PlaceTooLong -> R.string.error_event_place_too_long
            StepFormError.DepartMissing -> R.string.error_event_date_missing
            StepFormError.ArriveBeforeDepart -> R.string.error_step_arrive_before_depart
            StepFormError.NoteTooLong -> R.string.error_event_note_too_long
        },
    )
    return { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

/** Fictional data for previews and screenshots. */
internal fun previewStepEditState(isNew: Boolean = false): StepEditUiState.Editing = StepEditUiState.Editing(
    form = StepForm(
        type = StepType.Flight,
        place = "Ajaccio",
        departDate = LocalDate.of(2027, 8, 2),
        departTime = LocalTime.of(8, 15),
        arriveDate = LocalDate.of(2027, 8, 2),
        arriveTime = LocalTime.of(9, 40),
        note = "Vol AF 7520",
    ),
    isNew = isNew,
    canDelete = !isNew,
    errors = emptySet(),
)

@FamiliaPreviews
@Composable
private fun StepEditScreenPreview() {
    FamiliaTheme { Surface { StepEditScreen(previewStepEditState(), {}, {}, {}, {}) } }
}
