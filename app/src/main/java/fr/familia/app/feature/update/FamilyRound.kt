package fr.familia.app.feature.update

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.welcome.OLIVE_SUN_RADIUS
import fr.familia.app.core.welcome.WelcomeFruit
import fr.familia.app.core.welcome.drawMedallion
import fr.familia.app.core.welcome.drawOlive
import fr.familia.app.core.welcome.rememberOlivePaths
import fr.familia.app.core.welcome.welcomeFruits
import kotlin.math.cos
import kotlin.math.sin

/** One turn of the round, in milliseconds (spec DESIGN « mise à jour », 18/11). */
private const val TURN_MILLIS = 16_000

/** The leaves sway this many degrees each way, one way in [SWAY_MILLIS]. */
private const val SWAY_DEGREES = 1.6f
private const val SWAY_MILLIS = 1_600

/** The round is about 1.33 times as wide as the sun. */
private const val ROUND_TO_SUN = 1.33f

/**
 * « La ronde de la famille » while the update downloads (spec DESIGN, choisie par Kévin le 18/11):
 * the olive tree without fruits in its sun, and the family around it, each member as their photo or
 * their letters, turning slowly clockwise and always upright, while the leaves sway. Decorative:
 * TalkBack reads the percentage instead. Animations off on the phone: the round stays still.
 */
@Composable
fun FamilyRound(family: Family?, me: Member?, photos: Map<String, ImageBitmap>, modifier: Modifier = Modifier) {
    val members = remember(family, me) {
        if (family == null || me == null) emptyList() else welcomeFruits(family, me)
    }
    val context = LocalContext.current
    val still = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // At 200 % text the percentage keeps its room: the drawing goes down to 180 dp.
    val side = if (LocalDensity.current.fontScale >= 1.5f) 180.dp else 240.dp
    if (still) {
        RoundPicture(members, photos, turn = 0f, sway = 0f, modifier = modifier.size(side))
    } else {
        val motion = rememberInfiniteTransition(label = "round")
        val turn by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(TURN_MILLIS, easing = LinearEasing)), label = "turn")
        val sway by motion.animateFloat(-SWAY_DEGREES, SWAY_DEGREES, infiniteRepeatable(tween(SWAY_MILLIS), RepeatMode.Reverse), label = "sway")
        RoundPicture(members, photos, turn, sway, modifier.size(side))
    }
}

@Composable
private fun RoundPicture(members: List<WelcomeFruit>, photos: Map<String, ImageBitmap>, turn: Float, sway: Float, modifier: Modifier) {
    val olive = rememberOlivePaths()
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val medallion = size.minDimension * 27f / 240f / 2f
        val round = size.minDimension / 2f - medallion - 1.dp.toPx()
        val unit = round / ROUND_TO_SUN / OLIVE_SUN_RADIUS
        val center = Offset(size.width / 2f, size.height / 2f)
        drawOlive(olive, center, unit, sway = sway, fruitDetails = false)
        members.forEachIndexed { index, member ->
            // Each medallion moves along the round but is drawn upright: only its center turns.
            val angle = Math.toRadians(-90.0 + 360.0 * index / members.size + turn)
            val at = Offset(center.x + round * cos(angle).toFloat(), center.y + round * sin(angle).toFloat())
            drawMedallion(measurer, at, medallion, member.color, photos[member.memberId], member.initials)
        }
    }
}

@Preview(showBackground = true, widthDp = 280, heightDp = 280)
@Composable
private fun FamilyRoundPreview() {
    val names = listOf("Camille", "Inès", "Sam", "Noé", "Lou", "Jules", "Nina", "Léon", "Mia", "Hugo", "Lina", "Tom", "Zoé")
    val members = names.mapIndexed { index, name ->
        Member("m$index", name, null, null, "h${index / 4}", MemberProfile.Adult, isAdmin = false, parentIds = emptyList(), uid = null, colorIndex = index)
    }
    val family = Family(listOf(Household("h0", "Foyer Un"), Household("h1", "Foyer Deux"), Household("h2", "Foyer Trois"), Household("h3", "Foyer Quatre")), members)
    FamiliaTheme {
        Surface { RoundPicture(welcomeFruits(family, members.first()), emptyMap(), turn = 0f, sway = 0f, modifier = Modifier.size(240.dp)) }
    }
}
