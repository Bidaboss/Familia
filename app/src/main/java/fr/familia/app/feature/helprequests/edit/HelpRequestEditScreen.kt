package fr.familia.app.feature.helprequests.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.mention.MentionTextField
import fr.familia.app.core.model.HelpKind
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.photo.PhotoField
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.PickGroup
import fr.familia.app.core.ui.householdState
import fr.familia.app.core.ui.toggleHousehold
import fr.familia.app.core.ui.toggleMember
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.feed.FeedTone
import fr.familia.app.feature.helprequests.kindLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * « Demander un coup de main » (récits 13 et 14), redone as one page of tiles (récit 76, DESIGN-48
 * A): what is needed, the request, when, where, for whom, who to warn, and a dark button fixed at
 * the bottom that counts the people warned. Only the look changes: the form, the ViewModel and the
 * errors are the same. Stateless: every change goes through the callbacks.
 */
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
    val editing = state as? HelpRequestEditUiState.Editing
    val hasChanges = editing?.hasChanges == true
    BackHandler(enabled = hasChanges) { confirmLeave = true }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.helprequest_leave_title)) },
            text = { Text(stringResource(R.string.event_leave_body)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.member_leave_keep))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.member_leave_discard))
                }
            },
        )
    }
    Column(Modifier.fillMaxSize().themeBackground()) {
        TopBar(
            title = stringResource(if (editing?.isNew == false) R.string.helprequest_edit_title else R.string.helprequest_add_title),
            onBack = { if (hasChanges) confirmLeave = true else onBack() },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                HelpRequestEditUiState.Loading, HelpRequestEditUiState.Done ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                HelpRequestEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.VolunteerActivism,
                    title = stringResource(R.string.helprequest_unavailable_title),
                    body = stringResource(R.string.helprequest_unavailable_body),
                )
                is HelpRequestEditUiState.Editing -> FormContent(state, onChange, onPick, onShortcut, photoOn)
            }
        }
        if (editing != null) SendBar(editing, onSave)
    }
}

