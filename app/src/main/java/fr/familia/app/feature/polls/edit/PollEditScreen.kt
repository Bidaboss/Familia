package fr.familia.app.feature.polls.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Poll
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.HouseholdMemberPicker
import fr.familia.app.core.ui.PickGroup
import fr.familia.app.core.ui.formatDay
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.polls.closingDayLabel
import fr.familia.app.feature.polls.kindLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** The « Nouveau sondage » form (récits 2 bis et 2 ter). Stateless: every change goes through the callbacks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollEditScreen(
    state: PollEditUiState,
    onChange: ((PollForm) -> PollForm) -> Unit,
    onAsked: (Set<String>) -> Unit,
    onShortcut: (AskShortcut) -> Unit,
    onAddOption: () -> Unit,
    onRemoveOption: (Int) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    today: LocalDate = familyToday(),
) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val hasChanges = (state as? PollEditUiState.Editing)?.hasChanges == true
    BackHandler(enabled = hasChanges) { confirmLeave = true }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.poll_leave_title)) },
            text = { Text(stringResource(R.string.poll_leave_body)) },
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
                title = { Text(stringResource(R.string.poll_new_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = { if (hasChanges) confirmLeave = true else onBack() }) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                PollEditUiState.Loading, PollEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                PollEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.Poll,
                    title = stringResource(R.string.poll_unavailable_title),
                    body = stringResource(R.string.poll_unavailable_body),
                )
                is PollEditUiState.Editing -> FormContent(state, today, onChange, onAsked, onShortcut, onAddOption, onRemoveOption, onSave)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormContent(
    state: PollEditUiState.Editing,
    today: LocalDate,
    onChange: ((PollForm) -> PollForm) -> Unit,
    onAsked: (Set<String>) -> Unit,
    onShortcut: (AskShortcut) -> Unit,
    onAddOption: () -> Unit,
    onRemoveOption: (Int) -> Unit,
    onSave: () -> Unit,
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
        val event = state.forEvent
        if (event != null) {
            // Asked from an event (retour du 18/11): about it only, the event never changes here.
            EventHeader(event, today)
            SectionTitle(stringResource(R.string.poll_motive))
            val questions = PollMotive.entries.associateWith { motiveQuestion(it, event.title) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PollMotive.entries.forEach { motive ->
                    val selected = form.motive == motive
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onChange { f ->
                                // « Autre question » keeps what was typed, only a question written in by a motive goes.
                                val written = questions.getValue(motive).ifEmpty { f.question.takeUnless { it in questions.values }.orEmpty() }
                                f.withMotive(motive, written)
                            }
                        },
                        label = { Text(motiveLabel(motive, event.title), style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                        modifier = Modifier.heightIn(min = 56.dp),
                    )
                }
            }
        } else {
            SectionTitle(stringResource(R.string.poll_kind))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Poll.Kind.entries.forEach { kind ->
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
        }

        OutlinedTextField(
            value = form.question,
            onValueChange = { value -> onChange { it.copy(question = value) } },
            label = { Text(stringResource(R.string.poll_question)) },
            placeholder = {
                Text(
                    event?.let { stringResource(R.string.poll_question_hint_event, it.title) }
                        ?: stringResource(if (form.kind == Poll.Kind.Date) R.string.poll_question_hint_date else R.string.poll_question_hint_choice),
                )
            },
            isError = PollFormError.QuestionMissing in errors || PollFormError.QuestionTooLong in errors,
            supportingText = errorText(errors, PollFormError.QuestionMissing, PollFormError.QuestionTooLong),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        if (form.kind == Poll.Kind.Date) {
            DatesSection(form, errors, today, onChange, onRemoveOption)
        } else {
            ChoicesSection(form, errors, onChange, onRemoveOption)
        }
        if (form.canAddOption) {
            OutlinedButton(onClick = onAddOption, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (form.kind == Poll.Kind.Date) R.string.poll_add_date else R.string.poll_add_choice))
            }
        }
        if (form.kind == Poll.Kind.Choice) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(value = form.multiple, role = Role.Switch) { value -> onChange { it.copy(multiple = value) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.poll_multiple), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = form.multiple, onCheckedChange = null)
            }
        }

        AskedSection(state, onAsked, onShortcut)
        ClosesSection(form, errors, today, onChange)

        if (state.saveFailed) {
            Text(stringResource(R.string.poll_save_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            if (state.isSaving) {
                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.poll_send))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DatesSection(
    form: PollForm,
    errors: Set<PollFormError>,
    today: LocalDate,
    onChange: ((PollForm) -> PollForm) -> Unit,
    onRemoveOption: (Int) -> Unit,
) {
    // The index of the date whose day or time picker is open.
    var dayPicker by rememberSaveable { mutableStateOf<Int?>(null) }
    var timePicker by rememberSaveable { mutableStateOf<Int?>(null) }
    fun set(index: Int, change: (DateChoice) -> DateChoice) = onChange { f ->
        f.copy(dates = f.dates.mapIndexed { i, d -> if (i == index) change(d) else d })
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.poll_dates))
        Hint(stringResource(R.string.poll_dates_hint))
        form.dates.forEachIndexed { index, choice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { dayPicker = index }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(choice.day?.let { formatDay(it, today) } ?: stringResource(R.string.event_pick_date))
                    }
                    if (choice.day != null) {
                        TextButton(onClick = { timePicker = index }, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(choice.time?.let(::formatTime) ?: stringResource(R.string.poll_add_time))
                        }
                    }
                }
                if (form.canRemoveOption) {
                    val name = choice.day?.let { formatDay(it, today) } ?: stringResource(R.string.event_pick_date)
                    IconButton(onClick = { onRemoveOption(index) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.poll_remove_option, name))
                    }
                }
            }
        }
        errorText(errors, PollFormError.TooFewOptions, PollFormError.DatePast)?.invoke()
    }
    val day = dayPicker
    if (day != null) {
        val initial = form.dates.getOrNull(day)?.day ?: today
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { dayPicker = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis -> set(day) { it.copy(day = millis.toUtcDate()) } }
                    dayPicker = null
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { dayPicker = null }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
    val time = timePicker
    if (time != null) {
        val initial = form.dates.getOrNull(time)?.time ?: LocalTime.of(12, 0)
        val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timePicker = null },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    set(time) { it.copy(time = LocalTime.of(pickerState.hour, pickerState.minute)) }
                    timePicker = null
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    set(time) { it.copy(time = null) }
                    timePicker = null
                }) { Text(stringResource(R.string.event_no_time)) }
            },
        )
    }
}

