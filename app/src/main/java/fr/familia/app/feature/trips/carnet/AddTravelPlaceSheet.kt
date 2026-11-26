package fr.familia.app.feature.trips.carnet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.customActions
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import fr.familia.app.core.model.TravelPlace
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.TravelPlaceProblem
import fr.familia.app.core.model.travelPlaceProblem
import fr.familia.app.core.place.Place
import fr.familia.app.feature.trips.TripDateButton
import fr.familia.app.feature.trips.edit.DestinationSearch
import java.time.LocalDate
import kotlinx.coroutines.launch

/** One place of « Ajouter un voyage »: what is typed, the place chosen, its days. */
private class StopDraft(query: String = "", place: Place? = null, days: Int = DEFAULT_DAYS) {
    var query by mutableStateOf(query)
    var place by mutableStateOf(place)
    var days by mutableStateOf(days)

    /** The name the carnet shows for the place, which can be corrected (« Samoure 281 00 »). */
    var label by mutableStateOf(place?.let(::placeLabel).orEmpty())
    var search by mutableStateOf<DestinationSearch>(DestinationSearch.Idle)

    /** The height of its card on screen, to know when a drag has passed over the next one. */
    var height = 0
}

private const val DEFAULT_DAYS = 3

private fun placeLabel(place: Place): String =
    fr.familia.app.feature.trips.edit.destinationName(place).ifBlank { place.title.orEmpty() }

/** A place of a trip already in the carnet, back into the sheet. */
private fun TravelPlace.asPlace() = Place(lat, lng, name = name, countryCode = countryCode)

