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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.SideBySide
import fr.familia.app.core.welcome.OLIVE_RING_RADIUS
import fr.familia.app.core.welcome.OLIVE_RING_WIDTH
import fr.familia.app.core.welcome.drawOlive
import fr.familia.app.core.welcome.rememberOlivePaths

/** The card is dark on every theme (DESIGN-47, D), so its ink is fixed too. */
private val CARD_DARK = Color(0xFF1E1B18)
private val CARD_INK = Color.White
private val CARD_MUTED = Color(0xFFE6E0D8)
private val CARD_OUTLINE = Color(0xFF8A847C)
private val UPDATE_YELLOW = Color(0xFFF2C14E)

/** The sheet stays white on every theme (DESIGN-47, F). */
private val SHEET_BACKGROUND = Color.White
private val SHEET_INK = Color(0xFF1B1B1B)
private val SHEET_MUTED = Color(0xFF5C5650)
private val SHEET_KICKER = Color(0xFF1F5C3C)

/**
 * Offered when a newer version is in the Drive folder (plan, lot 10). Variante D (DESIGN-47, 19/11):
 * the dark card, after « Plus tard » of the sheet, at the place of the white card of before: above
 * « Tout Familia » on the Accueil and above the tabs elsewhere, over the content without pushing it.
 * The olive tree circled in yellow, « NOUVEAU », two lines, « Plus tard » and a yellow « Mettre à jour ».
 */
@Composable
fun UpdateBanner(onUpdate: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CARD_DARK,
        contentColor = CARD_INK,
        shadowElevation = 16.dp,
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                UpdateOlive(size = 60, yellowRing = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = UPDATE_YELLOW, contentColor = CARD_DARK) {
                        Text(
                            stringResource(R.string.update_banner_new),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.update_banner_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = Fredoka, fontSize = 20.sp, lineHeight = 24.sp),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.update_banner_line),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CARD_MUTED,
                    )
                }
            }
            // Side by side, or one under the other with large text, so « Mettre à jour » is never cut.
            SideBySide(spacing = 12.dp) { item ->
                OutlinedButton(
                    onClick = onLater,
                    border = BorderStroke(1.5.dp, CARD_OUTLINE),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = CARD_DARK, contentColor = CARD_INK),
                    modifier = item.heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.update_later), textAlign = TextAlign.Center)
                }
                Button(
                    onClick = onUpdate,
                    colors = ButtonDefaults.buttonColors(containerColor = UPDATE_YELLOW, contentColor = CARD_DARK),
                    modifier = item.heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.update_action), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Variante F (DESIGN-47): the first time a version is offered, a sheet comes up from the bottom over
 * a darkened screen. Shown once per version ([UpdatePreferences]); « Plus tard », a swipe down or
 * back all close it, and the dark card then stays until the update. It never blocks the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(onUpdate: () -> Unit, onLater: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onLater, containerColor = SHEET_BACKGROUND, contentColor = SHEET_INK) {
        UpdateSheetContent(onUpdate, onLater)
    }
}

/** What the sheet holds; it scrolls, so that it holds at a large font size. */
@Composable
internal fun UpdateSheetContent(onUpdate: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UpdateOlive(size = 104, yellowRing = false)
        Text(
            stringResource(R.string.update_sheet_kicker),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = SHEET_KICKER,
        )
        Text(
            stringResource(R.string.update_banner_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Fredoka, fontSize = 24.sp, lineHeight = 30.sp),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.update_sheet_line),
            style = MaterialTheme.typography.bodyLarge,
            color = SHEET_MUTED,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onUpdate,
            colors = ButtonDefaults.buttonColors(containerColor = SHEET_INK, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 4.dp),
        ) {
            Text(stringResource(R.string.update_sheet_action), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        }
        TextButton(
            onClick = onLater,
            colors = ButtonDefaults.textButtonColors(contentColor = SHEET_INK),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.update_later), textAlign = TextAlign.Center)
        }
    }
}

/** The final olive tree in its sun and thin red ring, as on the icon; on the dark card, circled in yellow. Decorative only. */
@Composable
private fun UpdateOlive(size: Int, yellowRing: Boolean) {
    val olive = rememberOlivePaths()
    Canvas(Modifier.size(size.dp)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val ring = if (yellowRing) 3.dp.toPx() else 0f
        val unit = (this.size.minDimension / 2f - ring * 1.5f) / (OLIVE_RING_RADIUS + OLIVE_RING_WIDTH)
        drawOlive(olive, center, unit, fruitDetails = false)
        if (yellowRing) {
            drawCircle(UPDATE_YELLOW, radius = this.size.minDimension / 2f - ring / 2f, center = center, style = Stroke(width = ring))
        }
    }
}

@FamiliaPreviews
@Composable
private fun UpdateBannerPreview() {
    FamiliaTheme { UpdateBanner({}, {}, Modifier.padding(12.dp)) }
}

@FamiliaPreviews
@Composable
private fun UpdateSheetPreview() {
    FamiliaTheme { Surface(color = SHEET_BACKGROUND) { UpdateSheetContent({}, {}) } }
}
