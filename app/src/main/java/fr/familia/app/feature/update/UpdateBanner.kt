package fr.familia.app.feature.update

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews

/**
 * Offered above the tabs when a newer version is in the Drive folder (plan, lot 10). One line and
 * one button (DESIGN-5): the explanations come after the tap, in the update dialogs.
 */
@Composable
fun UpdateBanner(update: AppUpdate, onUpdate: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.update_available, update.versionName),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.update_later))
            }
            Button(onClick = onUpdate, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.update_action))
            }
        }
    }
}

@FamiliaPreviews
@Composable
private fun UpdateBannerPreview() {
    FamiliaTheme { UpdateBanner(AppUpdate("1.1.0", "https://example.com/familia.apk"), {}, {}) }
}
