package fr.familia.app.feature.souvenirs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.model.Souvenir
import fr.familia.app.core.model.SouvenirCounters
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.clayRelief
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)

internal fun earnedLabel(at: Instant): String = at.atZone(ZoneId.of("Europe/Paris")).format(dateFormatter)

/**
 * SouvenirMedal ([DESIGN]): a clay circle of the family's light tint with a dotted inner ring, the
 * icon in the dark tint at 48 % of the diameter, and a badge over the bottom edge. Not earned
 * yet, a grey dotted outline and what is left to do. TalkBack reads it as one sentence.
 */
@Composable
fun SouvenirMedal(
    kind: SouvenirKind,
    earnedAt: Instant?,
    modifier: Modifier = Modifier,
    counters: SouvenirCounters = SouvenirCounters(),
    size: Dp = 96.dp,
) {
    val title = stringResource(kind.title)
    val earned = earnedAt != null
    val detail = when {
        earnedAt != null -> earnedLabel(earnedAt)
        kind.leftPlural != null -> kind.left(counters)!!.let { pluralStringResource(kind.leftPlural, it, it) }
        else -> kind.hint?.let { stringResource(it) }.orEmpty()
    }
    val spoken = if (earnedAt != null) stringResource(R.string.souvenir_spoken, title, detail) else stringResource(R.string.souvenir_spoken_next, title, detail)
    Column(
        modifier = modifier.width(size + 32.dp).clearAndSetSemantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(size + 12.dp), contentAlignment = Alignment.TopCenter) {
            val circle = if (earned) {
                Modifier.size(size).clayRelief(LocalClayColors.current, CircleShape, kind.family.light)
            } else {
                Modifier.size(size).drawBehind { dottedRing(Color.Gray, inset = 0f) }
            }
            Box(circle.then(if (earned) Modifier.drawBehind { dottedRing(kind.family.dark.copy(alpha = 0.35f), inset = 6.dp.toPx()) } else Modifier), contentAlignment = Alignment.Center) {
                Icon(kind.icon, contentDescription = null, tint = if (earned) kind.family.dark else Color.Gray, modifier = Modifier.size(size * 0.48f))
            }
            if (earned) {
                Surface(
                    color = kind.family.dark,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(2.dp, Color.White),
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = 0.dp),
                ) {
                    Text(stringResource(kind.badge), fontFamily = Fredoka, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                }
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.dottedRing(color: Color, inset: Float) {
    val stroke = 2.dp.toPx()
    drawCircle(
        color = color,
        radius = size.minDimension / 2 - inset - stroke / 2,
        center = Offset(size.width / 2, size.height / 2),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
    )
}

@FamiliaPreviews
@Composable
private fun SouvenirMedalPreview() {
    FamiliaTheme {
        Surface {
            androidx.compose.foundation.layout.Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SouvenirMedal(SouvenirKind.FirstGoodNews, Instant.parse("2026-12-02T10:00:00Z"))
                SouvenirMedal(SouvenirKind.HelpGiven5, null, counters = SouvenirCounters(helpGiven = 2))
                SouvenirMedal(SouvenirKind.OneYear, null)
            }
        }
    }
}

/** Fictional souvenirs for previews. */
internal fun previewSouvenirs() = listOf(
    Souvenir("firstGoodNews", Instant.parse("2026-12-02T10:00:00Z"), shown = true),
    Souvenir("firstWeeklyAnswer", Instant.parse("2026-11-30T09:00:00Z"), shown = false),
)
