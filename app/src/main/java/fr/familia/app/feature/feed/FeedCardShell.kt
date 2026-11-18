package fr.familia.app.feature.feed

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.core.model.Member
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
 * The frame of every card of the fil: one tile per actualité (PRODUIT-127, variante A). A white
 * card with corners of 22 dp like the cards of the Planning, a very soft shadow and a thin line of
 * the color of its type under it; the label of the type says the rest. [header] on one line, then
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
    val line = tone.bar.copy(alpha = 0.25f)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = TILE_LINE)
            // The thin colored line: the same tile, TILE_LINE lower, behind the card.
            .drawBehind {
                val radius = CornerRadius(TILE_CORNER.toPx())
                drawRoundRect(line, topLeft = Offset(0f, TILE_LINE.toPx()), size = size, cornerRadius = radius)
            }
            .shadow(elevation = 1.dp, shape = shape, ambientColor = TILE_SHADOW, spotColor = TILE_SHADOW),
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
private val TILE_LINE = 2.dp

/** Brown at 7 %: a shadow that only lifts the tile off the dotted background. */
private val TILE_SHADOW = Color(0x12503A28)

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
