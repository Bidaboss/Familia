package fr.familia.app.feature.trips.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import fr.familia.app.feature.trips.list.GlobeFlight
import fr.familia.app.feature.trips.list.GlobeMarker
import fr.familia.app.feature.trips.list.TripsGlobe
import fr.familia.app.feature.trips.list.talkBackOn
import fr.familia.app.feature.trips.map.GeoPoint
import fr.familia.app.feature.trips.map.GlobeHealth
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.place.Place
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.trips.TripFormScaffold
import fr.familia.app.feature.trips.TripTile
import fr.familia.app.core.ui.form.FormHint

/**
 * « Où allez-vous ? » (récit 95, variante A de DESIGN-65): one question, the destination of the
 * trip, searched by the phone (up to 5 places). Stateless; « Je tape un nom » leaves without a place.
 * On the satellite globe (récit 110): it turns until a place is chosen, then flies over it; without
 * a [globeToken], with TalkBack or when the globe failed lately, the plain form stays.
 */
@Composable
fun TripDestinationScreen(
    query: String,
    search: DestinationSearch,
    chosen: Place?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPick: (Place) -> Unit,
    onContinue: () -> Unit,
    onTypeAName: () -> Unit,
    onBack: () -> Unit,
    globeToken: String = "",
) {
    val context = LocalContext.current
    var globeFailed by rememberSaveable { mutableStateOf(false) }
    val globeAllowed = remember { GlobeHealth.usable(context) && !talkBackOn(context) }
    val globe = globeToken.isNotBlank() && !globeFailed && globeAllowed
    // Each new choice flies again; the id only changes with the place.
    val flight = remember(chosen) { chosen?.let { GlobeFlight(it.hashCode(), "", GeoPoint(it.lat, it.lng)) } }
    TripFormScaffold(
        title = stringResource(R.string.trip_destination_title),
        help = ScreenHelp.TripEdit,
        onBack = onBack,
        action = if (chosen != null) stringResource(R.string.trip_destination_continue) else null,
        busy = false,
        onAction = onContinue,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (globe) {
                TripsGlobe(
                    token = globeToken,
                    markers = chosen?.let { listOf(GlobeMarker(GeoPoint(it.lat, it.lng), highlighted = true)) }.orEmpty(),
                    souvenirs = false,
                    stopped = false,
                    flight = flight,
                    onFailed = {
                        GlobeHealth.markFailed(context)
                        globeFailed = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clearAndSetSemantics { },
                )
            }
            TripTile(Icons.Outlined.Place, stringResource(R.string.trip_destination_question)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.trip_destination_field)) },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = onSearch,
                    enabled = query.isNotBlank() && search != DestinationSearch.Searching,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.trip_destination_search)) }
                when (search) {
                    DestinationSearch.Searching -> FormHint(stringResource(R.string.trip_destination_searching))
                    DestinationSearch.NothingFound -> FormHint(stringResource(R.string.trip_destination_none), isError = true)
                    is DestinationSearch.Found -> Column(Modifier.selectableGroup()) {
                        search.places.forEach { place ->
                            PlaceRow(place, selected = place == chosen, onPick = { onPick(place) })
                        }
                    }
                    DestinationSearch.Idle -> FormHint(stringResource(R.string.trip_destination_hint))
                }
            }
            TextButton(onClick = onTypeAName, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.trip_destination_type_name))
            }
        }
    }
}

/** One proposed place, at least 56 dp high, read as one choice by TalkBack. */
@Composable
private fun PlaceRow(place: Place, selected: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onPick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.padding(start = 4.dp)) {
            Icon(
                if (selected) Icons.Filled.Check else Icons.Outlined.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(place.title.orEmpty(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
