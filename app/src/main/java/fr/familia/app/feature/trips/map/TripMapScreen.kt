package fr.familia.app.feature.trips.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.feature.trips.detail.TripDetailUiState
import fr.familia.app.core.model.Feature
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.model.TripWord
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.detail.palette
import fr.familia.app.feature.trips.detail.previewTripDetail
import fr.familia.app.feature.trips.stepTypeIcon
import fr.familia.app.feature.trips.stepTypeLabel

/**
 * « La carte du voyage » (récit 87): the steps joined in order on the landscape of the trip, with
 * their number; a touch on a point shows its step under the map. Followers and participants see
 * the same map (the steps are read with the same rules) and open the step, with its word (récit 88). A step with no point stays in the list under the map. No live position. Stateless.
 */
@Composable
fun TripMapScreen(
    state: TripDetailUiState,
    onOpenStep: (stepId: String) -> Unit,
    onEditStep: (stepId: String) -> Unit,
    onBack: () -> Unit,
    /** The public token when the streets are asked for (module on, token in this APK); null: the drawn map. */
    streetsToken: String? = null,
    /** The words of the steps by step id, for « Le trajet » (récit 94). */
    words: Map<String, TripWord> = emptyMap(),
) {
    val success = state as? TripDetailUiState.Success
    val sky = success?.trip?.landscape?.palette?.sky ?: TripPathColors.Sky
    Scaffold(
        containerColor = sky,
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.trip_map_title), maxLines = 2) },
                navigationIcon = { BackButton(onClick = onBack) },
                containerColor = sky,
                contentColor = TripPathColors.Ink,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                TripDetailUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                TripDetailUiState.Missing, TripDetailUiState.Error -> EmptyState(
                    icon = Icons.Outlined.Map,
                    title = stringResource(R.string.trip_missing_title),
                    body = stringResource(R.string.trip_missing_body),
                )
                is TripDetailUiState.Success -> MapContent(state, onOpenStep, onEditStep, streetsToken, words)
            }
        }
    }
}

@Composable
private fun MapContent(state: TripDetailUiState.Success, onOpenStep: (String) -> Unit, onEditStep: (String) -> Unit, streetsToken: String?, words: Map<String, TripWord>) {
    // The streets: the points are placed by the maths of the image; without them, as before.
    val streets = remember(state.steps, streetsToken) {
        streetsToken?.let { token ->
            val points = state.steps.filter { it.hasPosition }.map { GeoPoint(it.lat!!, it.lng!!) }
            streetsFrame(points)?.let { frame -> frame to streetsUrl(frame, token) }
        }
    }
    val stops = remember(state.steps, streets) {
        if (streets == null) mapStops(state.steps) else mapStops(state.steps, project = { projectStreets(it, streets.first) })
    }
    val entries = remember(stops, words) { routeEntries(stops, words) }
    // TalkBack or « supprimer les animations »: the list comes before the map.
    val listFirst = rememberPrefersStillMap()
    val placed = stops.filter { it.point != null }
    val unplaced = stops.filter { it.point == null }
    if (placed.isEmpty()) {
        // The sky colour is for the map; the text of this page follows the theme, so it sits on the theme background.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EmptyState(
                icon = Icons.Outlined.Map,
                title = stringResource(R.string.trip_map_empty_title),
                body = stringResource(R.string.trip_map_empty_body),
            )
            TripRouteTile(entries, onOpenStep, Modifier.padding(horizontal = 16.dp))
            // « Chez Mamie »: a place the phone cannot find says so, instead of nothing.
            if (unplaced.isNotEmpty()) {
                Box(Modifier.padding(16.dp)) { UnplacedTile(unplaced, state.isParticipant, onEditStep) }
            }
        }
        }
        return
    }
    var selectedId by rememberSaveable(state.trip.id) { mutableStateOf<String?>(null) }
    val selected = placed.firstOrNull { it.step.id == selectedId }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (listFirst) TripRouteTile(entries, onOpenStep)
        if (streets == null) {
            TripMap(placed, selectedId, onSelect = { selectedId = it }, landscape = state.trip.landscape, modifier = Modifier.fillMaxWidth())
        } else {
            TripMap(
                placed, selectedId, onSelect = { selectedId = it }, landscape = state.trip.landscape, modifier = Modifier.fillMaxWidth(),
                background = { StreetsBackground(streets.second, state.trip.landscape) },
            )
        }
        if (streets != null) StreetsCredit()
        if (selected == null) {
            Text(stringResource(R.string.trip_map_pick), style = MaterialTheme.typography.bodyLarge, color = TripPathColors.Ink)
        } else {
            StopCard(selected, onOpen = { onOpenStep(selected.step.id) })
        }
        if (!listFirst) TripRouteTile(entries, onOpenStep)
        if (unplaced.isNotEmpty()) UnplacedTile(unplaced, state.isParticipant, onEditStep)
    }
}

