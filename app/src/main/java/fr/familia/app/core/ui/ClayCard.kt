package fr.familia.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TextButton
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import fr.familia.app.core.theme.ClayBorder
import fr.familia.app.core.theme.ClayShadow
import fr.familia.app.core.theme.ClayStyle
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
fun Modifier.clayRelief(colors: ClayColors, shape: Shape, background: Color, elevation: Dp = 12.dp, light: Boolean = false): Modifier =
    this
        .shadow(
            elevation,
            shape,
            clip = false,
            ambientColor = if (light) colors.shadow.copy(alpha = colors.shadow.alpha * LIGHT_SHADOW) else colors.shadow,
            spotColor = if (light) colors.shadow.copy(alpha = colors.shadow.alpha * LIGHT_SHADOW) else colors.shadow,
        )
        .clip(shape)
        .drawBehind {
            drawRect(background)
            drawRect(
                Brush.linearGradient(
                    colors = listOf(if (light) colors.highlight.copy(alpha = colors.highlight.alpha * LIGHT_INSET) else colors.highlight, Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.5f, size.height * 0.6f),
                ),
            )
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, if (light) colors.shade.copy(alpha = colors.shade.alpha * LIGHT_INSET) else colors.shade),
                    start = Offset(size.width * 0.5f, size.height * 0.4f),
                    end = Offset(size.width, size.height),
                ),
            )
        }

/** Relief of a card that is not the main point of its screen (DESIGN-11): shadow and inset lighter. */
private const val LIGHT_SHADOW = 0.66f
private const val LIGHT_INSET = 0.5f
private val LIGHT_ELEVATION = 6.dp

/** The spaces of the airy layout (DESIGN-11). */
object ClaySpacing {
    /** Sides of a screen. */
    val screen = 20.dp

    /** Between two cards. */
    val between = 18.dp

    /** Inside a card: 16 dp at the top and bottom, 18 dp on the sides. */
    val card = PaddingValues(horizontal = 18.dp, vertical = 16.dp)

    /** The content padding of a list of cards. */
    val list = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
}

/** The tint of the card around, for [ClayCardTitle]. */
val LocalClayCardTint = staticCompositionLocalOf { ClayTint.Plain }

/**
 * The shared card of the Argile style. [tint] says who it is for and must double a « Pour … »
 * text; in the light themes the card stays white and the tint shows as a dot before its
 * [ClayCardTitle]. [emphasis] keeps the full relief for the main point of the screen (« À faire »
 * on the Accueil, the next event…); the others get a lighter one (DESIGN-11). Clickable as a whole
 * when [onClick] is set, with [onClickLabel] read by TalkBack. Colors and reliefs only come from
 * the theme: screens never draw their own.
 */
@Composable
fun ClayCard(
    modifier: Modifier = Modifier,
    tint: ClayTint = ClayTint.Plain,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = ClaySpacing.card,
    emphasis: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FamiliaClay.colors
    val style = FamiliaClay.style
    val shape = if (style.cardRadius == 28.dp) ClayCardShape else RoundedCornerShape(style.cardRadius)
    val stripe = colors.tintAsStripe && tint != ClayTint.Plain
    val background = colors.cardBackground(tint)
    val stripeColor = colors.tint(tint)
    Box(
        modifier = modifier
            .themedRelief(style, colors, shape, background, emphasis)
            // The dark-mode stripe is drawn, not laid out: it follows the card height whether the
            // card sits in a LazyColumn or in a bounded Column, and never stretches the card.
            .then(if (stripe) Modifier.drawBehind { drawStripe(stripeColor) } else Modifier)
            .then(style.border?.let { Modifier.themedBorder(it, shape) } ?: Modifier)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, LocalClayCardTint provides tint) {
            Column(Modifier.padding(contentPadding).padding(start = if (stripe) 8.dp else 0.dp), content = content)
        }
    }
}

/** The relief of the theme: the Argile one, a hard offset copy, a soft shadow or a glow, or none. */
private fun Modifier.themedRelief(style: ClayStyle, colors: ClayColors, shape: Shape, background: Color, emphasis: Boolean): Modifier =
    when (val kind = style.shadow) {
        ClayShadow.Relief -> if (emphasis) clayRelief(colors, shape, background) else clayRelief(colors, shape, background, LIGHT_ELEVATION, light = true)
        is ClayShadow.Hard -> this
            .drawBehind {
                val offset = (if (emphasis) kind.emphasisOffset else kind.offset).toPx()
                translate(offset, offset) {
                    drawOutline(shape.createOutline(size, layoutDirection, this), kind.color)
                }
            }
            .clip(shape)
            .drawBehind { drawRect(background) }
        is ClayShadow.Soft -> this
            .shadow(
                if (emphasis) kind.elevation else kind.elevation * LIGHT_SHADOW,
                shape,
                clip = false,
                ambientColor = kind.color,
                spotColor = kind.color,
            )
            .clip(shape)
            .drawBehind { drawRect(background) }
        ClayShadow.None -> clip(shape).drawBehind { drawRect(background) }
    }

/** A plain or dashed border, drawn inside the card's edge. */
private fun Modifier.themedBorder(line: ClayBorder, shape: Shape): Modifier =
    if (!line.dashed) {
        border(line.width, line.color, shape)
    } else {
        drawWithContent {
            drawContent()
            val width = line.width.toPx()
            val inset = width / 2
            translate(inset, inset) {
                val inner = Size(size.width - width, size.height - width)
                drawOutline(
                    shape.createOutline(inner, layoutDirection, this),
                    line.color,
                    style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * width, 2 * width))),
                )
            }
        }
    }

/**
 * The title of a card, with the 10 dp dot of its tint before it in the light themes (DESIGN-11).
 * A heading for TalkBack; the dot is decoration, the « Pour … » line says who it is for.
 */
@Composable
fun ClayCardTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    tint: ClayTint = LocalClayCardTint.current,
) {
    val colors = FamiliaClay.colors
    Row(modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
        if (colors.tintAsDot && tint != ClayTint.Plain) {
            ClayDot(colors.mark(tint))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = style)
    }
}

/** The 10 dp dot of a tint, for a row that has no card title (a planning line, a chip). */
@Composable
fun ClayDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(color))
}

/**
 * A secondary action as a text link, « Tout le planning > », at least 56 dp tall: one filled
 * button per card at most, the others are links (DESIGN-11).
 */
@Composable
fun ClayLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
        Text(" ›", style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics { })
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
            ClayCardTitle("Repas de Noël", style = MaterialTheme.typography.titleMedium)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = FamiliaClay.colors.onTintVariant(tint))
        }
    }
    Surface {
        if (lazy) {
            LazyColumn(contentPadding = ClaySpacing.list, verticalArrangement = Arrangement.spacedBy(ClaySpacing.between)) {
                items(samples) { (tint, line) -> card(tint, line) }
            }
        } else {
            Column(Modifier.padding(ClaySpacing.list), verticalArrangement = Arrangement.spacedBy(ClaySpacing.between)) {
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