@Composable
private fun ChoicesSection(
    form: PollForm,
    errors: Set<PollFormError>,
    onChange: ((PollForm) -> PollForm) -> Unit,
    onRemoveOption: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.poll_choices))
        Hint(stringResource(R.string.poll_choices_hint))
        form.choices.forEachIndexed { index, text ->
            val label = stringResource(R.string.poll_choice_n, index + 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        onChange { f -> f.copy(choices = f.choices.mapIndexed { i, c -> if (i == index) value else c }) }
                    },
                    label = { Text(label) },
                    isError = text.trim().length > Poll.MAX_OPTION,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                )
                if (form.canRemoveOption) {
                    IconButton(onClick = { onRemoveOption(index) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.poll_remove_option, label))
                    }
                }
            }
        }
        errorText(errors, PollFormError.TooFewOptions, PollFormError.OptionTooLong)?.invoke()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskedSection(state: PollEditUiState.Editing, onAsked: (Set<String>) -> Unit, onShortcut: (AskShortcut) -> Unit) {
    val asked = state.form.askedIds
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.poll_asked))
        Hint(stringResource(R.string.poll_asked_hint))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            state.shortcuts.filter { it.label == null }.forEach { shortcut ->
                val selected = asked == shortcut.memberIds
                FilterChip(
                    selected = selected,
                    onClick = { onShortcut(shortcut) },
                    label = { Text(shortcut.label ?: stringResource(R.string.poll_asked_everyone), style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
        // Each household ticks all its members (règle de Kévin, 17/11): the chips keep « Tout le monde ».
        HouseholdMemberPicker(
            groups = state.groups.map { PickGroup(it.name, it.members) },
            selected = asked,
            onChange = onAsked,
        )
        errorText(state.errors, PollFormError.NobodyAsked)?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ClosesSection(form: PollForm, errors: Set<PollFormError>, today: LocalDate, onChange: ((PollForm) -> PollForm) -> Unit) {
    var picker by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.poll_closes))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(
                    form.closesOn?.let { stringResource(R.string.poll_closes_value, closingDayLabel(it)) }
                        ?: stringResource(R.string.poll_closes_pick),
                )
            }
            if (form.closesOn != null) {
                TextButton(onClick = { onChange { it.copy(closesOn = null) } }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.poll_closes_remove))
                }
            }
        }
        if (form.closesOn == null) Hint(stringResource(R.string.poll_closes_none))
        errorText(errors, PollFormError.ClosesPast)?.invoke()
    }
    if (picker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = (form.closesOn ?: today.plusDays(7)).toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis -> onChange { it.copy(closesOn = millis.toUtcDate()) } }
                    picker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

