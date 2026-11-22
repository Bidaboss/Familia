package fr.familia.app.feature.events.edit

import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.form.FormDeleteButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.mention.MentionTextField
import fr.familia.app.core.ui.PickGroup
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.PlaceKind
import fr.familia.app.core.model.RepeatChoice
import fr.familia.app.core.photo.PhotoField
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.form.DayChips
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormLabel
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.PeopleRow
import fr.familia.app.core.ui.form.PersonChoice
import fr.familia.app.core.ui.form.PlaceChooser
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.core.ui.form.TimeBox
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
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
    onSave: (SeriesScope) -> Unit,
    onDelete: (SeriesScope) -> Unit,
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
    val editing = state as? EventEditUiState.Editing
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        topBar = {
            FamiliaTopBar(
                title = stringResource(if (editing?.isNew == false) R.string.event_edit_title else R.string.event_add_title),
                onBack = back,
                actions = { HelpButton(ScreenHelp.EventEdit) },
            )
        },
        bottomBar = {
            if (editing != null) {
                var askScope by rememberSaveable { mutableStateOf(false) }
                StickyActionBar(
                    text = stringResource(if (editing.isNew) R.string.event_add_action else R.string.member_save),
                    onClick = { if (editing.isOccurrence) askScope = true else onSave(SeriesScope.ThisOnly) },
                    busy = editing.isSaving,
                )
                if (askScope) {
                    SeriesScopeDialog(
                        title = R.string.event_scope_save_title,
                        body = R.string.event_scope_save_body,
                        onPick = { scope -> askScope = false; onSave(scope) },
                        onDismiss = { askScope = false },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                EventEditUiState.Loading, is EventEditUiState.Done ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                EventEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.EventBusy,
                    title = stringResource(R.string.event_unavailable_title),
                    body = stringResource(R.string.event_unavailable_body),
                )
                is EventEditUiState.Editing -> EventFormContent(state, onChange, onDelete)
            }
        }
    }
}

/** The tiles of DESIGN-50, in the order of the questions: what, when, where, for whom. */
private enum class FormPart { Event, When, Where, Who, Note }

private fun EventFormError.part(): FormPart = when (this) {
    EventFormError.TitleMissing, EventFormError.TitleTooLong -> FormPart.Event
    EventFormError.DateMissing, EventFormError.DateTooOld, EventFormError.RepeatEndInvalid -> FormPart.When
    EventFormError.PlaceTooLong -> FormPart.Where
    EventFormError.TargetMissing -> FormPart.Who
    EventFormError.NoteTooLong -> FormPart.Note
}

@Composable
private fun EventFormContent(
    state: EventEditUiState.Editing,
    onChange: ((EventForm) -> EventForm) -> Unit,
    onDelete: (SeriesScope) -> Unit,
) {
    val form = state.form
    val errors = state.errors
    val scroll = rememberScrollState()
    val tops = remember { mutableStateMapOf<FormPart, Int>() }
    // After a refused « Enregistrer », the page goes up to the first question to fix.
    LaunchedEffect(errors) {
        val first = errors.map { it.part() }.minOrNull() ?: return@LaunchedEffect
        tops[first]?.let { scroll.animateScrollTo(it) }
    }
    fun Modifier.part(part: FormPart) = onGloballyPositioned { tops[part] = it.positionInParent().y.toInt() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FormTile(Icons.Outlined.StarOutline, stringResource(R.string.event_tile_event), Modifier.part(FormPart.Event)) {
            FormLabel(stringResource(R.string.event_title_label))
            OutlinedTextField(
                value = form.title,
                onValueChange = { value -> onChange { it.copy(title = value) } },
                isError = EventFormError.TitleMissing in errors || EventFormError.TitleTooLong in errors,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            val titleError = errorMessage(errors, EventFormError.TitleMissing, EventFormError.TitleTooLong)
            FormHint(titleError ?: stringResource(R.string.event_title_help), isError = titleError != null)
            // A series has no cover of its own: each time can get one afterwards.
            if (state.showPhoto && !(state.isNew && form.repeat != RepeatChoice.None)) {
                PhotoField(
                    stored = state.storedCover,
                    edit = form.photo,
                    onEdit = { photo -> onChange { it.copy(photo = photo) } },
                    enabled = !state.isSaving,
                )
            }
        }

        FormTile(Icons.Outlined.CalendarMonth, stringResource(R.string.event_when), Modifier.part(FormPart.When)) {
            DayChips(date = form.date, today = familyToday(), onPick = { day -> onChange { it.copy(date = day) } })
            errorMessage(errors, EventFormError.DateMissing, EventFormError.DateTooOld)?.let { FormHint(it, isError = true) }
            TimeBox(time = form.time, onPick = { time -> onChange { it.copy(time = time) } })
            if (state.showRepeat) RepeatSection(form, state.isOccurrence, EventFormError.RepeatEndInvalid in errors, onChange)
        }

        FormTile(Icons.Outlined.Place, stringResource(R.string.event_where_tile), Modifier.part(FormPart.Where)) {
            PlaceChooser(
                kind = form.placeKind,
                onKind = { kind -> onChange { it.withPlaceKind(kind) } },
                households = state.households,
                householdId = form.placeHouseholdId,
                onHousehold = { household -> onChange { it.atHousehold(household) } },
                text = form.place,
                onText = { text -> onChange { it.copy(place = text) } },
                error = errorMessage(errors, EventFormError.PlaceTooLong),
            )
        }

        FormTile(Icons.Outlined.Groups, stringResource(R.string.event_for_whom), Modifier.part(FormPart.Who)) {
            PeopleRow(
                choices = state.targets.map { personChoice(it, it.target == form.target) },
                multiple = false,
                onToggle = { key -> state.targets.firstOrNull { it.target.key() == key }?.let { choice -> onChange { it.copy(target = choice.target) } } },
            )
            val targetError = errorMessage(errors, EventFormError.TargetMissing)
            FormHint(targetError ?: stringResource(R.string.event_for_whom_help), isError = targetError != null)
        }

        Box(Modifier.part(FormPart.Note)) { NoteField(form, errors, onChange, state.mentionGroups) }

        if (state.saveFailed) FormHint(stringResource(R.string.event_save_failed), isError = true)
        if (!state.isNew) DeleteButton(form.title, state.isOccurrence, enabled = !state.isSaving, onDelete)
        Spacer(Modifier.height(8.dp))
    }
}

/** A stable key of a target, for the rounds of « Pour qui ». */
private fun EventTarget.key(): String = when (this) {
    EventTarget.ForFamily -> "family"
    is EventTarget.ForHousehold -> "household:$householdId"
    is EventTarget.ForSelf -> "self:$memberId"
    is EventTarget.ForChild -> "child:$memberId"
}

@Composable
private fun personChoice(choice: TargetChoice, selected: Boolean): PersonChoice {
    val clay = LocalClayColors.current
    val label = targetLabel(choice)
    return when (val target = choice.target) {
        EventTarget.ForFamily -> PersonChoice(target.key(), label, selected, icon = Icons.Outlined.Groups, tint = clay.family)
        is EventTarget.ForHousehold -> PersonChoice(target.key(), label, selected, icon = Icons.Outlined.Home, tint = clay.household)
        is EventTarget.ForSelf -> PersonChoice(target.key(), label, selected, memberId = target.memberId)
        is EventTarget.ForChild -> PersonChoice(target.key(), label, selected, memberId = target.memberId)
    }
}

@Composable
private fun DeleteButton(title: String, isOccurrence: Boolean, enabled: Boolean, onDelete: (SeriesScope) -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    // The shared red button of the redone forms (refonte globale).
    FormDeleteButton(stringResource(R.string.event_delete), onClick = { confirm = true }, enabled = enabled)
    if (confirm && isOccurrence) {
        SeriesScopeDialog(
            title = R.string.event_scope_delete_title,
            body = R.string.event_scope_delete_body,
            onPick = { scope -> confirm = false; onDelete(scope) },
            onDismiss = { confirm = false },
        )
    } else if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.event_delete_title, title.trim())) },
            text = { Text(stringResource(R.string.event_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete(SeriesScope.ThisOnly) }) {
                    Text(stringResource(R.string.event_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_keep)) }
            },
        )
    }
}

