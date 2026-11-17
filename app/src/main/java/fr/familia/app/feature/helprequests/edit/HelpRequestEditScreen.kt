package fr.familia.app.feature.helprequests.edit

import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import fr.familia.app.core.photo.PhotoField
import fr.familia.app.core.model.HelpKind
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.HouseholdMemberPicker
import fr.familia.app.core.ui.PickGroup
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.helprequests.kindLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The request form (récits 13 and 14). Stateless: every change goes through the callbacks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpRequestEditScreen(
    state: HelpRequestEditUiState,
    onChange: ((HelpRequestForm) -> HelpRequestForm) -> Unit,
    onPick: (Set<String>) -> Unit,
    onShortcut: (NotifyShortcut) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    /** A photo can be joined (Kévin 16/11): false when photos cannot be sent. */
    photoOn: Boolean = true,
) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val hasChanges = (state as? HelpRequestEditUiState.Editing)?.hasChanges == true
    BackHandler(enabled = hasChanges) { confirmLeave = true }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.helprequest_leave_title)) },
            text = { Text(stringResource(R.string.event_leave_body)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.member_leave_keep)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) { Text(stringResource(R.string.member_leave_discard)) }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if ((state as? HelpRequestEditUiState.Editing)?.isNew == false) R.string.helprequest_edit_title else R.string.helprequest_add_title,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton(onClick = { if (hasChanges) confirmLeave = true else onBack() }) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                HelpRequestEditUiState.Loading, HelpRequestEditUiState.Done ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                HelpRequestEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.VolunteerActivism,
                    title = stringResource(R.string.helprequest_unavailable_title),
                    body = stringResource(R.string.helprequest_unavailable_body),
                )
                is HelpRequestEditUiState.Editing -> FormContent(state, onChange, onPick, onShortcut, onSave, photoOn)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormContent(
    state: HelpRequestEditUiState.Editing,
    onChange: ((HelpRequestForm) -> HelpRequestForm) -> Unit,
    onPick: (Set<String>) -> Unit,
    onShortcut: (NotifyShortcut) -> Unit,
    onSave: () -> Unit,
    photoOn: Boolean,
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
        SectionTitle(stringResource(R.string.helprequest_kind))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HelpKind.entries.forEach { kind ->
                val selected = form.kind == kind
                FilterChip(
                    selected = selected,
                    onClick = { onChange { it.copy(kind = kind) } },
                    label = { Text(kindLabel(kind), style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }

        OutlinedTextField(
            value = form.title,
            onValueChange = { value -> onChange { it.copy(title = value) } },
            label = { Text(stringResource(R.string.helprequest_title_field)) },
            placeholder = { Text(stringResource(R.string.helprequest_title_hint)) },
            isError = HelpFormError.TitleMissing in errors || HelpFormError.TitleTooLong in errors,
            supportingText = errorText(errors, HelpFormError.TitleMissing, HelpFormError.TitleTooLong),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        WhenSection(form, errors, onChange)

        OutlinedTextField(
            value = form.place,
            onValueChange = { value -> onChange { it.copy(place = value) } },
            label = { Text(stringResource(R.string.event_place)) },
            isError = HelpFormError.PlaceTooLong in errors,
            supportingText = errorText(errors, HelpFormError.PlaceTooLong),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.note,
            onValueChange = { value -> onChange { it.copy(note = value) } },
            label = { Text(stringResource(R.string.event_note)) },
            isError = HelpFormError.NoteTooLong in errors,
            supportingText = errorText(errors, HelpFormError.NoteTooLong),
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        // A photo to show what it is about: a QR code, a parcel, a place (Kévin 16/11).
        if (photoOn) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionTitle(stringResource(R.string.helprequest_photo_title))
                Text(stringResource(R.string.helprequest_photo_hint), style = MaterialTheme.typography.bodyMedium)
                PhotoField(
                    stored = state.storedPhoto,
                    edit = form.photo,
                    onEdit = { edit -> onChange { it.copy(photo = edit) } },
                    enabled = !state.isSaving,
                )
            }
        }

        if (state.targets.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionTitle(stringResource(R.string.helprequest_for_whom))
                Column(Modifier.selectableGroup()) {
                    state.targets.forEachIndexed { index, member ->
                        val selected = form.forMemberId == member.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .selectable(selected = selected, role = Role.RadioButton) {
                                    onChange { it.copy(forMemberId = member.id) }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                if (index == 0) stringResource(R.string.event_for_me) else member.firstName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }

        NotifySection(state, onPick, onShortcut)

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
                Text(stringResource(if (state.isNew) R.string.helprequest_send else R.string.member_save))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotifySection(
    state: HelpRequestEditUiState.Editing,
    onPick: (Set<String>) -> Unit,
    onShortcut: (NotifyShortcut) -> Unit,
) {
    val warned = state.warnedIds
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.helprequest_notify))
        Text(
            stringResource(R.string.helprequest_notify_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            state.shortcuts.forEach { shortcut ->
                val selected = warned.containsAll(shortcut.memberIds)
                FilterChip(
                    selected = selected,
                    onClick = { onShortcut(shortcut) },
                    label = {
                        Text(
                            shortcut.label ?: stringResource(R.string.helprequest_notify_everyone),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
        // By household, a box per household ticks all of its adults (règle du 17/11). The people
        // always warned stay ticked: they are warned whatever is picked.
        val always = stringResource(R.string.helprequest_notify_always)
        val others = stringResource(R.string.helprequest_notify_others)
        HouseholdMemberPicker(
            groups = state.groups.map { if (it.name.isBlank()) it.copy(name = others) else it },
            selected = warned,
            onChange = onPick,
            label = { adult -> if (adult.id in state.lockedIds) "${adult.firstName} · $always" else adult.firstName },
        )
        errorText(state.errors, HelpFormError.NobodyWarned)?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenSection(form: HelpRequestForm, errors: Set<HelpFormError>, onChange: ((HelpRequestForm) -> HelpRequestForm) -> Unit) {
    var datePicker by rememberSaveable { mutableStateOf(false) }
    // "start" or "end" while a time picker is open.
    var timePicker by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.event_when))
        OutlinedButton(onClick = { datePicker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(form.date?.format(dateFormatter) ?: stringResource(R.string.event_pick_date))
        }
        errorText(errors, HelpFormError.DateMissing, HelpFormError.DatePast)?.invoke()
        TimeRow(R.string.helprequest_start, form.startTime, onPick = { timePicker = "start" }) {
            onChange { it.copy(startTime = null) }
        }
        TimeRow(R.string.helprequest_end, form.endTime, onPick = { timePicker = "end" }) {
            onChange { it.copy(endTime = null) }
        }
        errorText(errors, HelpFormError.EndBeforeStart)?.invoke()
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
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
    val which = timePicker
    if (which != null) {
        val initial = (if (which == "start") form.startTime else form.endTime) ?: LocalTime.of(if (which == "start") 9 else 12, 0)
        val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timePicker = null },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val time = LocalTime.of(pickerState.hour, pickerState.minute)
                    onChange { if (which == "start") it.copy(startTime = time) else it.copy(endTime = time) }
                    timePicker = null
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { timePicker = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** "Début : 18 h 00" with a button to remove it, or "Ajouter une heure de début". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeRow(labelRes: Int, time: LocalTime?, onPick: () -> Unit, onClear: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPick, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(
                time?.let { stringResource(R.string.helprequest_time_value, stringResource(labelRes), formatTime(it)) }
                    ?: stringResource(R.string.helprequest_time_add, stringResource(labelRes).lowercase(Locale.FRANCE)),
            )
        }
        if (time != null) {
            TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.event_no_time)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun errorText(errors: Set<HelpFormError>, vararg kinds: HelpFormError): (@Composable () -> Unit)? {
    val error = kinds.firstOrNull { it in errors } ?: return null
    val message = when (error) {
        HelpFormError.TitleMissing -> stringResource(R.string.error_event_title_missing)
        HelpFormError.TitleTooLong -> stringResource(R.string.error_event_title_too_long)
        HelpFormError.DateMissing -> stringResource(R.string.error_event_date_missing)
        HelpFormError.DatePast -> stringResource(R.string.helprequest_error_date_past)
        HelpFormError.EndBeforeStart -> stringResource(R.string.helprequest_error_end_before_start)
        HelpFormError.PlaceTooLong -> stringResource(R.string.error_event_place_too_long)
        HelpFormError.NoteTooLong -> stringResource(R.string.error_event_note_too_long)
        HelpFormError.NobodyWarned -> stringResource(R.string.helprequest_error_nobody)
    }
    return { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.FRANCE)

// The date picker works in UTC milliseconds.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Fictional data for previews and screenshots. */
internal fun previewHelpRequestEdit(errors: Set<HelpFormError> = emptySet()): HelpRequestEditUiState.Editing {
    fun adult(id: String, name: String, household: String) = Member(id, name, null, null, household, MemberProfile.Adult, false, emptyList(), null)
    val me = adult("me", "Camille", "h1")
    val kid = Member("noe", "Noé", null, null, "h1", MemberProfile.ChildWithoutApp, false, listOf("me", "jules"), null)
    val adults = listOf(adult("ines", "Inès", "h2"), adult("jules", "Jules", "h1"), adult("lou", "Lou", "h2"))
    return HelpRequestEditUiState.Editing(
        form = HelpRequestForm(
            kind = HelpKind.Childcare,
            title = "Garder Noé mercredi après-midi",
            date = LocalDate.of(2026, 11, 25),
            startTime = LocalTime.of(14, 0),
            endTime = LocalTime.of(18, 0),
            forMemberId = "noe",
            notifyIds = setOf("ines"),
        ),
        isNew = true,
        targets = listOf(me, kid),
        adults = adults,
        shortcuts = listOf(NotifyShortcut(null, adults.map { it.id }.toSet())),
        groups = listOf(PickGroup("Foyer Martin", listOf(adults[1])), PickGroup("Foyer Petit", listOf(adults[0], adults[2]))),
        lockedIds = setOf("jules"),
        errors = errors,
    )
}

@FamiliaPreviews
@Composable
private fun HelpRequestEditScreenPreview() {
    FamiliaTheme { Surface { HelpRequestEditScreen(previewHelpRequestEdit(), {}, {}, {}, {}, {}) } }
}
