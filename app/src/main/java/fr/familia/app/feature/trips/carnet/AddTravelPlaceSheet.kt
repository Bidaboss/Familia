package fr.familia.app.feature.trips.carnet

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.TravelPlaceProblem
import fr.familia.app.core.model.travelPlaceProblem
import fr.familia.app.core.place.Place
import fr.familia.app.feature.trips.TripDateButton
import fr.familia.app.feature.trips.edit.DestinationSearch
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * « Ajouter une destination » (règle 38): a trip already done, a place chosen among the answers of
 * the phone's geocoder (a city, a region or a country, règle 17), a past day and a number of days.
 * [suggest] asks the geocoder; [failed] says the last save did not go through.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTravelPlaceSheet(
    today: LocalDate,
    saving: Boolean,
    failed: Boolean,
    suggest: suspend (String) -> List<Place>,
    onSave: (Place, LocalDate, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var chosen by rememberSaveable(stateSaver = PlaceSaver) { mutableStateOf<Place?>(null) }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var days by rememberSaveable { mutableStateOf("") }
    var problem by rememberSaveable { mutableStateOf<TravelPlaceProblem?>(null) }
    var search by remember { mutableStateOf<DestinationSearch>(DestinationSearch.Idle) }
    val scope = rememberCoroutineScope()
    val runSearch = {
        val typed = query.trim()
        if (typed.isNotEmpty()) {
            search = DestinationSearch.Searching
            scope.launch {
                val found = runCatching { suggest(typed) }.getOrDefault(emptyList())
                search = if (found.isEmpty()) DestinationSearch.NothingFound else DestinationSearch.Found(found)
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CarnetColors.Page, contentColor = CarnetColors.Ink) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.carnet_add), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.carnet_add_intro), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            val picked = chosen
            if (picked != null) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Place, contentDescription = null, tint = CarnetColors.Ink)
                    Text(picked.title.orEmpty(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    TextButton(onClick = { chosen = null }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.carnet_add_change)) }
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(60); search = DestinationSearch.Idle },
                    singleLine = true,
                    label = { Text(stringResource(R.string.carnet_add_field)) },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = runSearch,
                    enabled = query.isNotBlank() && search != DestinationSearch.Searching,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.carnet_add_search)) }
                when (val s = search) {
                    DestinationSearch.Searching -> Text(stringResource(R.string.carnet_add_searching), color = CarnetColors.Soft)
                    DestinationSearch.NothingFound -> Text(stringResource(R.string.carnet_add_none), color = MaterialTheme.colorScheme.error)
                    is DestinationSearch.Found -> Column(Modifier.selectableGroup()) {
                        s.places.forEach { place ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .selectable(selected = false, role = Role.RadioButton, onClick = { chosen = place; problem = null }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Place, contentDescription = null, tint = CarnetColors.Soft)
                                Text(place.title.orEmpty(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    DestinationSearch.Idle -> Unit
                }
            }
            Text(stringResource(R.string.carnet_add_date), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = CarnetColors.Soft)
            TripDateButton(date, stringResource(R.string.carnet_add_date_pick), onPick = { date = it; problem = null }, initial = today.minusDays(1))
            OutlinedTextField(
                value = days,
                onValueChange = { typed -> days = typed.filter { it.isDigit() }.take(3); problem = null },
                singleLine = true,
                label = { Text(stringResource(R.string.carnet_add_days)) },
                placeholder = { Text(stringResource(R.string.carnet_add_days_hint)) },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            problem?.let { Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            if (failed) Text(stringResource(R.string.carnet_add_failed), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Button(
                onClick = {
                    val count = days.toIntOrNull()
                    problem = travelPlaceProblem(chosen != null, date, count, today)
                    val place = chosen
                    val day = date
                    if (problem == null && place != null && day != null && count != null) onSave(place, day, count)
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = CarnetColors.Ink, contentColor = androidx.compose.ui.graphics.Color.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.carnet_add_save), fontWeight = FontWeight.ExtraBold) }
        }
    }
}

/** Keeps the chosen place when Android recreates the sheet. */
private val PlaceSaver = listSaver<Place?, Any?>(
    save = { place -> if (place == null) emptyList() else listOf(place.lat, place.lng, place.name, place.address) },
    restore = { saved -> if (saved.isEmpty()) null else Place(saved[0] as Double, saved[1] as Double, saved[2] as String?, saved[3] as String?) },
)

private fun problemText(problem: TravelPlaceProblem): Int = when (problem) {
    TravelPlaceProblem.NoPlace -> R.string.carnet_add_no_place
    TravelPlaceProblem.NoDate -> R.string.carnet_add_no_date
    TravelPlaceProblem.DateNotPast -> R.string.carnet_add_not_past
    TravelPlaceProblem.Days -> R.string.carnet_add_bad_days
}
