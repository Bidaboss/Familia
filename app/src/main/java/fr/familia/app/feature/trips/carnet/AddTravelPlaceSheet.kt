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
    var search by mutableStateOf<DestinationSearch>(DestinationSearch.Idle)
}

private const val DEFAULT_DAYS = 3

/**
 * « Ajouter un voyage » (règle 38, retour de l'admin du 26/11, maquette « carnet-voyage-plusieurs-lieux »):
 * a trip already done, with an optional name, its first day (past) and one or several places, each
 * chosen among the answers of the phone's geocoder (a city, a region or a country, règle 17) with its
 * number of days; their dates follow each other by themselves. It counts as one trip. [suggest] asks
 * the geocoder; [failed] says the last save did not go through.
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
) {
    var name by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var problem by rememberSaveable { mutableStateOf<TravelPlaceProblem?>(null) }
    val stops = remember { mutableStateListOf(StopDraft()) }
    val scope = rememberCoroutineScope()
    val starts = date?.let { chainedDays(it, stops.map { stop -> stop.days }) }
    val total = stops.sumOf { it.days }
    val ready = date != null && stops.all { it.place != null }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CarnetColors.Page, contentColor = CarnetColors.Ink) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.carnet_add), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.carnet_add_intro), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
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
                StopCard(
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
                    onRemove = if (stops.size > 1) ({ stops.remove(stop) }) else null,
                    onChange = { problem = null },
                )
            }
            DashedAddButton(stringResource(R.string.carnet_add_another)) { stops += StopDraft() }
            problem?.let { Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            if (failed) Text(stringResource(R.string.carnet_add_failed), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Button(
                onClick = {
                    val first = stops.firstOrNull { it.place == null }
                    problem = travelPlaceProblem(first == null, date, total.takeIf { it in 1..TravelPlace.MAX_DAYS }, today)
                    val day = date
                    if (problem == null && day != null) onSave(name.trim().ifEmpty { null }, day, stops.map { it.place!! to it.days })
                },
                enabled = !saving && ready,
                colors = ButtonDefaults.buttonColors(containerColor = CarnetColors.Ink, contentColor = androidx.compose.ui.graphics.Color.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(
                    if (ready) pluralStringResource(R.plurals.carnet_add_save_days, total, total) else stringResource(R.string.carnet_add_save),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = CarnetColors.Soft, modifier = Modifier.padding(top = 4.dp))
}

/** A place of the trip: its number, the search until one is chosen, then its dates and « − 3 j + ». */
@Composable
private fun StopCard(number: Int, stop: StopDraft, start: LocalDate?, onSearch: () -> Unit, onRemove: (() -> Unit)?, onChange: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White, shape).border(2.dp, CarnetColors.Dash, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val picked = stop.place
        val label = stringResource(R.string.carnet_add_stop, number)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(CarnetColors.Ink), contentAlignment = Alignment.Center) {
                Text("$number", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
            }
            if (picked != null) {
                val placeName = fr.familia.app.feature.trips.edit.destinationName(picked).ifBlank { picked.title.orEmpty() }
                Text(placeName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 10.dp).semantics { contentDescription = "$label : $placeName" })
                TextButton(onClick = { stop.place = null; onChange() }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.carnet_add_change)) }
            } else {
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 10.dp))
            }
            if (onRemove != null) {
                val described = stringResource(R.string.carnet_add_stop_remove, picked?.title ?: label)
                TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = described }) {
                    Text(stringResource(R.string.carnet_remove), color = MaterialTheme.colorScheme.error)
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
                                .selectable(selected = false, role = Role.RadioButton, onClick = { stop.place = place; stop.search = DestinationSearch.Idle; onChange() }),
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
            val name = picked.title.orEmpty()
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
