package fr.familia.app.feature.trips.word

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.feature.trips.TripTile
import fr.familia.app.feature.trips.map.PlaceLinkKind
import fr.familia.app.feature.trips.map.placeLinks

/**
 * « Chercher plus loin » (récit 96, DESIGN-69), the last tile of « Sur place »: « Restaurants près
 * de… », « Hôtels à… » and « Voir… sur Tripadvisor », each a 56 dp line that opens another app. Always
 * there, whatever the network; nothing but the name of the place leaves the phone.
 */
@Composable
fun PlaceLinksTile(place: String) {
    val links = remember(place) { placeLinks(place) } ?: return
    val uri = LocalUriHandler.current
    val name = place.trim()
    TripTile(Icons.Outlined.Place, stringResource(R.string.place_links_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            links.forEach { link ->
                val label = when (link.kind) {
                    PlaceLinkKind.Restaurants -> stringResource(R.string.place_links_restaurants, name)
                    PlaceLinkKind.Hotels -> stringResource(R.string.place_links_hotels, name)
                    PlaceLinkKind.Tripadvisor -> stringResource(R.string.place_links_tripadvisor, name)
                }
                // A visible zone to touch: tinted, rounded, the text at the start, in the ink colour, with the « opens another app » icon.
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(role = Role.Button) { runCatching { uri.openUri(link.url) } }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        FormHint(stringResource(R.string.place_links_hint))
    }
}
