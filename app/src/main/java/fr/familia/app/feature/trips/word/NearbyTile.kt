package fr.familia.app.feature.trips.word

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.place.NearbyKind
import fr.familia.app.core.place.NearbyPlace
import fr.familia.app.core.place.NearbyPlaces
import fr.familia.app.core.place.distanceLabel
import fr.familia.app.core.place.geoLink
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.feature.trips.TripTile

/** What the tile shows for the chosen kind. */
private sealed interface NearbyState {
    data object Loading : NearbyState
    /** No network on the phone. */
    data object Offline : NearbyState

    /** The phone is online but the service did not answer (busy, refused). */
    data object Unavailable : NearbyState
    data class Found(val places: List<NearbyPlace>) : NearbyState
}

/**
 * « Autour de l'étape » (récit 96, DESIGN-69): up to 5 places within 5 km of the place of the step,
 * « À manger » or « À voir », nearest first. A touch opens the place in the map app. The search is
 * around the step, never around the phone: no location permission. Nothing is stored.
 */
@Composable
fun NearbyTile(lat: Double, lng: Double, nearby: NearbyPlaces?) {
    if (nearby == null) return
    var kind by rememberSaveable { mutableStateOf(NearbyKind.Eat) }
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(lat, lng, kind, attempt) { mutableStateOf<NearbyState>(NearbyState.Loading) }
    val context = LocalContext.current
    LaunchedEffect(lat, lng, kind, attempt) {
        state = nearby.around(lat, lng, kind)?.let { NearbyState.Found(it) } ?: if (hasNetwork(context)) NearbyState.Unavailable else NearbyState.Offline
    }
    val uri = LocalUriHandler.current
    TripTile(Icons.Outlined.Place, stringResource(R.string.nearby_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
            FilterChip(
                selected = kind == NearbyKind.Eat,
                onClick = { kind = NearbyKind.Eat },
                label = { Text(stringResource(R.string.nearby_eat)) },
                modifier = Modifier.heightIn(min = 56.dp),
            )
            FilterChip(
                selected = kind == NearbyKind.See,
                onClick = { kind = NearbyKind.See },
                label = { Text(stringResource(R.string.nearby_see)) },
                modifier = Modifier.heightIn(min = 56.dp),
            )
        }
        when (val current = state) {
            NearbyState.Loading -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) {
                        Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                    }
                }
                FormHint(stringResource(R.string.nearby_loading))
            }
            NearbyState.Offline -> FormHint(stringResource(R.string.nearby_offline))
            NearbyState.Unavailable -> {
                FormHint(stringResource(R.string.nearby_unavailable))
                TextButton(onClick = { attempt++ }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_retry)) }
            }
            is NearbyState.Found -> if (current.places.isEmpty()) {
                FormHint(stringResource(R.string.nearby_none))
            } else {
                current.places.forEach { place -> NearbyRow(place, onOpen = { runCatching { uri.openUri(geoLink(place)) } }) }
                FormHint(stringResource(R.string.nearby_opens_map))
            }
        }
        FormHint(stringResource(R.string.nearby_source))
    }
}

/** One place, at least 56 dp high, read as « Le Cabanon, Restaurant, à 350 m, ouvre la carte ». */
@Composable
private fun NearbyRow(place: NearbyPlace, onOpen: () -> Unit) {
    val distance = distanceLabel(place.meters)
    val description = stringResource(R.string.nearby_row_description, place.name, place.genre, distance)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = description }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.bodyLarge)
            Text("${place.genre} · $distance", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** True when the phone has a network that reaches the internet; true too when it cannot tell. */
private fun hasNetwork(context: android.content.Context): Boolean = runCatching {
    val manager = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return@runCatching true
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return@runCatching false
    capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}.getOrDefault(true)
