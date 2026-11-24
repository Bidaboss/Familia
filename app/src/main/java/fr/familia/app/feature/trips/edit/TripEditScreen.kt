package fr.familia.app.feature.trips.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripLandscape
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.HouseholdMemberPicker
import fr.familia.app.core.ui.PickGroup
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormLabel
import fr.familia.app.core.ui.form.PeopleRow
import fr.familia.app.core.ui.form.PersonChoice
import fr.familia.app.core.ui.householdState
import fr.familia.app.core.ui.toggleHousehold
import fr.familia.app.core.ui.toggleMember
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.trips.TripDayBox
import fr.familia.app.feature.trips.TripDeleteButton
import fr.familia.app.feature.trips.TripFormScaffold
import fr.familia.app.feature.trips.TripTile
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.list.MiniLandscape
import fr.familia.app.feature.trips.list.tripLength
import fr.familia.app.feature.trips.list.tripRangeEnds
import java.time.LocalDate

/** The parts of the form, in their order on the page, to go up to the first one to fix. */
private enum class TripPart { Trip, Who }

private fun TripFormError.part(): TripPart = when (this) {
    TripFormError.TooManyParticipants -> TripPart.Who
    else -> TripPart.Trip
}

/**
 * Trip form (create, edit, delete), in tiles on the sky of the trips (récit 81, DESIGN-56).
 * Stateless: every change goes through [onChange].
 */
