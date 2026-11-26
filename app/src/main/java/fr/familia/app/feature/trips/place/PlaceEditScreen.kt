package fr.familia.app.feature.trips.place

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormLabel
import fr.familia.app.core.ui.form.TimeBox
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.trips.TripDeleteButton
import fr.familia.app.feature.trips.TripFormScaffold
import fr.familia.app.feature.trips.TripTile
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.map.PlaceLinkKind
import fr.familia.app.feature.trips.map.placeLinks
import java.time.LocalTime

/** The icon of each kind of place, as on the panel of a destination. */
fun placeKindIcon(kind: TripPlaceKind): ImageVector = when (kind) {
    TripPlaceKind.Lodging -> Icons.Outlined.Hotel
    TripPlaceKind.Restaurant -> Icons.Outlined.Restaurant
    TripPlaceKind.Spot -> Icons.Outlined.Place
}

/** « Hébergement », « Restaurant », « Spot ». */
@Composable
fun placeKindLabel(kind: TripPlaceKind): String = stringResource(
    when (kind) {
        TripPlaceKind.Lodging -> R.string.place_kind_lodging
        TripPlaceKind.Restaurant -> R.string.place_kind_restaurant
        TripPlaceKind.Spot -> R.string.place_kind_spot
    },
)

/**
 * The page of an accommodation, a restaurant or a spot (récit 103, règles 19, 21, 24, 26), in tiles
 * on the sky of the trips like the step form: its name, the hour of arrival, a note on several
 * lines, a cost, « Voir sur Tripadvisor », and « Supprimer » at the bottom. Leaving with changes
 * asks first (règle 24). Stateless: every change goes through [onChange].
 */
@Composable
fun PlaceEditScreen(
    state: PlaceEditUiState,
    onChange: ((PlaceForm) -> PlaceForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    /** Opens the search of its kind again (« Choisir un autre … », règle 19); null hides the button. */
    onSearch: (() -> Unit)? = null,
    /** In the sheet under the map of the trip (fiche F2 §4). */
    inSheet: Boolean = false,
) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val editing = state as? PlaceEditUiState.Editing
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
        // Fiche F2 §4: a new accommodation, spot or restaurant is « Nouvel élément », as a transport.
        title = editing?.let { if (it.isNew) stringResource(R.string.trip_element_new) else pageTitle(it) } ?: stringResource(R.string.place_page_title),
        help = ScreenHelp.TripStepEdit,
        onBack = { if (hasChanges) confirmLeave = true else onBack() },
        action = editing?.takeIf { it.canEdit }?.let { stringResource(if (it.isNew) R.string.trip_element_add else R.string.member_save) },
        busy = editing?.isSaving == true,
        onAction = onSave,
        inSheet = inSheet,
        subtitle = editing?.takeIf { it.isNew }?.let { listOf(placeKindLabel(it.kind), it.destination).filter { part -> part.isNotBlank() }.joinToString(" · ") },
    ) {
        when (state) {
            PlaceEditUiState.Loading, PlaceEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            PlaceEditUiState.Unavailable -> CompositionLocalProvider(LocalContentColor provides TripPathColors.Ink) {
                EmptyState(
                    icon = Icons.Outlined.CardTravel,
                    title = stringResource(R.string.place_unavailable_title),
                    body = stringResource(R.string.place_unavailable_body),
                )
            }
            is PlaceEditUiState.Editing -> FormContent(state, onChange, onDelete, onSearch)
        }
    }
}

/** « Ton restaurant » when it is new, « Modifier ton restaurant » for a participant, the name for a follower. */
@Composable
private fun pageTitle(state: PlaceEditUiState.Editing): String = when {
    !state.canEdit -> state.form.name.trim().ifEmpty { placeKindLabel(state.kind) }
    state.isNew -> stringResource(
        when (state.kind) {
            TripPlaceKind.Lodging -> R.string.place_new_lodging
            TripPlaceKind.Restaurant -> R.string.place_new_restaurant
            TripPlaceKind.Spot -> R.string.place_new_spot
        },
    )
    else -> stringResource(
        when (state.kind) {
            TripPlaceKind.Lodging -> R.string.place_edit_lodging
            TripPlaceKind.Restaurant -> R.string.place_edit_restaurant
            TripPlaceKind.Spot -> R.string.place_edit_spot
        },
    )
}

