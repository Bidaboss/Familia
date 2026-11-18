package fr.familia.app.feature.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.LocalClayColors
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
                // The 5 dp of the former bar go to the left margin: the content does not move.
                .padding(start = 17.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            header?.invoke()
            content()
        }
    }
}

private val TILE_CORNER = 22.dp

/** « (I) Inès · 9 h 12          [Bonne nouvelle] »: who, when, and the module. */
@Composable
fun FeedHeader(member: Member?, name: String, time: String, label: String, tone: FeedTone) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (member != null) MemberAvatar(member, size = 30.dp)
        Text(
            "$name · $time",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        FeedLabel(label, tone)
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

/** « Hugo : Champion ! », the last comment of a card, on one line. */
@Composable
fun FeedLastComment(name: String, text: String) {
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
            append(name)
            pop()
            append(" : ")
            append(text.replace(Regex("\\s+"), " "))
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** « Aujourd'hui ———— », the day of the cards under it. */
@Composable
fun FeedDayHeader(label: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)).background(MaterialTheme.colorScheme.outlineVariant))
    }
}