/** A white round « Retour » and the title, on the background of the theme. */
@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Surface(shape = CircleShape, color = LocalClayColors.current.card, shadowElevation = 2.dp) {
            BackButton(onClick = onBack)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp, lineHeight = 28.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** The dark button fixed at the bottom: « Envoyer à 5 personnes », or « Enregistrer ». */
@Composable
private fun SendBar(state: HelpRequestEditUiState.Editing, onSave: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.saveFailed) {
            Text(stringResource(R.string.event_save_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        val count = state.warnedIds.size
        Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            if (state.isSaving) {
                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(
                    if (state.isNew) pluralStringResource(R.plurals.helprequest_send_to, count, count) else stringResource(R.string.member_save),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** The tiles, in the order of the maquette; the screen scrolls to the first one in error. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FormContent(
    state: HelpRequestEditUiState.Editing,
    onChange: ((HelpRequestForm) -> HelpRequestForm) -> Unit,
    onPick: (Set<String>) -> Unit,
    onShortcut: (NotifyShortcut) -> Unit,
    photoOn: Boolean,
) {
    val form = state.form
    val errors = state.errors
    val request = remember { BringIntoViewRequester() }
    val time = remember { BringIntoViewRequester() }
    val place = remember { BringIntoViewRequester() }
    val notify = remember { BringIntoViewRequester() }
    LaunchedEffect(errors) {
        when (errors.map(::tileOf).minOrNull()) {
            ErrorTile.Request -> request.bringIntoView()
            ErrorTile.When -> time.bringIntoView()
            ErrorTile.Where -> place.bringIntoView()
            ErrorTile.Notify -> notify.bringIntoView()
            null -> Unit
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Tile(Icons.Outlined.PanTool, stringResource(R.string.helprequest_need)) {
            KindTiles(form.kind, enabled = !state.isSaving) { kind -> onChange { it.copy(kind = kind) } }
        }
        Tile(Icons.Outlined.Description, stringResource(R.string.helprequest_tile_request), Modifier.bringIntoViewRequester(request)) {
            RequestFields(state, onChange, photoOn)
        }
        Tile(Icons.Outlined.CalendarMonth, stringResource(R.string.helprequest_when), Modifier.bringIntoViewRequester(time)) {
            WhenFields(form, errors, onChange)
        }
        // « Où » with « Chez un foyer » comes next (récit 76): the place stays a text until then.
        Tile(Icons.Outlined.Place, stringResource(R.string.helprequest_where), Modifier.bringIntoViewRequester(place)) {
            OutlinedTextField(
                value = form.place,
                onValueChange = { value -> onChange { it.copy(place = value) } },
                label = { Text(stringResource(R.string.event_place)) },
                isError = HelpFormError.PlaceTooLong in errors,
                supportingText = errorText(errors, HelpFormError.PlaceTooLong),
                singleLine = true,
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.targets.size > 1) {
            Tile(Icons.Outlined.Person, stringResource(R.string.helprequest_for_whom)) {
                ForWhom(state.targets, form.forMemberId) { id -> onChange { it.copy(forMemberId = id) } }
            }
        }
        Tile(Icons.Outlined.Notifications, stringResource(R.string.helprequest_notify), Modifier.bringIntoViewRequester(notify)) {
            NotifyFields(state, onPick, onShortcut)
        }
        Spacer(Modifier.heightIn(min = 8.dp))
    }
}

private enum class ErrorTile { Request, When, Where, Notify }

private fun tileOf(error: HelpFormError): ErrorTile = when (error) {
    HelpFormError.TitleMissing, HelpFormError.TitleTooLong, HelpFormError.NoteTooLong -> ErrorTile.Request
    HelpFormError.DateMissing, HelpFormError.DatePast, HelpFormError.EndBeforeStart -> ErrorTile.When
    HelpFormError.PlaceTooLong -> ErrorTile.Where
    HelpFormError.NobodyWarned -> ErrorTile.Notify
}

/**
 * A white tile with the lilac edge of « s'organiser » (like the Coup de main card of the fil), its
 * icon in a pale lilac square and its title, a heading for TalkBack.
 */
@Composable
private fun Tile(icon: ImageVector, title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val tone = FeedTone.Lilac
    Surface(
        shape = shape,
        color = LocalClayColors.current.card,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .drawBehind { drawRect(tone.bar, topLeft = Offset.Zero, size = Size(5.dp.toPx(), size.height)) }
                .padding(start = 21.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.semantics(mergeDescendants = true) { heading() },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tone.labelBackground),
                ) {
                    Icon(icon, contentDescription = null, tint = tone.label, modifier = Modifier.size(20.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 24.sp))
            }
            content()
        }
    }
}

/** Garde, Trajet, Autre: three big tiles, a radio group. At 130 % they go on two lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindTiles(selected: HelpKind, enabled: Boolean, onSelect: (HelpKind) -> Unit) {
    val perRow = if (LocalDensity.current.fontScale >= 1.3f) 2 else 3
    FlowRow(
        maxItemsInEachRow = perRow,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().selectableGroup(),
    ) {
        HelpKind.entries.forEach { kind ->
            val on = kind == selected
            val shape = RoundedCornerShape(18.dp)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 84.dp)
                    .clip(shape)
                    .background(if (on) FeedTone.Lilac.labelBackground else LocalClayColors.current.card)
                    .border(if (on) 2.5.dp else 1.5.dp, if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, shape)
                    .selectable(selected = on, enabled = enabled, role = Role.RadioButton) { onSelect(kind) },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp),
                ) {
                    Icon(kindIcon(kind), contentDescription = null, tint = FeedTone.Lilac.label)
                    Text(kindLabel(kind), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
                if (on) CheckBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
    }
}

private fun kindIcon(kind: HelpKind): ImageVector = when (kind) {
    HelpKind.Childcare -> Icons.Outlined.ChildCare
    HelpKind.Ride -> Icons.Outlined.DirectionsCar
    HelpKind.Other -> Icons.Outlined.PanTool
}

/** The small dark round with a check, on what is chosen. */
@Composable
private fun CheckBadge(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface),
    ) {
        Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(14.dp))
    }
}

/** « Ta question », « Précisions » with « @ », and the photo. */
@Composable
private fun RequestFields(state: HelpRequestEditUiState.Editing, onChange: ((HelpRequestForm) -> HelpRequestForm) -> Unit, photoOn: Boolean) {
    val form = state.form
    val errors = state.errors
    OutlinedTextField(
        value = form.title,
        onValueChange = { value -> onChange { it.copy(title = value) } },
        label = { Text(stringResource(R.string.helprequest_question)) },
        placeholder = { Text(stringResource(R.string.helprequest_question_placeholder)) },
        isError = HelpFormError.TitleMissing in errors || HelpFormError.TitleTooLong in errors,
        supportingText = errorText(errors, HelpFormError.TitleMissing, HelpFormError.TitleTooLong)
            ?: { Text(stringResource(R.string.helprequest_question_hint)) },
        enabled = !state.isSaving,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    // « @Prénom » names one of those warned (Kévin, 19/11).
    val warned = state.warnedIds
    MentionTextField(
        text = form.note,
        mentionIds = form.mentionIds,
        onChange = { text, ids -> onChange { it.copy(note = text, mentionIds = ids) } },
        groups = state.groups.map { g -> g.copy(members = g.members.filter { it.id in warned }) }.filter { it.members.isNotEmpty() },
        label = { Text(stringResource(R.string.helprequest_details)) },
        isError = HelpFormError.NoteTooLong in errors,
        supportingText = errorText(errors, HelpFormError.NoteTooLong) ?: { Text(stringResource(R.string.helprequest_details_hint)) },
        enabled = !state.isSaving,
        minLines = 3,
    )
    // A photo to show what it is about: a QR code, a parcel, a place (Kévin 16/11).
    if (photoOn) {
        Text(stringResource(R.string.helprequest_photo_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PhotoField(
            stored = state.storedPhoto,
            edit = form.photo,
            onEdit = { edit -> onChange { it.copy(photo = edit) } },
            enabled = !state.isSaving,
        )
    }
}

/** The days of the chips: today, tomorrow and the two days after. */
internal fun quickDays(today: LocalDate): List<LocalDate> = (0L..3L).map { today.plusDays(it) }

/** Aujourd'hui, Demain, « Ven. 20 », « Sam. 21 », then « Autre jour »; « De » and « À » for the hours. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WhenFields(form: HelpRequestForm, errors: Set<HelpFormError>, onChange: ((HelpRequestForm) -> HelpRequestForm) -> Unit) {
    var datePicker by rememberSaveable { mutableStateOf(false) }
    // "start" or "end" while a time picker is open.
    var timePicker by rememberSaveable { mutableStateOf<String?>(null) }
    val today = familyToday()
    val days = quickDays(today)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
        days.forEachIndexed { index, day ->
            val label = when (index) {
                0 -> stringResource(R.string.helprequest_today)
                1 -> stringResource(R.string.helprequest_tomorrow)
                else -> day.format(shortDay).replaceFirstChar { it.uppercase() }
            }
            DayChip(label, selected = form.date == day, description = day.format(dateFormatter)) { onChange { it.copy(date = day) } }
        }
        val other = form.date?.takeIf { it !in days }
        DayChip(
            other?.format(dateFormatter)?.replaceFirstChar { it.uppercase() } ?: stringResource(R.string.helprequest_other_day),
            selected = other != null,
            icon = Icons.Outlined.CalendarMonth,
        ) { datePicker = true }
    }
    errorText(errors, HelpFormError.DateMissing, HelpFormError.DatePast)?.invoke()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        TimeBox(R.string.helprequest_from, form.startTime, Modifier.weight(1f), onPick = { timePicker = "start" }) { onChange { it.copy(startTime = null) } }
        TimeBox(R.string.helprequest_to, form.endTime, Modifier.weight(1f), onPick = { timePicker = "end" }) { onChange { it.copy(endTime = null) } }
    }
    errorText(errors, HelpFormError.EndBeforeStart)?.invoke()
    Text(stringResource(R.string.helprequest_times_optional), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (datePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = (form.date ?: today).toUtcMillis())
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

/** A day to choose: dark when chosen. 48 dp high, inside the tile. */
@Composable
private fun DayChip(label: String, selected: Boolean, description: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val ink = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.onSurface else LocalClayColors.current.card)
            .border(1.5.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = "$label, $description" } else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = ink)
    }
}

/** « De 14 h 00 »: a box that opens the time picker, with a cross to take the hour away. */
@Composable
private fun TimeBox(labelRes: Int, time: LocalTime?, modifier: Modifier, onPick: () -> Unit, onClear: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val label = stringResource(labelRes)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onPick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                time?.let(::formatTime) ?: stringResource(R.string.helprequest_time_empty),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp),
                color = if (time == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (time != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.helprequest_time_clear, label))
            }
        }
    }
}

