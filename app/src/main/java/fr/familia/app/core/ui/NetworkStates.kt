package fr.familia.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.photo.LocalOnline
import fr.familia.app.core.theme.FamiliaTheme
import kotlinx.coroutines.delay

/** Past this, a screen still loading says why it takes time. */
internal const val SLOW_LOADING_MILLIS = 6_000L

/**
 * A screen that loads, on a weak network too: the wheel, then a line that says it takes time,
 * or that the network is gone and the screen fills by itself when it comes back. Never a blank screen.
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val online = LocalOnline.current
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SLOW_LOADING_MILLIS)
        slow = true
    }
    Column(
        modifier.padding(32.dp).widthIn(max = 360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        val line = when {
            !online -> R.string.loading_offline
            slow -> R.string.loading_slow
            else -> null
        }
        if (line != null) {
            Text(
                stringResource(line),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** « En attente du réseau »: written on this phone, it goes by itself, once, when the network is back. */
@Composable
fun PendingSign(modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = color, modifier = Modifier.size(16.dp)) // The text says it.
        Text(stringResource(R.string.pending_network), style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@FamiliaPreviews
@Composable
private fun NetworkStatesPreview() {
    FamiliaTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LoadingState()
            PendingSign()
        }
    }
}