/** « Pour : <titre> · <date> », at the top of a poll asked from an event; it cannot be changed. */
@Composable
private fun EventHeader(event: PollEvent, today: LocalDate) {
    Text(
        stringResource(R.string.poll_for_event, event.title, formatDay(event.date, today)),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
private fun motiveLabel(motive: PollMotive, title: String): String = when (motive) {
    PollMotive.Date -> stringResource(R.string.poll_motive_date)
    PollMotive.Place -> stringResource(R.string.poll_motive_place)
    PollMotive.Menu -> stringResource(R.string.poll_motive_menu)
    PollMotive.Other -> stringResource(R.string.poll_motive_other, title)
}

/** The question each motive writes in; « Autre question » leaves it to write. */
@Composable
private fun motiveQuestion(motive: PollMotive, title: String): String = when (motive) {
    PollMotive.Date -> stringResource(R.string.poll_motive_q_date, title)
    PollMotive.Place -> stringResource(R.string.poll_motive_q_place, title)
    PollMotive.Menu -> stringResource(R.string.poll_motive_q_menu, title)
    PollMotive.Other -> ""
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun errorText(errors: Set<PollFormError>, vararg kinds: PollFormError): (@Composable () -> Unit)? {
    val error = kinds.firstOrNull { it in errors } ?: return null
    val message = when (error) {
        PollFormError.QuestionMissing -> stringResource(R.string.poll_error_question_missing)
        PollFormError.QuestionTooLong -> stringResource(R.string.poll_error_question_too_long)
        PollFormError.TooFewOptions -> stringResource(R.string.poll_error_options)
        PollFormError.OptionTooLong -> stringResource(R.string.poll_error_option_too_long)
        PollFormError.DatePast -> stringResource(R.string.poll_error_date_past)
        PollFormError.NobodyAsked -> stringResource(R.string.poll_error_nobody)
        PollFormError.ClosesPast -> stringResource(R.string.poll_error_closes_past)
    }
    return { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

// The date picker works in UTC milliseconds.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Fictional data for previews and screenshots. */
internal fun previewPollEdit(kind: Poll.Kind = Poll.Kind.Date, errors: Set<PollFormError> = emptySet()): PollEditUiState.Editing {
    fun member(id: String, name: String, household: String, profile: MemberProfile = MemberProfile.Adult) =
        Member(id, name, null, null, household, profile, false, emptyList(), null)
    val martin = listOf(member("me", "Camille", "h1"), member("jules", "Jules", "h1"), member("noe", "Noé", "h1", MemberProfile.ChildWithoutApp))
    val petit = listOf(member("ines", "Inès", "h2"), member("lou", "Lou", "h2"))
    val everyone = (martin + petit).map { it.id }.toSet()
    return PollEditUiState.Editing(
        form = PollForm(
            kind = kind,
            question = if (kind == Poll.Kind.Date) "Quand fait-on le repas de Noël ?" else "Quel restaurant pour l'anniversaire ?",
            dates = listOf(DateChoice(LocalDate.of(2026, 12, 19), LocalTime.of(12, 30)), DateChoice(LocalDate.of(2026, 12, 20))),
            choices = listOf("La Pizzeria", "Le Bistrot", ""),
            multiple = true,
            askedIds = everyone,
            closesOn = LocalDate.of(2026, 12, 1),
        ),
        groups = listOf(AskGroup("Foyer Martin", martin), AskGroup("Foyer Petit", petit)),
        shortcuts = listOf(AskShortcut(null, everyone), AskShortcut("Foyer Martin", martin.map { it.id }.toSet()), AskShortcut("Foyer Petit", petit.map { it.id }.toSet())),
        errors = errors,
    )
}

@FamiliaPreviews
@Composable
private fun PollEditScreenPreview() {
    FamiliaTheme { Surface { PollEditScreen(previewPollEdit(), {}, {}, {}, {}, {}, {}, {}, today = LocalDate.of(2026, 11, 20)) } }
}