@Composable
private fun FormContent(
    state: PlaceEditUiState.Editing,
    onChange: ((PlaceForm) -> PlaceForm) -> Unit,
    onDelete: () -> Unit,
    onSearch: (() -> Unit)?,
) {
    val form = state.form
    val errors = state.errors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TripTile(placeKindIcon(state.kind), placeKindLabel(state.kind)) {
            if (state.canEdit) {
                val label = stringResource(R.string.place_field_name)
                FormLabel(label)
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { value -> onChange { it.copy(name = value) } },
                    isError = PlaceFormError.NameMissing in errors || PlaceFormError.NameTooLong in errors,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                )
                val nameError = errorMessage(errors, PlaceFormError.NameMissing, PlaceFormError.NameTooLong)
                FormHint(nameError ?: stringResource(R.string.place_name_hint), isError = nameError != null)
                form.pick?.takeIf { it.name == form.name.trim() }?.address?.let { FormHint(it) }
                if (onSearch != null) {
                    TextButton(onClick = onSearch, modifier = Modifier.heightIn(min = 56.dp)) {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                when (state.kind) {
                                    TripPlaceKind.Lodging -> R.string.place_choose_other_lodging
                                    TripPlaceKind.Restaurant -> R.string.place_choose_other_restaurant
                                    TripPlaceKind.Spot -> R.string.place_choose_other_spot
                                },
                            ),
                        )
                    }
                }
            } else {
                Text(form.name, style = MaterialTheme.typography.titleMedium)
                form.pick?.address?.let { FormHint(it) }
            }
            if (state.destination.isNotEmpty()) FormHint(stringResource(R.string.place_in_destination, state.destination))
        }

        // Récit 122: the day of a restaurant or a spot, the nights of an accommodation, in the stay.
        if (state.stayStart != null && state.stayEnd != null) {
            StayTile(state, onChange)
        }

        TripTile(Icons.Outlined.Schedule, stringResource(if (state.kind == TripPlaceKind.Spot) R.string.place_tile_time else R.string.place_tile_arrive)) {
            if (state.canEdit) {
                TimeBox(
                    time = form.arriveTime,
                    onPick = { time -> onChange { it.copy(arriveTime = time) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.trip_step_time),
                    initial = defaultHour(state.kind),
                )
                FormHint(stringResource(if (state.kind == TripPlaceKind.Lodging) R.string.place_time_hint_lodging else R.string.place_time_hint))
            } else {
                Text(form.arriveTime?.let(::hourText) ?: stringResource(R.string.place_no_time), style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (state.canEdit || form.note.isNotBlank()) {
            TripTile(Icons.Outlined.EditNote, stringResource(R.string.place_tile_note)) {
                if (state.canEdit) {
                    val label = stringResource(R.string.place_tile_note)
                    OutlinedTextField(
                        value = form.note,
                        onValueChange = { value -> onChange { it.copy(note = value) } },
                        isError = PlaceFormError.NoteTooLong in errors,
                        placeholder = {
                            Text(stringResource(if (state.kind == TripPlaceKind.Lodging) R.string.place_note_example_lodging else R.string.place_note_example))
                        },
                        // Several lines, said clearly, without a field too high (règle 26).
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                    )
                    val noteError = errorMessage(errors, PlaceFormError.NoteTooLong)
                    FormHint(
                        noteError ?: stringResource(R.string.place_note_hint, form.note.trim().length, MAX_PLACE_NOTE),
                        isError = noteError != null,
                    )
                } else {
                    Text(form.note.trim(), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (state.canEdit || form.cost.isNotBlank()) {
            val symbol = currencySymbol(state.currency)
            TripTile(Icons.Outlined.Payments, stringResource(R.string.place_tile_cost, symbol)) {
                if (state.canEdit) {
                    val label = stringResource(R.string.place_tile_cost, symbol)
                    OutlinedTextField(
                        value = form.cost,
                        onValueChange = { value -> onChange { it.copy(cost = value.take(12)) } },
                        isError = PlaceFormError.CostInvalid in errors,
                        singleLine = true,
                        placeholder = { Text("0") },
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                    )
                    val costError = errorMessage(errors, PlaceFormError.CostInvalid)
                    FormHint(costError ?: stringResource(R.string.place_cost_hint), isError = costError != null)
                } else {
                    Text("${form.cost} $symbol", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        TripadvisorLink(form.name, state.destination)

        if (state.saveFailed) {
            Surface(shape = RoundedCornerShape(16.dp), color = LocalClayColors.current.card) {
                FormHint(stringResource(R.string.trip_save_failed), isError = true, modifier = Modifier.padding(14.dp))
            }
        }
        if (state.canDelete) DeletePlaceButton(state.kind, form.name, enabled = !state.isSaving, onDelete)
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * « Jour » (récit 122): the day of a restaurant or a spot in the stay, « Pas encore de jour (à caser) »
 * when it has none; « Nuits » for an accommodation, the whole stay unless chosen. A button of 56 dp
 * opens the choice; a follower only reads it.
 */
@Composable
private fun StayTile(state: PlaceEditUiState.Editing, onChange: ((PlaceForm) -> PlaceForm) -> Unit) {
    val start = state.stayStart ?: return
    val end = state.stayEnd ?: return
    val form = state.form
    var open by rememberSaveable { mutableStateOf(false) }
    if (state.kind == TripPlaceKind.Lodging) {
        val nights = lodgingNights(form.toPlace("", "", TripPlaceKind.Lodging), start, end)
        val text = nightsLabel(nights.checkIn, nights.checkOut)
        TripTile(Icons.Outlined.CalendarMonth, stringResource(R.string.place_tile_nights)) {
            if (state.canEdit) {
                ChoiceButton(text, stringResource(R.string.place_nights_change)) { open = true }
                FormHint(stringResource(R.string.place_nights_hint))
            } else {
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (open) {
            NightsChoiceSheet(
                name = form.name.ifBlank { placeKindLabel(state.kind) },
                start = start,
                end = end,
                checkIn = nights.checkIn,
                checkOut = nights.checkOut,
                onChoose = { checkIn, checkOut -> onChange { it.copy(checkIn = checkIn, checkOut = checkOut) } },
                onDismiss = { open = false },
            )
        }
    } else {
        val days = stayDays(start, end)
        val day = form.day?.takeIf { it in days }
        val text = day?.let { stayDayLabel(it, days.indexOf(it) + 1) } ?: stringResource(R.string.place_day_none)
        TripTile(Icons.Outlined.CalendarMonth, stringResource(R.string.place_tile_day)) {
            if (state.canEdit) {
                ChoiceButton(text, stringResource(R.string.place_day_change)) { open = true }
                FormHint(stringResource(R.string.place_day_hint))
            } else {
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (open) {
            DayChoiceSheet(
                name = form.name.ifBlank { placeKindLabel(state.kind) },
                destination = state.destination,
                days = days,
                chosen = day,
                onChoose = { chosen -> onChange { it.copy(day = chosen) }; open = false },
                onDismiss = { open = false },
            )
        }
    }
}

/** A white line of 56 dp that says the choice and opens it, read as « Changer le jour, lun. 12 avril ». */
@Composable
private fun ChoiceButton(text: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClickLabel = action, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(action, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = TripPathColors.Ink)
    }
}

/** « Voir sur Tripadvisor » (demande du 23/11): a search with the name and the town, our icon, never their logo. */
@Composable
private fun TripadvisorLink(name: String, destination: String) {
    val query = listOf(name.trim(), destination).filter { it.isNotEmpty() }.joinToString(" ")
    val link = placeLinks(query)?.firstOrNull { it.kind == PlaceLinkKind.Tripadvisor } ?: return
    val uri = LocalUriHandler.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button) { runCatching { uri.openUri(link.url) } }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.place_tripadvisor), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeletePlaceButton(kind: TripPlaceKind, name: String, enabled: Boolean, onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val text = stringResource(
        when (kind) {
            TripPlaceKind.Lodging -> R.string.place_delete_lodging
            TripPlaceKind.Restaurant -> R.string.place_delete_restaurant
            TripPlaceKind.Spot -> R.string.place_delete_spot
        },
    )
    TripDeleteButton(text, enabled = enabled, onClick = { confirm = true })
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.place_delete_title, name.trim())) },
            text = { Text(stringResource(R.string.place_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete() }) {
                    Text(stringResource(R.string.event_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_keep)) } },
        )
    }
}

/** The hour the clock opens on: arrival at 16 h for a bed, 20 h for a restaurant, 10 h for a visit. */
internal fun defaultHour(kind: TripPlaceKind): LocalTime = when (kind) {
    TripPlaceKind.Lodging -> LocalTime.of(16, 0)
    TripPlaceKind.Restaurant -> LocalTime.of(20, 0)
    TripPlaceKind.Spot -> LocalTime.of(10, 0)
}

/** « 20 h 30 », « 9 h ». */
internal fun hourText(time: LocalTime): String = if (time.minute == 0) "${time.hour} h" else "${time.hour} h ${"%02d".format(java.util.Locale.ROOT, time.minute)}"

/** « € » for the euro or no money, the French symbol of [code] else, the code itself when unknown. */
internal fun currencySymbol(code: String?): String {
    if (code == null || code == "EUR") return "€"
    return runCatching { java.util.Currency.getInstance(code).getSymbol(java.util.Locale.FRANCE) }.getOrDefault(code)
}

@Composable
private fun errorMessage(errors: Set<PlaceFormError>, vararg kinds: PlaceFormError): String? {
    val error = kinds.firstOrNull { it in errors } ?: return null
    return stringResource(
        when (error) {
            PlaceFormError.NameMissing -> R.string.error_place_name_missing
            PlaceFormError.NameTooLong -> R.string.error_event_place_too_long
            PlaceFormError.NoteTooLong -> R.string.error_place_note_too_long
            PlaceFormError.CostInvalid -> R.string.error_place_cost
        },
    )
}

/** Fictional data for previews, screenshots and the accessibility tests. */
internal fun previewPlaceEditState(
    kind: TripPlaceKind = TripPlaceKind.Restaurant,
    isNew: Boolean = false,
    canEdit: Boolean = true,
    errors: Set<PlaceFormError> = emptySet(),
    day: java.time.LocalDate? = java.time.LocalDate.of(2027, 4, 12),
): PlaceEditUiState.Editing = PlaceEditUiState.Editing(
    kind = kind,
    form = PlaceForm(
        name = if (PlaceFormError.NameMissing in errors) "" else "Le Bistrot du Port",
        arriveTime = LocalTime.of(20, 0),
        note = "Table réservée au nom de Camille.\nGoûter le fiadone.",
        cost = if (PlaceFormError.CostInvalid in errors) "beaucoup" else "80",
        day = day,
    ),
    isNew = isNew,
    canEdit = canEdit,
    canDelete = canEdit && !isNew,
    errors = errors,
    destination = "Ajaccio",
    stayStart = java.time.LocalDate.of(2027, 4, 11),
    stayEnd = java.time.LocalDate.of(2027, 4, 16),
)

@FamiliaPreviews
@Composable
private fun PlaceEditScreenPreview() {
    FamiliaTheme { Surface { PlaceEditScreen(previewPlaceEditState(), {}, {}, {}, {}) } }
}
