package fr.familia.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.welcome.OliveMascot

/**
 * Centered message shown when a screen has nothing to display yet, with an optional button.
 * [mascot]: an empty list shows the olive tree with the family's photos instead of [icon]
 * (DESIGN-58); never for an error.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    mascot: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (mascot) {
            OliveMascot(120.dp) // Decorative, like the icon: the title says it.
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null, // Decorative: the title says the same thing.
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

/**
 * The fallback of a screen with nothing to do yet (a question or a challenge still to come): never
 * a dead end, the family can always share a bonne nouvelle.
 */
@Composable
fun ShareGoodNewsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.empty_share_good_news)) }
}

@FamiliaPreviews
@Composable
private fun EmptyStatePreview() {
    FamiliaTheme {
        Surface {
            EmptyState(
                icon = Icons.Outlined.Event,
                title = stringResource(R.string.planning_empty_title),
                body = stringResource(R.string.planning_empty_body),
            )
        }
    }
}
