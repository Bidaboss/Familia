package fr.familia.app.feature.trips.step

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.form.CategoryTiles
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormLabel
import fr.familia.app.core.ui.form.TimeBox
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.trips.TripDayBox
import fr.familia.app.feature.trips.TripDeleteButton
import fr.familia.app.feature.trips.TripFormColors
import fr.familia.app.feature.trips.TripFormScaffold
import fr.familia.app.feature.trips.TripTile
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.list.tripRangeEnds
import fr.familia.app.feature.trips.stepTypeIcon
import fr.familia.app.feature.trips.stepTypeLabel
import java.time.LocalDate
import java.time.LocalTime

/** The parts of the form, in their order on the page, to go up to the first one to fix. */
private enum class StepPart { Where, When, Note }

private fun StepFormError.part(): StepPart = when (this) {
    StepFormError.PlaceMissing, StepFormError.PlaceTooLong -> StepPart.Where
    StepFormError.DepartMissing, StepFormError.ArriveBeforeDepart -> StepPart.When
    StepFormError.NoteTooLong -> StepPart.Note
}

/**
 * Step form (create, edit, delete), in tiles on the sky of the trips (DESIGN-57). Stateless:
 * every change goes through [onChange].
 */
@Composable
fun StepEditScreen(
    state: StepEditUiState,
    onChange: ((StepForm) -> StepForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    /** The form sits in the sheet under the map of the trip (règle 4). */
    inSheet: Boolean = false,
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
    val isNew = editing?.isNew != false
    TripFormScaffold(
        // Fiche F2 §4: a new transport is « Nouvel élément », its title over the map.
        title = stringResource(if (isNew) R.string.trip_element_new else R.string.trip_step_edit_title),
        help = ScreenHelp.TripStepEdit,
        onBack = { if (hasChanges) confirmLeave = true else onBack() },
        action = editing?.let { stringResource(if (it.isNew) R.string.trip_element_add else R.string.member_save) },
        busy = editing?.isSaving == true,
        onAction = onSave,
        inSheet = inSheet,
        subtitle = editing?.takeIf { it.isNew }?.let { listOfNotNull(stepTypeLabel(it.form.type), it.trip?.title).joinToString(" · ") },
    ) {
        when (state) {
            StepEditUiState.Loading, StepEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            StepEditUiState.Unavailable -> CompositionLocalProvider(LocalContentColor provides TripPathColors.Ink) {
                EmptyState(
                    icon = Icons.Outlined.CardTravel,
                    title = stringResource(R.string.trip_step_unavailable_title),
                    body = stringResource(R.string.trip_step_unavailable_body),
                )
            }
            is StepEditUiState.Editing -> FormContent(state, onChange, onDelete)
        }
    }
}

@Composable
private fun FormContent(
    state: StepEditUiState.Editing,
    onChange: ((StepForm) -> StepForm) -> Unit,
    onDelete: () -> Unit,
) {
    val form = state.form
    val errors = state.errors
    val scroll = rememberScrollState()
    val tops = remember { mutableStateMapOf<StepPart, Int>() }
    // After a refused « Ajouter l'étape », the page goes up to the first question to fix.
    LaunchedEffect(errors) {
        val first = errors.map { it.part() }.minOrNull() ?: return@LaunchedEffect
        tops[first]?.let { scroll.animateScrollTo(it) }
    }
    fun Modifier.part(part: StepPart) = onGloballyPositioned { tops[part] = it.positionInParent().y.toInt() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.trip?.let { TripReminder(it) }

        // DESIGN-99 A (E-17): the « + » of « Le trajet » already said what is added; only a transport chooses its way.
        if (state.typeOptions.size > 1) TripTile(Icons.Outlined.Category, stringResource(R.string.trip_step_tile_type)) {
            CategoryTiles(
                options = state.typeOptions,
                selected = form.type,
                onSelect = { type -> onChange { it.copy(type = type) } },
                label = { stepTypeLabel(it) },
                icon = ::stepTypeIcon,
                selectedColor = TripFormColors.IconBackground,
                selectedContentColor = TripPathColors.Ink,
            )
        }

        TripTile(Icons.Outlined.Place, stringResource(R.string.trip_step_tile_where), Modifier.part(StepPart.Where)) {
            val label = stringResource(R.string.trip_step_field_place)
            FormLabel(label)
            OutlinedTextField(
                value = form.place,
                onValueChange = { value -> onChange { it.copy(place = value) } },
                isError = StepFormError.PlaceMissing in errors || StepFormError.PlaceTooLong in errors,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
            val placeError = errorMessage(errors, StepFormError.PlaceMissing, StepFormError.PlaceTooLong)
            val hint = if (form.type == StepType.Lodging) R.string.trip_step_place_hint_lodging else R.string.trip_step_place_hint
            FormHint(placeError ?: stringResource(hint), isError = placeError != null)
        }

        TripTile(Icons.Outlined.Schedule, stringResource(R.string.trip_step_tile_when), Modifier.part(StepPart.When)) {
            FormLabel(stringResource(R.string.trip_step_field_depart))
            DayAndTime(
                day = form.departDate,
                onDay = { day -> onChange { it.copy(departDate = day, arriveDate = it.arriveDate?.takeIf { a -> a >= day }) } },
                time = form.departTime,
                onTime = { time -> onChange { it.copy(departTime = time) } },
                clearDescription = stringResource(R.string.trip_step_clear_depart_time),
            )
            errorMessage(errors, StepFormError.DepartMissing)?.let { FormHint(it, isError = true) }
            FormLabel(stringResource(R.string.trip_step_field_arrive))
            DayAndTime(
                day = form.arriveDate ?: form.departDate,
                onDay = { day -> onChange { it.copy(arriveDate = day) } },
                time = form.arriveTime,
                onTime = { time -> onChange { it.copy(arriveTime = time) } },
                initial = form.departDate,
                clearDescription = stringResource(R.string.trip_step_clear_arrive_time),
            )
            errorMessage(errors, StepFormError.ArriveBeforeDepart)?.let { FormHint(it, isError = true) }
            ReminderBand()
        }

        TripTile(Icons.AutoMirrored.Outlined.Notes, stringResource(R.string.trip_step_tile_note), Modifier.part(StepPart.Note)) {
            val label = stringResource(R.string.event_note)
            OutlinedTextField(
                value = form.note,
                onValueChange = { value -> onChange { it.copy(note = value) } },
                isError = StepFormError.NoteTooLong in errors,
                // Several lines, said by the hint, but never a field taller than 6 lines: it scrolls (règle 26).
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
            val noteError = errorMessage(errors, StepFormError.NoteTooLong)
            FormHint(noteError ?: stringResource(R.string.trip_step_note_hint), isError = noteError != null)
        }

        if (state.saveFailed) {
            Surface(shape = RoundedCornerShape(16.dp), color = LocalClayColors.current.card) {
                FormHint(stringResource(R.string.trip_save_failed), isError = true, modifier = Modifier.padding(14.dp))
            }
        }
        if (state.canDelete) DeleteStepButton(form.place, enabled = !state.isSaving, onDelete)
        Spacer(Modifier.height(8.dp))
    }
}

/** « Dans le voyage · La Palmyre · 11 → 16 avril », a small white card, not a button. */
@Composable
private fun TripReminder(trip: Trip) {
    val (from, to) = tripRangeEnds(trip, weekday = false)
    val dates = if (trip.startDate == trip.endDate) to else stringResource(R.string.trip_preview_range, from, to)
    // Fiche F2 §4: a white band 40 dp high, rounded 26, grey bold text.
    Text(
        stringResource(R.string.trip_step_in_trip, trip.title, dates),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
        fontWeight = FontWeight.Bold,
        color = Color(0xFF5B6770),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/**
 * A day box and the hour (TimeBox) side by side, one under the other with a big font. No big
 * « Sans heure » chip under each pair (E-18): « Choisir » already means no hour, and a small
 * « Sans heure » link clears an hour once one is set.
 */
@Composable
private fun DayAndTime(
    day: LocalDate?,
    onDay: (LocalDate) -> Unit,
    time: LocalTime?,
    onTime: (LocalTime?) -> Unit,
    initial: LocalDate? = null,
    /** What TalkBack says on the « Sans heure » link: which hour it clears (C1 of #590). */
    clearDescription: String? = null,
) {
    val dayLabel = stringResource(R.string.trip_step_day)
    val timeLabel = stringResource(R.string.trip_step_time)
    Column {
        if (LocalDensity.current.fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TripDayBox(dayLabel, day, onDay, Modifier.fillMaxWidth(), initial = initial)
                TimeBox(time = time, onPick = onTime, modifier = Modifier.fillMaxWidth(), label = timeLabel, clearLabel = null)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TripDayBox(dayLabel, day, onDay, Modifier.weight(1f), initial = initial)
                TimeBox(time = time, onPick = onTime, modifier = Modifier.weight(1f), label = timeLabel, clearLabel = null)
            }
        }
        if (time != null) {
            TextButton(
                onClick = { onTime(null) },
                modifier = Modifier.align(Alignment.End).heightIn(min = 56.dp)
                    .then(if (clearDescription != null) Modifier.semantics { contentDescription = clearDescription } else Modifier),
            ) { Text(stringResource(R.string.event_no_time)) }
        }
    }
}

/** The reminder of « Bien arrivé », in a pale blue band with a clock. */
@Composable
private fun ReminderBand() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TripFormColors.IconBackground)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = TripFormColors.Icon, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.trip_step_field_arrive_help), style = MaterialTheme.typography.bodyMedium, color = TripFormColors.Icon)
    }
}