/** Me, then my children: a round with the photo, the chosen one sharp and checked, the others pale. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForWhom(targets: List<Member>, selectedId: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
        targets.forEachIndexed { index, member ->
            val on = member.id == selectedId
            Face(
                member = member,
                label = if (index == 0) stringResource(R.string.event_for_me) else member.firstName,
                checked = on,
                pale = !on,
                modifier = Modifier.selectable(selected = on, role = Role.RadioButton) { onSelect(member.id) },
            )
        }
    }
}

/** A photo round with its name under it, 72 dp wide at least. */
@Composable
private fun Face(member: Member, label: String, checked: Boolean, pale: Boolean, modifier: Modifier = Modifier, locked: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.widthIn(min = 72.dp).heightIn(min = 56.dp).padding(4.dp),
    ) {
        Box {
            MemberAvatar(member, Modifier.alpha(if (pale) 0.45f else 1f), size = 48.dp)
            when {
                locked -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface),
                ) { Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(13.dp)) }
                checked -> CheckBadge(Modifier.align(Alignment.BottomEnd))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (pale) FontWeight.Normal else FontWeight.SemiBold,
            color = if (pale) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * « Seules ces personnes verront ta demande », « Toute la famille », then one block per household:
 * a box for the whole household (on, off or half) and the faces of its adults. Someone always
 * warned carries a lock and « · toujours ».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotifyFields(state: HelpRequestEditUiState.Editing, onPick: (Set<String>) -> Unit, onShortcut: (NotifyShortcut) -> Unit) {
    val warned = state.warnedIds
    val picked = state.form.notifyIds
    val locked = state.lockedIds
    Text(stringResource(R.string.helprequest_notify_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.shortcuts.forEach { shortcut ->
            val on = warned.containsAll(shortcut.memberIds)
            val shape = RoundedCornerShape(16.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(if (on) FeedTone.Lilac.labelBackground else LocalClayColors.current.card)
                    .border(1.5.dp, if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, shape)
                    .toggleable(value = on, enabled = !state.isSaving, role = Role.Checkbox) { onShortcut(shortcut) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(shortcut.label ?: stringResource(R.string.helprequest_notify_everyone), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    val always = stringResource(R.string.helprequest_always_short)
    val others = stringResource(R.string.helprequest_notify_others)
    state.groups.forEach { group ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val ids = group.members.map { it.id }
        val free = ids.filter { it !in locked }
        val household = householdState(picked + locked, ids)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .triStateToggleable(state = household, enabled = !state.isSaving && free.isNotEmpty(), role = Role.Checkbox) {
                    onPick(toggleHousehold(picked, free))
                },
        ) {
            TriStateCheckbox(state = household, onClick = null, enabled = !state.isSaving && free.isNotEmpty())
            Spacer(Modifier.width(8.dp))
            Text(group.name.ifBlank { others }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 12.dp)) {
            group.members.forEach { member ->
                val isLocked = member.id in locked
                val on = isLocked || member.id in picked
                val description = stringResource(R.string.helprequest_always_description, member.firstName)
                Face(
                    member = member,
                    label = if (isLocked) "${member.firstName} · $always" else member.firstName,
                    checked = on,
                    pale = !on,
                    locked = isLocked,
                    modifier = Modifier
                        .toggleable(value = on, enabled = !state.isSaving && !isLocked, role = Role.Checkbox) { onPick(toggleMember(picked, member.id)) }
                        .then(if (isLocked) Modifier.semantics { contentDescription = description } else Modifier),
                )
            }
        }
    }
    errorText(state.errors, HelpFormError.NobodyWarned)?.invoke()
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
private val shortDay = DateTimeFormatter.ofPattern("EEE d", Locale.FRANCE)

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
            title = "Qui peut garder Noé mercredi après-midi ?",
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
    FamiliaTheme { HelpRequestEditScreen(previewHelpRequestEdit(), {}, {}, {}, {}, {}) }
}