/** The note stays folded behind « Ajouter une note » until it is used (audit DESIGN du 15/11). */
@Composable
private fun NoteField(
    form: EventForm,
    errors: Set<EventFormError>,
    onChange: ((EventForm) -> EventForm) -> Unit,
    groups: List<PickGroup>,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    if (!open && form.note.isEmpty()) {
        OutlinedButton(
            onClick = { open = true },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = LocalClayColors.current.card, contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.event_note_add), fontWeight = FontWeight.Bold)
        }
        return
    }
    FormTile(Icons.AutoMirrored.Outlined.Notes, stringResource(R.string.event_note)) {
        // « @Prénom » names someone of the family (Kévin, 19/11).
        MentionTextField(
            text = form.note,
            mentionIds = form.mentionIds,
            onChange = { text, ids -> onChange { it.copy(note = text, mentionIds = ids) } },
            groups = groups,
            label = null,
            isError = EventFormError.NoteTooLong in errors,
            minLines = 3,
        )
        errorMessage(errors, EventFormError.NoteTooLong)?.let { FormHint(it, isError = true) }
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
private fun errorMessage(errors: Set<EventFormError>, vararg kinds: EventFormError): String? {
    val error = kinds.firstOrNull { it in errors } ?: return null
    return stringResource(
        when (error) {
            EventFormError.TitleMissing -> R.string.error_event_title_missing
            EventFormError.TitleTooLong -> R.string.error_event_title_too_long
            EventFormError.DateMissing -> R.string.error_event_date_missing
            EventFormError.PlaceTooLong -> R.string.error_event_place_too_long
            EventFormError.NoteTooLong -> R.string.error_event_note_too_long
            EventFormError.TargetMissing -> R.string.error_event_target_missing
            EventFormError.RepeatEndInvalid -> R.string.error_event_repeat_end
            EventFormError.DateTooOld -> R.string.error_event_date_too_old
        },
    )
}

internal val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.FRANCE)

// The date picker works in UTC milliseconds.
internal fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
internal fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Fictional data for previews and screenshots. */
internal fun previewEventState(isNew: Boolean = true, errors: Set<EventFormError> = emptySet()) = EventEditUiState.Editing(
    form = EventForm(
        title = "Repas de Noël",
        date = LocalDate.of(2026, 12, 24),
        time = LocalTime.of(19, 30),
        place = "Foyer Martin",
        target = EventTarget.ForFamily,
        placeKind = PlaceKind.Household,
        placeHouseholdId = "h1",
    ),
    isNew = isNew,
    targets = listOf(
        TargetChoice(EventTarget.ForFamily, null),
        TargetChoice(EventTarget.ForHousehold("h1"), "Foyer Martin"),
        TargetChoice(EventTarget.ForSelf("a"), null),
        TargetChoice(EventTarget.ForChild("c"), "Noé"),
    ),
    errors = errors,
    households = listOf(Household("h1", "Foyer Martin", "3 rue des Lilas, Nantes"), Household("h2", "Foyer Durand")),
)

@FamiliaPreviews
@Composable
private fun EventEditScreenPreview() {
    FamiliaTheme { Surface { EventEditScreen(previewEventState(isNew = false), {}, {}, {}, {}) } }
}