@Composable
private fun DeleteStepButton(place: String, enabled: Boolean, onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    TripDeleteButton(stringResource(R.string.trip_step_delete), enabled = enabled, onClick = { confirm = true })
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
private fun errorMessage(errors: Set<StepFormError>, vararg kinds: StepFormError): String? {
    val error = kinds.firstOrNull { it in errors } ?: return null
    return stringResource(
        when (error) {
            StepFormError.PlaceMissing -> R.string.error_step_place_missing
            StepFormError.PlaceTooLong -> R.string.error_event_place_too_long
            StepFormError.DepartMissing -> R.string.error_event_date_missing
            StepFormError.ArriveBeforeDepart -> R.string.error_step_arrive_before_depart
            StepFormError.NoteTooLong -> R.string.error_event_note_too_long
        },
    )
}

/** Fictional data for previews and screenshots. */
internal fun previewStepEditState(isNew: Boolean = false, errors: Set<StepFormError> = emptySet()): StepEditUiState.Editing = StepEditUiState.Editing(
    form = StepForm(
        type = StepType.Flight,
        place = if (errors.isEmpty()) "Ajaccio" else "",
        departDate = LocalDate.of(2027, 8, 2),
        departTime = LocalTime.of(8, 15),
        arriveDate = LocalDate.of(2027, 8, 2),
        arriveTime = LocalTime.of(9, 40),
        note = "Vol AF 7520",
    ),
    isNew = isNew,
    canDelete = !isNew,
    errors = errors,
    trip = Trip("t1", "Corse", LocalDate.of(2027, 8, 2), LocalDate.of(2027, 8, 16), emptyList(), emptyList(), "a", "Camille"),
)

@FamiliaPreviews
@Composable
private fun StepEditScreenPreview() {
    FamiliaTheme { Surface { StepEditScreen(previewStepEditState(), {}, {}, {}, {}) } }
}