/** The attribution of Mapbox and OpenStreetMap under the streets, as their terms ask: text and links, legible. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreetsCredit() {
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.trip_map_streets_credit), style = MaterialTheme.typography.bodyMedium, color = TripPathColors.Ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                R.string.trip_map_credit_mapbox to "https://www.mapbox.com/about/maps",
                R.string.trip_map_credit_osm to "https://www.openstreetmap.org/about/",
                R.string.trip_map_credit_improve to "https://apps.mapbox.com/feedback/",
            ).forEach { (label, url) ->
                TextButton(onClick = { runCatching { uri.openUri(url) } }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(label)) }
            }
        }
    }
}

/**
 * The steps the phone found no point for (« chez Mamie », « le camping »): each says « Position non
 * trouvée », and a participant can type a more precise address in the step.
 */
@Composable
private fun UnplacedTile(unplaced: List<MapStop>, canEdit: Boolean, onEditStep: (String) -> Unit) {
    FormTile(Icons.Outlined.Place, stringResource(R.string.trip_map_unplaced_title)) {
        unplaced.forEach { stop ->
            Column(Modifier.fillMaxWidth()) {
                Text("${stop.number}. ${stop.step.place}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.trip_map_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canEdit) {
                    TextButton(onClick = { onEditStep(stop.step.id) }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.trip_map_type_address))
                    }
                }
            }
        }
        FormHint(stringResource(R.string.trip_map_unplaced_hint))
    }
}

/** The step of the chosen point: its rank, its place, its type and day, « Bien arrivé ». */
@Composable
private fun StopCard(stop: MapStop, onOpen: () -> Unit) {
    val step: TripStep = stop.step
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(40.dp).background(TripPathColors.Button, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(stepTypeIcon(step.type), contentDescription = null, tint = TripPathColors.InkSoft)
                }
                Column(Modifier.weight(1f)) {
                    Text(step.place, style = MaterialTheme.typography.titleLarge, color = TripPathColors.Ink, modifier = Modifier.semantics { heading() })
                    Text(
                        stringResource(R.string.trip_map_rank, stop.number, stop.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TripPathColors.InkSoft,
                    )
                }
            }
            Text(
                "${stepTypeLabel(step.type)} · ${frenchDate(step.departDate, "EEEE d MMMM")}",
                style = MaterialTheme.typography.bodyLarge,
                color = TripPathColors.Ink,
            )
            step.arrival?.let {
                Text(stringResource(R.string.trip_arrived_by, it.name), style = MaterialTheme.typography.bodyMedium, color = TripPathColors.InkSoft)
            }
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.trip_map_open_step))
            }
            PlaceLinksRow(step.place)
        }
    }
}

/** « Restaurants près de… », « Hôtels à… », « Voir sur Tripadvisor » for the step, behind the module (récit 96). */
@Composable
private fun PlaceLinksRow(place: String) {
    if (!LocalFeatureFlags.current.isOn(Feature.PlaceLinks)) return
    val links = remember(place) { placeLinks(place) } ?: return
    val uri = LocalUriHandler.current
    val name = place.trim()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        links.forEach { link ->
            val label = when (link.kind) {
                PlaceLinkKind.Restaurants -> stringResource(R.string.place_links_restaurants, name)
                PlaceLinkKind.Hotels -> stringResource(R.string.place_links_hotels, name)
                PlaceLinkKind.Tripadvisor -> stringResource(R.string.place_links_tripadvisor, name)
            }
            TextButton(onClick = { runCatching { uri.openUri(link.url) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(label, color = TripPathColors.Ink)
            }
        }
        Text(stringResource(R.string.place_links_hint), style = MaterialTheme.typography.bodySmall, color = TripPathColors.InkSoft)
    }
}

@FamiliaPreviews
@Composable
private fun TripMapPreview() {
    FamiliaTheme { Surface { TripMapScreen(previewTripDetail(withPositions = true), {}, {}, {}) } }
}
