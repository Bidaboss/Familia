package fr.familia.app.feature.trips.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.HouseholdMemberPicker
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.trips.TripDateButton
import fr.familia.app.feature.trips.TripSectionTitle
import java.time.LocalDate

/** Trip form (create, edit, delete). Stateless: every change goes through [onChange]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripEditScreen(
    state: TripEditUiState,
    onChange: ((TripForm) -> TripForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val editing = state as? TripEditUiState.Editing
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
                title = { Text(stringResource(if (editing?.isNew == false) R.string.trip_edit_title else R.string.trip_add_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = { if (hasChanges) confirmLeave = true else onBack() }) },
                actions = { HelpButton(ScreenHelp.TripEdit) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                TripEditUiState.Loading, is TripEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                TripEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.CardTravel,
                    title = stringResource(R.string.trip_missing_title),
                    body = stringResource(R.string.trip_unavailable_body),
                )
                is TripEditUiState.Editing -> FormContent(state, onChange, onSave, onDelete)
            }
        }
    }
}

@Composable
private fun FormContent(
    state: TripEditUiState.Editing,
    onChange: ((TripForm) -> TripForm) -> Unit,
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
            label = { Text(stringResource(R.string.trip_field_title)) },
            isError = TripFormError.TitleMissing in errors || TripFormError.TitleTooLong in errors,
            supportingText = errorText(errors, TripFormError.TitleMissing, TripFormError.TitleTooLong),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TripSectionTitle(stringResource(R.string.trip_field_dates))
            TripDateButton(form.startDate, stringResource(R.string.trip_pick_start), onPick = { day ->
                onChange { it.copy(startDate = day, endDate = it.endDate?.takeIf { end -> end >= day } ?: day) }
            })
            errorText(errors, TripFormError.StartMissing)?.invoke()
            TripDateButton(
                form.endDate,
                stringResource(R.string.trip_pick_end),
                onPick = { day -> onChange { it.copy(endDate = day) } },
                initial = form.startDate,
            )
            errorText(errors, TripFormError.EndMissing, TripFormError.EndBeforeStart)?.invoke()
        }

        Column {
            TripSectionTitle(stringResource(R.string.trip_field_participants))
            Text(
                stringResource(R.string.trip_field_participants_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ParticipantShortcuts(state.participants, form.participantIds) { ids -> onChange { it.withParticipants(ids) } }
            // By household, a box per household ticks all of its members (règle de Kévin, 17/11).
            // The creator always takes part: unticking them, alone or with their household, does nothing.
            val locked = state.participants.filter { it.locked }.map { it.id }.toSet()
            HouseholdMemberPicker(
                groups = state.participantGroups,
                selected = form.participantIds + locked,
                onChange = { picked -> onChange { it.withParticipantSet(picked + locked) } },
                locked = locked,
            )
            errorText(errors, TripFormError.TooManyParticipants)?.invoke()
        }

        Column {
            TripSectionTitle(stringResource(R.string.trip_field_readers))
            Text(
                stringResource(R.string.trip_field_readers_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // A participant always follows the trip: their box stays ticked, and says why.
            val taking = stringResource(R.string.trip_reader_participant)
            HouseholdMemberPicker(
                groups = state.readerGroups,
                selected = form.readerIds + form.participantIds,
                onChange = { picked -> onChange { it.withReaderSet(picked) } },
                label = { member -> if (member.id in form.participantIds) "${member.firstName} · $taking" else member.firstName },
                locked = form.participantIds,
            )
        }

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
        if (!state.isNew) DeleteTripButton(form.title, enabled = !state.isSaving, onDelete)
    }
}

@Composable
private fun DeleteTripButton(title: String, enabled: Boolean, onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { confirm = true },
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.trip_delete)) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.trip_delete_title, title.trim())) },
            text = { Text(stringResource(R.string.trip_delete_body)) },
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
private fun errorText(errors: Set<TripFormError>, vararg kinds: TripFormError): (@Composable () -> Unit)? {
    val error = kinds.firstOrNull { it in errors } ?: return null
    val message = stringResource(
        when (error) {
            TripFormError.TitleMissing -> R.string.error_trip_title_missing
            TripFormError.TitleTooLong -> R.string.error_event_title_too_long
            TripFormError.StartMissing -> R.string.error_trip_start_missing
            TripFormError.EndMissing -> R.string.error_trip_end_missing
            TripFormError.EndBeforeStart -> R.string.error_trip_end_before_start
            TripFormError.TooManyParticipants -> R.string.error_trip_too_many
        },
    )
    return { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

/** Fictional data for previews and screenshots. */
internal fun previewTripEditState(isNew: Boolean = true, errors: Set<TripFormError> = emptySet()): TripEditUiState.Editing {
    val people = listOf(
        MemberChoice("a", "Camille", "Foyer Martin", locked = true),
        MemberChoice("b", "Inès", "Foyer Bernard"),
        MemberChoice("c", "Noé", "Foyer Martin"),
        MemberChoice("d", "Sam", "Foyer Bernard"),
    )
    return TripEditUiState.Editing(
        form = TripForm(
            title = "Corse",
            startDate = LocalDate.of(2027, 8, 2),
            endDate = LocalDate.of(2027, 8, 16),
            participantIds = setOf("a", "b"),
            readerIds = setOf("a", "b", "c"),
        ),
        isNew = isNew,
        participants = people,
        readers = people.map { it.copy(locked = false) },
        errors = errors,
    )
}

@FamiliaPreviews
@Composable
private fun TripEditScreenPreview() {
    FamiliaTheme { Surface { TripEditScreen(previewTripEditState(isNew = false), {}, {}, {}, {}) } }
}

/**
 * « Toute la famille », then « Tout le foyer … » for each household (PRD rév. 190): several
 * households often leave together. Each adds everyone of it; unticking stays one by one. A shortcut
 * whose people are all ticked already is not offered.
 */
@Composable
private fun ParticipantShortcuts(choices: List<MemberChoice>, taking: Set<String>, onAdd: (List<String>) -> Unit) {
    val missing = { ids: List<String> -> ids.any { it !in taking } }
    val everyone = choices.map { it.id }
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (missing(everyone)) {
            OutlinedButton(onClick = { onAdd(everyone) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.trip_add_everyone))
            }
        }
    }
}
