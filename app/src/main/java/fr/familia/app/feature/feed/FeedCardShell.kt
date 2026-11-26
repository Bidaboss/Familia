package fr.familia.app.feature.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.theme.photoCard
import fr.familia.app.core.ui.MemberAvatar

/**
 * The three colors of the cards of the maquette F2 bis: the bar on the left of the card and its
 * label. Kept the same in light and dark: they sit on their own background.
 */
enum class FeedTone(val bar: Color, val labelBackground: Color, val label: Color) {
    /** Bonnes nouvelles, albums. */
    Green(Color(0xFFA8E0C2), Color(0xFFEDF7F0), Color(0xFF1F5C3C)),

    /** Anniversaires, recettes, question de la semaine. */
    Peach(Color(0xFFF9B99A), Color(0xFFFDF1EA), Color(0xFF8A3A12)),

    /** Planning, coups de main. */
    Lilac(Color(0xFFC9B8F5), Color(0xFFF4F0FD), Color(0xFF4A3F8C)),
}

/**
 * The frame of every card of the fil: one tile per actualité (PRODUIT-127, then PRODUIT-130
 * variante A). The card color of the theme, like ClayCard (the surface of Material is the screen
 * background in some themes, and the tile vanished there), corners of 22 dp, a 1.5 dp outline of
 * the theme and a soft shadow; the label of the type says the rest. [header] on one line, then
 * [content]. The whole card opens [onOpen].
 */
@Composable
fun FeedCardShell(
    tone: FeedTone,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (LocalFeedGlass.current) {
        GlassCard(onOpen, modifier, header, content)
        return
    }
    val shape = RoundedCornerShape(TILE_CORNER)
    val card = LocalClayColors.current.card
    // Brown-black shadow: 10 % on a light card, 35 % on a dark one, so it still lifts the tile.
    val shadow = Color.Black.copy(alpha = if (card.luminance() < 0.5f) 0.35f else 0.10f)
    Surface(
        shape = shape,
        color = card,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            // About 2 dp down and 8 dp of blur.
            .shadow(elevation = 4.dp, shape = shape, ambientColor = shadow, spotColor = shadow),
    ) {
        Column(
            Modifier
                .clip(shape)
                .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
                .padding(start = CARD_SIDE, end = CARD_SIDE, top = 10.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            header?.invoke()
            content()
            FeedCardSocial(onOpen)
        }
    }
}

private val TILE_CORNER = 22.dp

/** The bar of J'aime and comments, when the gazette gave one to the card being drawn (récit 115). */
@Composable
private fun FeedCardSocial(onOpen: (() -> Unit)?) {
    val social = LocalFeedCardSocial.current ?: return
    val env = LocalFeedSocialEnv.current ?: return
    FeedSocialBar(social, env, onOpen, onDetail = LocalFeedCardDetail.current)
}

/**
 * Whether the fil lies on the photo of the Accueil (récit 113, fiche A2): its cards are then of glass,
 * its day titles white on the photo. Provided by the Accueil only.
 */
val LocalFeedGlass = staticCompositionLocalOf { false }

/** The dark ink of the glass cards, the same in light and dark mode (DESIGN-160). */
private val GlassInk = Color(0xFF2B1D12)
private val GlassSoftInk = Color(0xFF6B4A1F)

/**
 * A card of the fil on the photo (fiche A2): the glass of the Accueil (radius 24), no shadow nor
 * outline. Its texts keep the dark ink of the cream glass, in dark mode too.
 */
@Composable
private fun GlassCard(
    onOpen: (() -> Unit)?,
    modifier: Modifier,
    header: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme.copy(
        onSurface = GlassInk,
        onSurfaceVariant = GlassSoftInk,
        onBackground = GlassInk,
        outlineVariant = Color(0x33000000),
    )
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides GlassInk) {
            Column(
                modifier
                    .fillMaxWidth()
                    .photoCard(GLASS_CORNER)
                    .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
                    // The prototype (.fcard): 10 dp at the top, 14 on the sides, 4 at the bottom.
                    .padding(start = CARD_SIDE, end = CARD_SIDE, top = 10.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                header?.invoke()
                content()
                FeedCardSocial(onOpen)
            }
        }
    }
}

private val GLASS_CORNER = 24.dp

/** The side margin inside a card; a photo goes over it, edge to edge ([feedEdgeToEdge]). */
private val CARD_SIDE = 14.dp

/**
 * A photo of a card from edge to edge (prototype: 366 dp in a card of 368): it takes the side
 * margins of the card back.
 */
fun Modifier.feedEdgeToEdge(): Modifier = layout { measurable, constraints ->
    val side = CARD_SIDE.roundToPx()
    val wide = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * side else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
    val shown = (placeable.width - 2 * side).coerceAtLeast(0)
    layout(shown, placeable.height) { placeable.place(-side, 0) }
}

/**
 * The top of a card (prototype, .fh): who in bold and what, « **Alix** a publié 3 photos », then the
 * emoji of the kind of card and the time, « 📷 8 h 12 ». No pill of the module: the emoji says it.
 * [trailing] is the « ⋯ » of my own publication.
 */
@Composable
fun FeedHeader(
    member: Member?,
    name: String,
    action: String,
    emoji: String,
    time: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (member != null) MemberAvatar(member, size = 34.dp)
        Column(Modifier.weight(1f)) {
            val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp)
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                    append(name)
                    pop()
                    if (action.isNotBlank()) append(" $action")
                },
                style = style,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text("$emoji $time", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        trailing?.invoke()
    }
}

/** The module of a card, in a small pill of its color. */
@Composable
fun FeedLabel(label: String, tone: FeedTone) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.ExtraBold,
        color = tone.label,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tone.labelBackground)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** « **Hugo** Champion ! », the last comment of a card, on one line (prototype: no colon). */
@Composable
fun FeedLastComment(name: String, text: String) {
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
            append(name)
            pop()
            append(" ")
            append(text.replace(Regex("\\s+"), " "))
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The orange of the day marks (prototype, .fd). */
private val DayOrange = Color(0xFFE8902F)

/** The brown of the inside of a day mark, on the photo as on the cream. */
private val DayBrown = Color(0xE63A2A1A)

/**
 * « ── (☀️) Aujourd'hui · mardi 24 novembre ── » (prototype, .fnew.fd): a pill outlined in orange,
 * centered, the emoji of the day in an orange round, a short white line on each side.
 */
@Composable
fun FeedDayHeader(label: String, emoji: String, modifier: Modifier = Modifier) {
    val glass = LocalFeedGlass.current
    val line = if (glass) Color.White else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.width(32.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(line))
        Spacer(Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(24.dp))
                .background(DayBrown)
                .border(BorderStroke(2.5.dp, DayOrange), RoundedCornerShape(24.dp))
                .padding(start = 5.dp, end = 16.dp, top = 5.dp, bottom = 5.dp)
                .semantics(mergeDescendants = true) { heading() },
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(DayOrange), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 16.sp, modifier = Modifier.clearAndSetSemantics { })
            }
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(32.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(line))
    }
}
