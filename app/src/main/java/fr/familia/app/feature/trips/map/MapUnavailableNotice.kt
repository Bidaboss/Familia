package fr.familia.app.feature.trips.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.detail.TripSheetColors

/**
 * Where the map should be, when it did not come (retour d'essai du 24/11): never a silent cut.
 * A discreet line, « Carte indisponible », and « Réessayer » (56 dp), on the sky of the trip; the
 * sheet below keeps its heights. The reason shows small under it, and goes to « Plantages » for the admin.
 */
@Composable
fun MapUnavailableNotice(onRetry: () -> Unit, modifier: Modifier = Modifier, reason: String? = null) {
    Box(modifier.background(TripPathColors.Sky).windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 24.dp).semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.trip_map_unavailable), style = MaterialTheme.typography.bodyLarge, color = TripSheetColors.InkSoft)
            // The reason, small, so that a screenshot is enough to say why (no waiting for « Plantages »).
            reason?.let { Text(stringResource(R.string.trip_map_unavailable_reason, it), style = MaterialTheme.typography.bodySmall, color = TripSheetColors.InkSoft) }
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.trip_map_retry), color = TripSheetColors.Ink)
            }
        }
    }
}