@Composable
fun TripEditScreen(
    state: TripEditUiState,
    onChange: ((TripForm) -> TripForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    /** The image of the place (règle 12): a real photo, else satellite, else the drawn landscape; null: the drawing. */
    cover: (@Composable (Modifier) -> Unit)? = null,
    /** The form sits in the sheet under the map of the trip (règle 4). */
    inSheet: Boolean = false,
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
    TripFormScaffold(
        title = stringResource(if (editing?.isNew == false) R.string.trip_edit_title else R.string.trip_add_title),
        help = ScreenHelp.TripEdit,
        onBack = { if (hasChanges) confirmLeave = true else onBack() },
        action = editing?.let { stringResource(if (it.isNew) R.string.trip_create else R.string.member_save) },
        busy = editing?.isSaving == true,
        onAction = onSave,
        inSheet = inSheet,
    ) {
        when (state) {
            TripEditUiState.Loading, is TripEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            TripEditUiState.Unavailable -> CompositionLocalProvider(LocalContentColor provides TripPathColors.Ink) {
                EmptyState(
                    icon = Icons.Outlined.CardTravel,
                    title = stringResource(R.string.trip_missing_title),
                    body = stringResource(R.string.trip_unavailable_body),
                )
            }
            is TripEditUiState.Editing -> FormContent(state, onChange, onDelete, cover, banner = !inSheet)
        }
    }
}

@Composable
private fun FormContent(
    state: TripEditUiState.Editing,
    onChange: ((TripForm) -> TripForm) -> Unit,
    onDelete: () -> Unit,
    cover: (@Composable (Modifier) -> Unit)?,
    /** False under the map: the satellite map above already shows the place, the image would show it twice. */
    banner: Boolean = true,
) {
    val form = state.form
    val errors = state.errors
    val scroll = rememberScrollState()
    val tops = remember { mutableStateMapOf<TripPart, Int>() }
    // After a refused « Créer le voyage », the page goes up to the first question to fix.
    LaunchedEffect(errors) {
        val first = errors.map { it.part() }.minOrNull() ?: return@LaunchedEffect
        tops[first]?.let { scroll.animateScrollTo(it) }
    }
    fun Modifier.part(part: TripPart) = onGloballyPositioned { tops[part] = it.positionInParent().y.toInt() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (banner) PreviewBanner(form, cover)

        TripTile(Icons.Outlined.CardTravel, stringResource(R.string.trip_tile_trip), Modifier.part(TripPart.Trip)) {
            FormLabel(stringResource(R.string.trip_field_title))
            val titleLabel = stringResource(R.string.trip_field_title)
            OutlinedTextField(
                value = form.title,
                onValueChange = { value -> onChange { it.copy(title = value) } },
                isError = TripFormError.TitleMissing in errors || TripFormError.TitleTooLong in errors,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = titleLabel },
            )
            val titleError = errorMessage(errors, TripFormError.TitleMissing, TripFormError.TitleTooLong)
            FormHint(titleError ?: stringResource(R.string.trip_title_hint), isError = titleError != null)
            DateBoxes(form, onChange)
            val start = form.startDate
            val end = form.endDate
            if (start != null && end != null && end >= start) {
                // « 5 nuitées » under the dates, as on the prototype (règle 13).
                val nights = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt()
                Text(
                    if (nights == 0) stringResource(R.string.trip_form_no_night) else pluralStringResource(R.plurals.trip_form_nights, nights, nights),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            errorMessage(errors, TripFormError.StartMissing, TripFormError.EndMissing, TripFormError.EndBeforeStart)
                ?.let { FormHint(it, isError = true) }
        }

        TripTile(Icons.Outlined.Groups, stringResource(R.string.trip_field_participants), Modifier.part(TripPart.Who)) {
            // By household, a box per household ticks all of its members (règle de Kévin, 17/11).
            // The creator always takes part: unticking them, alone or with their household, does nothing.
            val locked = state.participants.filter { it.locked }.map { it.id }.toSet()
            HouseholdFaces(
                groups = state.participantGroups,
                selected = form.participantIds + locked,
                locked = locked,
                onChange = { picked -> onChange { it.withParticipantSet(picked + locked) } },
            )
            val whoError = errorMessage(errors, TripFormError.TooManyParticipants)
            FormHint(whoError ?: stringResource(R.string.trip_who_hint), isError = whoError != null)
        }

        TripTile(Icons.Outlined.Visibility, stringResource(R.string.trip_field_readers)) {
            Readers(state, onChange)
        }

        if (state.saveFailed) {
            Surface(shape = RoundedCornerShape(16.dp), color = LocalClayColors.current.card) {
                FormHint(stringResource(R.string.trip_save_failed), isError = true, modifier = Modifier.padding(14.dp))
            }
        }
        if (!state.isNew) DeleteTripButton(form.title, enabled = !state.isSaving, onDelete)
        Spacer(Modifier.height(8.dp))
    }
}

/** The image of the place (règle 12), with a white label that fills in while the name and the dates are typed. */
@Composable
private fun PreviewBanner(form: TripForm, cover: (@Composable (Modifier) -> Unit)?) {
    Box(Modifier.fillMaxWidth().height(180.dp)) {
        if (cover != null) cover(Modifier.fillMaxSize()) else MiniLandscape(form.landscape, Modifier.fillMaxSize())
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                form.title.trim().ifEmpty { stringResource(R.string.trip_preview_title) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TripPathColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val start = form.startDate
            val end = form.endDate
            if (start != null && end != null && end >= start) {
                val trip = Trip("", form.title, start, end, emptyList(), emptyList(), "", "")
                val (from, to) = tripRangeEnds(trip, weekday = true)
                val days = tripLength(trip)
                val range = if (start == end) to else stringResource(R.string.trip_preview_range, from, to)
                Text(
                    stringResource(R.string.trips_dates_and_length, range, pluralStringResource(R.plurals.trips_days, days, days)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TripPathColors.InkSoft,
                )
            }
        }
    }
}

/** « Départ » and « Retour », two boxes that open the calendar; the return cannot be before the departure. */
@Composable
private fun DateBoxes(form: TripForm, onChange: ((TripForm) -> TripForm) -> Unit) {
    val stacked = LocalDensity.current.fontScale >= 1.3f
    val start: @Composable (Modifier) -> Unit = { modifier ->
        TripDayBox(stringResource(R.string.trip_start_label), form.startDate, modifier = modifier, onPick = { day ->
            onChange { it.copy(startDate = day, endDate = it.endDate?.takeIf { end -> end >= day } ?: day) }
        })
    }
    val end: @Composable (Modifier) -> Unit = { modifier ->
        TripDayBox(stringResource(R.string.trip_end_label), form.endDate, modifier = modifier, initial = form.startDate, onPick = { day ->
            onChange { it.copy(endDate = day) }
        })
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            start(Modifier.fillMaxWidth())
            end(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            start(Modifier.weight(1f))
            end(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HouseholdFaces(groups: List<PickGroup>, selected: Set<String>, locked: Set<String>, onChange: (Set<String>) -> Unit) {
    val all = stringResource(R.string.trip_household_all)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        groups.forEach { group ->
            val ids = group.members.map { it.id }
            val free = ids.filter { it !in locked }
            val state = householdState(selected, ids)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .triStateToggleable(state = state, enabled = free.isNotEmpty(), role = Role.Checkbox) { onChange(toggleHousehold(selected, free)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TriStateCheckbox(state = state, onClick = null, enabled = free.isNotEmpty())
                Spacer(Modifier.width(12.dp))
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(all, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PeopleRow(
                choices = group.members.map { member ->
                    PersonChoice(member.id, member.firstName, selected = member.id in selected, memberId = member.id)
                },
                multiple = true,
                onToggle = { id -> if (id !in locked) onChange(toggleMember(selected, id)) },
            )
        }
    }
}

/**
 * « Qui suit le voyage ? », folded on one line (« Toute la famille, 12 personnes, dont les 4 qui
 * partent ») with « Changer », which opens the choice by household. Who leaves always follows.
 */
@Composable
private fun Readers(state: TripEditUiState.Editing, onChange: ((TripForm) -> TripForm) -> Unit) {
    val form = state.form
    var open by rememberSaveable { mutableStateOf(false) }
    val everyone = state.readerGroups.flatMap { group -> group.members.map { it.id } }.toSet()
    val following = (form.readerIds + form.participantIds).intersect(everyone)
    val leaving = form.participantIds.intersect(everyone).size
    val dont = pluralStringResource(R.plurals.trip_readers_leaving, leaving, leaving)
    val summary = if (following.size >= everyone.size) {
        stringResource(R.string.trip_readers_everyone, pluralStringResource(R.plurals.trip_people, everyone.size, everyone.size), dont)
    } else {
        stringResource(R.string.trip_readers_some, following.size, everyone.size, dont)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(summary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(if (open) R.string.trip_readers_fold else R.string.trip_readers_change), fontWeight = FontWeight.Bold)
        }
    }
    if (open) {
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
    FormHint(stringResource(R.string.trip_readers_hidden))
}

@Composable
private fun DeleteTripButton(title: String, enabled: Boolean, onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    TripDeleteButton(stringResource(R.string.trip_delete), enabled = enabled, onClick = { confirm = true })
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
private fun errorMessage(errors: Set<TripFormError>, vararg kinds: TripFormError): String? {
    val error = kinds.firstOrNull { it in errors } ?: return null
    return stringResource(
        when (error) {
            TripFormError.TitleMissing -> R.string.error_trip_title_missing
            TripFormError.TitleTooLong -> R.string.error_event_title_too_long
            TripFormError.StartMissing -> R.string.error_trip_start_missing
            TripFormError.EndMissing -> R.string.error_trip_end_missing
            TripFormError.EndBeforeStart -> R.string.error_trip_end_before_start
            TripFormError.TooManyParticipants -> R.string.error_trip_too_many
        },
    )
}

/** Fictional data for previews and screenshots. */
internal fun previewTripEditState(isNew: Boolean = true, errors: Set<TripFormError> = emptySet()): TripEditUiState.Editing {
    val people = listOf(
        MemberChoice("a", "Camille", "Foyer Martin", locked = true),
        MemberChoice("b", "Inès", "Foyer Bernard"),
        MemberChoice("c", "Noé", "Foyer Martin"),
        MemberChoice("d", "Sam", "Foyer Bernard"),
    )
    fun member(choice: MemberChoice, home: String) =
        Member(choice.id, choice.firstName, null, null, home, MemberProfile.Adult, isAdmin = false, parentIds = emptyList(), uid = null)
    val groups = listOf(
        PickGroup("Foyer Martin", listOf(member(people[0], "h1"), member(people[2], "h1"))),
        PickGroup("Foyer Bernard", listOf(member(people[1], "h2"), member(people[3], "h2"))),
    )
    return TripEditUiState.Editing(
        form = TripForm(
            title = "Corse",
            startDate = LocalDate.of(2027, 8, 2),
            endDate = LocalDate.of(2027, 8, 16),
            participantIds = setOf("a", "b"),
            readerIds = setOf("a", "b", "c"),
            landscape = TripLandscape.Sea,
        ),
        isNew = isNew,
        participants = people,
        readers = people.map { it.copy(locked = false) },
        participantGroups = groups,
        readerGroups = groups,
        errors = errors,
    )
}

@FamiliaPreviews
@Composable
private fun TripEditScreenPreview() {
    FamiliaTheme { Surface { TripEditScreen(previewTripEditState(isNew = false), {}, {}, {}, {}) } }
}
