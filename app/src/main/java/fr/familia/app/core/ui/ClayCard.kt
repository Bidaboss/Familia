package fr.familia.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import fr.familia.app.core.theme.ClayColors
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme

/** Corner radius of an Argile card (design principles, « Style Argile »). */
val ClayCardShape = RoundedCornerShape(28.dp)

/**
 * The relief of the Argile style: a soft colored outer shadow, then [background] with a light
 * highlight at the top left and a slight shade at the bottom right, drawn inside the shape and
 * behind the content, so text keeps its contrast. No blur is used.
 */
fun Modifier.clayRelief(colors: ClayColors, shape: Shape, background: Color, elevation: Dp = 12.dp): Modifier =
    this
        .shadow(elevation, shape, clip = false, ambientColor = colors.shadow, spotColor = colors.shadow)
        .clip(shape)
        .drawBehind {
            drawRect(background)
            drawRect(
                Brush.linearGradient(
                    colors = listOf(colors.highlight, Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.5f, size.height * 0.6f),
                ),
            )
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, colors.shade),
                    start = Offset(size.width * 0.5f, size.height * 0.4f),
                    end = Offset(size.width, size.height),
                ),
            )
        }

/**
 * The shared card of the Argile style. [tint] says who it is for and must double a « Pour … »
 * text. Clickable as a whole when [onClick] is set, with [onClickLabel] read by TalkBack.
 * Colors and reliefs only come from the theme: screens never draw their own.
 */
@Composable
fun ClayCard(
    modifier: Modifier = Modifier,
    tint: ClayTint = ClayTint.Plain,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FamiliaClay.colors
    val stripe = colors.tintAsStripe && tint != ClayTint.Plain
    val background = if (colors.tintAsStripe) colors.card else colors.tint(tint)
    val stripeColor = colors.tint(tint)
    Box(
        modifier = modifier
            .clayRelief(colors, ClayCardShape, background)
            // The dark-mode stripe is drawn, not laid out: it follows the card height whether the
            // card sits in a LazyColumn or in a bounded Column, and never stretches the card.
            .then(if (stripe) Modifier.drawBehind { drawStripe(stripeColor) } else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.padding(contentPadding).padding(start = if (stripe) 8.dp else 0.dp), content = content)
        }
    }
}

/** A 4 dp rounded bar at the start of the card, 12 dp from the edge and 16 dp from top and bottom. */
private fun DrawScope.drawStripe(color: Color) {
    val width = 4.dp.toPx()
    val inset = 12.dp.toPx()
    val vertical = 16.dp.toPx()
    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - inset - width else inset
    drawRoundRect(
        color = color,
        topLeft = Offset(x, vertical),
        size = Size(width, (size.height - 2 * vertical).coerceAtLeast(0f)),
        cornerRadius = CornerRadius(width / 2),
    )
}

/**
 * The four tints, for previews and screenshots (fictional texts). [lazy] puts them in a
 * LazyColumn, where cards get no height constraint, like in the real screens.
 */
@Composable
internal fun ClayCardSamples(lazy: Boolean = false) {
    val samples = listOf(
        ClayTint.Plain to "Pour moi · 9 h 00",
        ClayTint.Household to "Pour le Foyer Martin · 18 h 30",
        ClayTint.Family to "Pour toute la famille · 12 h 30",
        ClayTint.Birthday to "Anniversaire de Camille · 10 ans",
    )
    val card: @Composable (ClayTint, String) -> Unit = { tint, line ->
        ClayCard(Modifier.fillMaxWidth(), tint = tint, onClick = {}) {
            Text("Repas de Noël", style = MaterialTheme.typography.titleMedium)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = FamiliaClay.colors.onTintVariant(tint))
        }
    }
    Surface {
        if (lazy) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(samples) { (tint, line) -> card(tint, line) }
            }
        } else {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                samples.forEach { (tint, line) -> card(tint, line) }
            }
        }
    }
}

@FamiliaPreviews
@Composable
private fun ClayCardPreview() {
    FamiliaTheme { ClayCardSamples() }
}