/**
 * « Ajouter un voyage » (règle 38, retour de l'admin du 26/11, maquette « carnet-voyage-plusieurs-lieux »):
 * a trip already done, with an optional name, its first day (past) and one or several places, each
 * chosen among the answers of the phone's geocoder (a city, a region or a country, règle 17) with its
 * number of days; their dates follow each other by themselves. It counts as one trip. [suggest] asks
 * the geocoder; [failed] says the last save did not go through.
 *
 * « Modifier le voyage » (demande de l'admin du 26/11, spec « modifier-voyage-ajoute »): the same sheet
 * filled with [editing]; « Enregistrer » replaces it, « Retirer ce voyage » ([onRemoveTrip]) after a question.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTravelPlaceSheet(
    today: LocalDate,
    saving: Boolean,
    failed: Boolean,
    suggest: suspend (String) -> List<Place>,
    onSave: (name: String?, start: LocalDate, stops: List<Pair<Place, Int>>) -> Unit,
    onDismiss: () -> Unit,
    editing: ManualTrip? = null,
    onRemoveTrip: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(editing?.start) }
    var problem by rememberSaveable { mutableStateOf<TravelPlaceProblem?>(null) }
    var askingRemove by rememberSaveable { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<StopDraft?>(null) }
    var dragOffset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val stops = remember {
        val first = editing?.places?.map { StopDraft(place = it.asPlace(), days = it.days.coerceIn(1, TravelPlace.MAX_DAYS)) }
        mutableStateListOf(*(first?.takeIf { it.isNotEmpty() } ?: listOf(StopDraft())).toTypedArray())
    }
    val scope = rememberCoroutineScope()
    val starts = date?.let { chainedDays(it, stops.map { stop -> stop.days }) }
    val total = stops.sumOf { it.days }
    val ready = date != null && stops.all { it.place != null }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CarnetColors.Page, contentColor = CarnetColors.Ink) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(if (editing != null) R.string.carnet_edit else R.string.carnet_add), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(stringResource(if (editing != null) R.string.carnet_edit_intro else R.string.carnet_add_intro), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Label(stringResource(R.string.carnet_add_name))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(TravelPlace.MAX_NAME) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.carnet_add_name_hint)) },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Label(stringResource(R.string.carnet_add_start))
            TripDateButton(date, stringResource(R.string.carnet_add_date_pick), onPick = { date = it; problem = null }, initial = today.minusDays(1))
            Label(stringResource(R.string.carnet_add_places))
            stops.forEachIndexed { index, stop ->
                // key: the card moves with its place while it is dragged, its drag going on.
                androidx.compose.runtime.key(stop) {
                val moving = dragging === stop
                StopCard(
                    modifier = Modifier
                        .zIndex(if (moving) 1f else 0f)
                        .graphicsLayer { translationY = if (moving) dragOffset else 0f }
                        .onSizeChanged { stop.height = it.height },
                    handle = if (stops.size < 2) null else Modifier.pointerInput(stop) {
                        val gap = 10.dp.toPx()
                        detectDragGestures(
                            onDragStart = { dragging = stop; dragOffset = 0f },
                            onDragEnd = { dragging = null; dragOffset = 0f },
                            onDragCancel = { dragging = null; dragOffset = 0f },
                        ) { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            val at = stops.indexOf(stop)
                            val next = stops.getOrNull(at + 1)
                            val before = stops.getOrNull(at - 1)
                            if (next != null && dragOffset > next.height / 2f + gap) {
                                stops.removeAt(at); stops.add(at + 1, stop); dragOffset -= next.height + gap
                            } else if (before != null && dragOffset < -(before.height / 2f + gap)) {
                                stops.removeAt(at); stops.add(at - 1, stop); dragOffset += before.height + gap
                            }
                        }
                    },
                    onMove = { by ->
                        val at = stops.indexOf(stop)
                        val to = at + by
                        if (to in stops.indices) { stops.removeAt(at); stops.add(to, stop) }
                    },
                    number = index + 1,
                    stop = stop,
                    start = starts?.getOrNull(index),
                    onSearch = {
                        val typed = stop.query.trim()
                        if (typed.isNotEmpty()) {
                            stop.search = DestinationSearch.Searching
                            scope.launch {
                                val found = runCatching { suggest(typed) }.getOrDefault(emptyList())
                                stop.search = if (found.isEmpty()) DestinationSearch.NothingFound else DestinationSearch.Found(found)
                            }
                        }
                    },
                    onRemove = when {
                        stops.size > 1 -> ({ stops.remove(stop) })
                        // The last place of a trip in the carnet: removing it removes the trip, after the question.
                        onRemoveTrip != null -> ({ askingRemove = true })
                        else -> null
                    },
                    onChange = { problem = null },
                )
                }
            }
            DashedAddButton(stringResource(R.string.carnet_add_another)) { stops += StopDraft() }
            problem?.let { Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            if (failed) Text(stringResource(R.string.carnet_add_failed), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Button(
                onClick = {
                    val first = stops.firstOrNull { it.place == null }
                    problem = travelPlaceProblem(first == null, date, total.takeIf { it in 1..TravelPlace.MAX_DAYS }, today)
                    val day = date
                    if (problem == null && day != null) onSave(name.trim().ifEmpty { null }, day, stops.map { stop -> stop.namedPlace() to stop.days })
                },
                enabled = !saving && ready,
                colors = ButtonDefaults.buttonColors(containerColor = CarnetColors.Ink, contentColor = androidx.compose.ui.graphics.Color.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(
                    when {
                        editing != null && ready -> pluralStringResource(R.plurals.carnet_edit_save_days, total, total)
                        editing != null -> stringResource(R.string.carnet_edit_save)
                        ready -> pluralStringResource(R.plurals.carnet_add_save_days, total, total)
                        else -> stringResource(R.string.carnet_add_save)
                    },
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            if (editing != null) {
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel), color = CarnetColors.Ink)
                }
                if (onRemoveTrip != null) {
                    TextButton(onClick = { askingRemove = true }, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.carnet_edit_remove), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (askingRemove && editing != null && onRemoveTrip != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askingRemove = false },
            title = { Text(stringResource(R.string.carnet_remove_title, editing.title)) },
            text = { Text(stringResource(R.string.carnet_remove_body)) },
            confirmButton = { TextButton(onClick = { askingRemove = false; onRemoveTrip() }) { Text(stringResource(R.string.carnet_remove), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { askingRemove = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** The place chosen, under the name typed for it (the one found when the field is left empty). */
private fun StopDraft.namedPlace(): Place {
    val picked = place!!
    val typed = label.trim().take(TravelPlace.MAX_NAME)
    return if (typed.isEmpty() || typed == placeLabel(picked)) picked else picked.copy(name = typed)
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = CarnetColors.Soft, modifier = Modifier.padding(top = 4.dp))
}

