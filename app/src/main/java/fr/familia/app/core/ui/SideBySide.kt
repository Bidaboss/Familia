package fr.familia.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** From this font scale (Android setting « Taille de la police »), side-by-side buttons stack. */
const val LARGE_TEXT_SCALE = 1.3f

/** True when the phone shows large text, so narrow columns would cut words in the middle. */
@Composable
fun isLargeText(): Boolean = LocalDensity.current.fontScale >= LARGE_TEXT_SCALE

/**
 * Buttons that share a line, each with the same width; with large text, one per line on the whole
 * width instead (retour d'essai du 18/11, « Cassé » : un mot coupé en deux dans un bouton étroit).
 * [content] receives the modifier to give each button.
 */
@Composable
fun SideBySide(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable (item: Modifier) -> Unit,
) {
    if (isLargeText()) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
            content(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            content(Modifier.weight(1f))
        }
    }
}
