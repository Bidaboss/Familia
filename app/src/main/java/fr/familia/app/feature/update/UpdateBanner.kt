package fr.familia.app.feature.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.SideBySide
import fr.familia.app.core.welcome.OLIVE_RING_RADIUS
import fr.familia.app.core.welcome.OLIVE_RING_WIDTH
import fr.familia.app.core.welcome.drawOlive
import fr.familia.app.core.welcome.rememberOlivePaths

/** The card stays white on every theme, so its ink is fixed too (PRODUIT-129). */
private val BANNER_CARD = Color.White
private val BANNER_INK = Color(0xFF1B1B1B)
private val BANNER_MUTED = Color(0xFF5C5650)
private val BANNER_OUTLINE = Color(0xFFE2DDD6)

/**
 * Offered when a newer version is in the Drive folder (plan, lot 10). Variante A (PRODUIT-129,
 * 18/11): a white card floating above « Tout Familia » on the Accueil and above the tabs elsewhere,
 * over the content without pushing it. The small olive tree in its ring, two lines, « Plus tard »
 * and « Mettre à jour »; the explanations come after the tap, in the update dialogs.
 */
@Composable
fun UpdateBanner(onUpdate: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = BANNER_CARD,
        contentColor = BANNER_INK,
        shadowElevation = 8.dp,
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                UpdateOlive()
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.update_banner_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.update_banner_line),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BANNER_MUTED,
                    )
                }
            }
            // Side by side, or one under the other with large text, so « Mettre à jour » is never cut.
            SideBySide(spacing = 12.dp) { item ->
                OutlinedButton(
                    onClick = onLater,
                    border = BorderStroke(1.5.dp, BANNER_OUTLINE),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = BANNER_CARD, contentColor = BANNER_INK),
                    modifier = item.heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.update_later), textAlign = TextAlign.Center)
                }
                Button(
                    onClick = onUpdate,
                    colors = ButtonDefaults.buttonColors(containerColor = BANNER_INK, contentColor = Color.White),
                    modifier = item.heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.update_action), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** The final olive tree in its sun and thin red ring, as on the icon; decorative only. */
@Composable
private fun UpdateOlive() {
    val olive = rememberOlivePaths()
    Canvas(Modifier.size(52.dp)) {
        val unit = size.minDimension / 2f / (OLIVE_RING_RADIUS + OLIVE_RING_WIDTH)
        drawOlive(olive, Offset(size.width / 2f, size.height / 2f), unit)
    }
}

@FamiliaPreviews
@Composable
private fun UpdateBannerPreview() {
    FamiliaTheme { UpdateBanner({}, {}, Modifier.padding(12.dp)) }
}