/** A place of the trip: its number, the search until one is chosen, then its dates and « − 3 j + ». */
@Composable
private fun StopCard(
    modifier: Modifier,
    handle: Modifier?,
    onMove: (Int) -> Unit,
    number: Int,
    stop: StopDraft,
    start: LocalDate?,
    onSearch: () -> Unit,
    onRemove: (() -> Unit)?,
    onChange: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White, shape).border(2.dp, CarnetColors.Dash, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val picked = stop.place
        val label = stringResource(R.string.carnet_add_stop, number)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (handle != null) {
                // ⠿: a long drag moves the place up or down (maquette « carnet-modifier-voyage »); TalkBack
                // gets « Monter » and « Descendre » instead.
                val shown = stop.label.ifBlank { label }
                val moveLabel = stringResource(R.string.carnet_add_move, shown)
                val up = stringResource(R.string.carnet_add_move_up, shown)
                val down = stringResource(R.string.carnet_add_move_down, shown)
                Box(
                    handle.size(56.dp).semantics {
                        contentDescription = moveLabel
                        customActions = listOf(
                            androidx.compose.ui.semantics.CustomAccessibilityAction(up) { onMove(-1); true },
                            androidx.compose.ui.semantics.CustomAccessibilityAction(down) { onMove(1); true },
                        )
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⠿", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = CarnetColors.Soft)
                }
            }
            Box(Modifier.size(32.dp).clip(CircleShape).background(CarnetColors.Ink), contentAlignment = Alignment.Center) {
                Text("$number", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
            }
            if (picked != null) {
                val placeName = stop.label.ifBlank { placeLabel(picked) }
                Text(placeName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 10.dp).semantics { contentDescription = "$label : $placeName" })
                TextButton(onClick = { stop.place = null; stop.label = ""; onChange() }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.carnet_add_change)) }
            } else {
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 10.dp))
            }
            if (onRemove != null) {
                val described = stringResource(R.string.carnet_add_stop_remove, picked?.title ?: label)
                androidx.compose.material3.IconButton(onClick = onRemove, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = described, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (picked == null) {
            OutlinedTextField(
                value = stop.query,
                onValueChange = { stop.query = it.take(60); stop.search = DestinationSearch.Idle },
                singleLine = true,
                label = { Text(stringResource(R.string.carnet_add_field)) },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = onSearch,
                enabled = stop.query.isNotBlank() && stop.search != DestinationSearch.Searching,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.carnet_add_search)) }
            when (val s = stop.search) {
                DestinationSearch.Searching -> Text(stringResource(R.string.carnet_add_searching), color = CarnetColors.Soft)
                DestinationSearch.NothingFound -> Text(stringResource(R.string.carnet_add_none), color = MaterialTheme.colorScheme.error)
                is DestinationSearch.Found -> Column(Modifier.selectableGroup()) {
                    s.places.forEach { place ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .selectable(selected = false, role = Role.RadioButton, onClick = { stop.place = place; stop.label = placeLabel(place); stop.search = DestinationSearch.Idle; onChange() }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Place, contentDescription = null, tint = CarnetColors.Soft)
                            Text(place.title.orEmpty(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                DestinationSearch.Idle -> Unit
            }
        } else {
            val name = stop.label.ifBlank { picked.title.orEmpty() }
            OutlinedTextField(
                value = stop.label,
                onValueChange = { stop.label = it.take(TravelPlace.MAX_NAME) },
                singleLine = true,
                label = { Text(stringResource(R.string.carnet_add_stop_name)) },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    start?.let { stringResource(R.string.carnet_add_stop_range, it.format(dayMonth), it.plusDays(stop.days.toLong()).format(dayMonth)) }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CarnetColors.Soft,
                    modifier = Modifier.weight(1f),
                )
                RoundStep("−", stringResource(R.string.carnet_add_less, name), enabled = stop.days > 1) { stop.days -= 1 }
                Text(stringResource(R.string.carnet_add_stop_days, stop.days), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp).widthIn(min = 40.dp), textAlign = TextAlign.Center)
                RoundStep("+", stringResource(R.string.carnet_add_more, name), enabled = stop.days < TravelPlace.MAX_DAYS) { stop.days += 1 }
            }
        }
    }
}

/** « 6 sept. » under a place of the trip. */
private val dayMonth = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.FRANCE)

/** − or + of the days: a 56 dp target around a 48 dp round. */
@Composable
private fun RoundStep(sign: String, described: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = described },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(48.dp).border(2.dp, CarnetColors.Dash, CircleShape), contentAlignment = Alignment.Center) {
            Text(sign, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (enabled) CarnetColors.Ink else CarnetColors.Soft.copy(alpha = 0.5f))
        }
    }
}

/** « ＋ Ajouter un autre lieu »: dashed, 56 dp. */
@Composable
private fun DashedAddButton(text: String, onClick: () -> Unit) {
    val dash = CarnetColors.Dash
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(20.dp))
            .drawBehind {
                drawRoundRect(
                    dash,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = CarnetColors.Ink, modifier = Modifier.size(20.dp))
        Text(text, fontWeight = FontWeight.ExtraBold, color = CarnetColors.Ink, modifier = Modifier.padding(start = 6.dp))
    }
}

private fun problemText(problem: TravelPlaceProblem): Int = when (problem) {
    TravelPlaceProblem.NoPlace -> R.string.carnet_add_no_place
    TravelPlaceProblem.NoDate -> R.string.carnet_add_no_date
    TravelPlaceProblem.DateNotPast -> R.string.carnet_add_not_past
    TravelPlaceProblem.Days -> R.string.carnet_add_bad_days
}
