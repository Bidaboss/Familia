package fr.familia.app.core.welcome

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.Fredoka
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The whole welcome, in milliseconds (spec DESIGN « animation d'ouverture », part 2). */
private const val END = 4_500f

/** Where the screen stays still when the animations are off on the phone: the circle and the words. */
private const val STILL = 3_400f

/**
 * The welcome of the first opening (spec DESIGN, 17/11, DESIGN-22): the tree of the icon grows, its
 * fruits become the family around the sun, each in the member's color with their letters, then
 * « Bienvenue dans Familia » and « Bonjour {prénom}, tu es chez toi. ». About 4.5 s, a touch anywhere
 * goes straight on. Animations off on the phone: the last picture alone, for 1.5 s.
 */
@Composable
fun WelcomeAnimation(family: Family, me: Member, onDone: () -> Unit) {
    val fruits = remember(family, me) { welcomeFruits(family, me) }
    val context = LocalContext.current
    val still = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val done by rememberUpdatedState(onDone)
    val time = remember { Animatable(if (still) STILL else 0f) }
    LaunchedEffect(still) {
        if (still) {
            delay(1_500)
        } else {
            time.animateTo(END, tween(durationMillis = END.toInt(), easing = LinearEasing))
        }
        done()
    }
    val title = stringResource(R.string.welcome_animation_title)
    val hello = stringResource(R.string.welcome_animation_hello, me.firstName)
    val skip = stringResource(R.string.welcome_animation_skip)
    WelcomePicture(
        fruits = fruits,
        time = time.value,
        title = title,
        hello = hello,
        modifier = Modifier
            .clearAndSetSemantics {
                contentDescription = "$title. $hello"
                liveRegion = LiveRegionMode.Polite
                onClick(label = skip) { done(); true }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() },
    )
}

/** The picture at [time] ms: drawn apart from the clock, for the previews. */
@Composable
internal fun WelcomePicture(fruits: List<WelcomeFruit>, time: Float, title: String, hello: String, modifier: Modifier = Modifier) {
    val sunColor = Color(0xFFF6D27A)
    val wood = Color(0xFF5A3A22)
    val leaf = Color(0xFF8A9A5B)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .alpha(1f - phase(time, 4_100f, END)),
    ) {
        // The circle shrinks on a narrow phone so that every fruit stays on screen.
        val ring: Dp = min(128f, maxWidth.value / 2f - 26f - 16f).dp
        val centerY: Dp = maxHeight * 0.40f
        Canvas(Modifier.fillMaxSize()) {
            val unit = 220.dp.toPx() / 108f
            val center = Offset(size.width / 2f, centerY.toPx())
            fun icon(x: Float, y: Float) = Offset(center.x + (x - 54f) * unit, center.y + (y - 54f) * unit)

            // The sun (0 to 350 ms), which stays.
            val sun = phase(time, 0f, 350f)
            scale(easeOutBack(sun), pivot = center) { drawCircle(sunColor.copy(alpha = sun), radius = 33f * unit, center = center) }

            // The tree fades away while the fruits leave for the circle (1.6 to 2.1 s).
            val treeAlpha = 1f - phase(time, 1_600f, 2_100f)
            if (treeAlpha > 0f) {
                val trunk = phase(time, 150f, 450f)
                if (trunk > 0f) {
                    scale(scaleX = 1f, scaleY = trunk, pivot = icon(54f, 92f)) { drawTrunk(::icon, unit, wood.copy(alpha = treeAlpha)) }
                }
                val crown = phase(time, 300f, 650f)
                if (crown > 0f) {
                    scale(easeOutBack(crown), pivot = icon(54f, 57f)) { drawCrown(::icon, unit, wood.copy(alpha = treeAlpha), leaf.copy(alpha = treeAlpha)) }
                }
                // The fruits of the icon that are nobody's go with the tree.
                TREE_SLOTS.drop(fruits.size).forEachIndexed { index, fruit ->
                    val grown = easeOutBack(phase(time, 550f + 30f * (fruits.size + index), 670f + 30f * (fruits.size + index)))
                    drawCircle(fruit.color.copy(alpha = treeAlpha), radius = fruit.radius * unit * grown, center = icon(fruit.center.x, fruit.center.y))
                }
            }

            // Each fruit: out on the tree one by one (550 to 900 ms), then to its place on the circle,
            // growing from about 22 to 52 dp and taking the member's color (1.6 to 2.5 s).
            val travel = easeInOut(phase(time, 1_600f, 2_500f))
            val tint = phase(time, 1_600f, 2_300f)
            val letters = phase(time, 2_300f, 2_600f)
            val big = 26.dp.toPx()
            fruits.forEach { fruit ->
                val start = fruit.tree?.let { icon(it.center.x, it.center.y) } ?: center
                val startRadius = (fruit.tree?.radius ?: 0f) * unit
                val order = if (fruit.tree != null) fruit.place else 13
                val grown = easeOutBack(phase(time, 550f + 30f * order, 670f + 30f * order))
                val angle = -Math.PI / 2 + 2 * Math.PI * fruit.place / fruits.size
                val end = Offset(center.x + ring.toPx() * cos(angle).toFloat(), center.y + ring.toPx() * sin(angle).toFloat())
                val at = Offset(start.x + (end.x - start.x) * travel, start.y + (end.y - start.y) * travel)
                val radius = (startRadius + (big - startRadius) * travel) * if (fruit.tree != null) grown else travel
                if (radius <= 0f) return@forEach
                drawCircle(sunColor, radius = radius + 0.9f * unit, center = at)
                drawCircle(lerp(fruit.tree?.color ?: fruit.color, fruit.color, tint), radius = radius, center = at)
                if (letters > 0f) {
                    val layout = measurer.measure(
                        fruit.initials,
                        TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Color.White.copy(alpha = letters)),
                    )
                    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
                }
            }
        }
        // The words (from 2.9 s), under the circle.
        val words = phase(time, 2_900f, 3_400f)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset(y = centerY + ring + 26.dp + 32.dp + (16f * (1f - words)).dp)
                .alpha(words),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 30.sp),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                hello,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The trunk and the roots of the icon. */
private fun DrawScope.drawTrunk(icon: (Float, Float) -> Offset, unit: Float, wood: Color) {
    val roots = listOf(30f to 84f, 40f to 90f, 54f to 92f, 68f to 90f, 78f to 84f, 24f to 74f, 84f to 74f)
    val bends = listOf(42f to 72.8f, 47f to 74f, 54f to 74.4f, 61f to 74f, 66f to 72.8f, 39f to 70.8f, 69f to 70.8f)
    roots.zip(bends).forEach { (end, bend) -> curve(icon(54f, 70f), icon(bend.first, bend.second), icon(end.first, end.second), wood, 2.4f * unit) }
    val trunk = Path().apply {
        icon(49f, 72f).let { moveTo(it.x, it.y) }
        icon(51f, 64f).let { c -> icon(50f, 56f).let { e -> quadraticTo(c.x, c.y, e.x, e.y) } }
        icon(58f, 56f).let { lineTo(it.x, it.y) }
        icon(57f, 64f).let { c -> icon(59f, 72f).let { e -> quadraticTo(c.x, c.y, e.x, e.y) } }
        close()
    }
    drawPath(trunk, wood)
}

/** The branches, the twigs and the leaves of the icon. */
private fun DrawScope.drawCrown(icon: (Float, Float) -> Offset, unit: Float, wood: Color, leaf: Color) {
    val branches = listOf(
        Triple(43.48f to 61.47f, 38.96f to 55.47f, 2.4f), Triple(43.48f to 50.53f, 38.96f to 44.53f, 2.4f),
        Triple(46.41f to 42.89f, 44.82f to 36.89f, 2.4f), Triple(61.59f to 42.89f, 63.18f to 36.89f, 2.4f),
        Triple(64.52f to 50.53f, 69.04f to 44.53f, 2.4f), Triple(64.52f to 61.47f, 69.04f to 55.47f, 2.4f),
    )
    branches.forEach { (bend, end, width) -> curve(icon(54f, 57f), icon(bend.first, bend.second), icon(end.first, end.second), wood, width * unit) }
    val twigs = listOf(
        Triple(38.96f to 55.47f, 32.67f to 40.5f, 32.37f to 38.5f), Triple(38.96f to 44.53f, 37.73f to 30.37f, 42.5f to 28.37f),
        Triple(44.82f to 36.89f, 52.41f to 27.5f, 54f to 25.5f), Triple(63.18f to 36.89f, 67.34f to 30.37f, 65.5f to 28.37f),
        Triple(69.04f to 44.53f, 75.33f to 40.5f, 75.63f to 38.5f),
    )
    twigs.forEach { (from, bend, end) -> curve(icon(from.first, from.second), icon(bend.first, bend.second), icon(end.first, end.second), wood, 1.68f * unit) }
    val leaves = listOf(
        Triple(33f, 58f, -30f), Triple(75f, 58f, 30f), Triple(28f, 44f, -60f), Triple(80f, 44f, 60f), Triple(36f, 24f, -20f),
        Triple(72f, 24f, 20f), Triple(54f, 32f, 0f), Triple(47f, 28f, -15f), Triple(61f, 28f, 15f), Triple(24f, 52f, -80f),
        Triple(84f, 52f, 80f), Triple(44f, 58f, -40f), Triple(64f, 58f, 40f),
    )
    leaves.forEach { (x, y, degrees) ->
        val at = icon(x, y)
        rotate(degrees, pivot = at) {
            drawOval(leaf, topLeft = Offset(at.x - 2.2f * unit, at.y - 4f * unit), size = androidx.compose.ui.geometry.Size(4.4f * unit, 8f * unit))
        }
    }
}

private fun DrawScope.curve(from: Offset, bend: Offset, to: Offset, color: Color, width: Float) {
    val path = Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(bend.x, bend.y, to.x, to.y)
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
}

/** How far [time] is between [start] and [end], from 0 to 1. */
internal fun phase(time: Float, start: Float, end: Float): Float = ((time - start) / (end - start)).coerceIn(0f, 1f)

/** A light bounce at the end (the « léger rebond » of the spec). */
private fun easeOutBack(p: Float): Float {
    if (p <= 0f) return 0f
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val q = p - 1f
    return 1f + c3 * q * q * q + c1 * q * q
}

private fun easeInOut(p: Float): Float = if (p < 0.5f) 2f * p * p else 1f - (-2f * p + 2f).let { it * it } / 2f

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun WelcomeTreePreview() = WelcomePreview(time = 900f)

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun WelcomeCirclePreview() = WelcomePreview(time = STILL)

@Composable
private fun WelcomePreview(time: Float) {
    val names = listOf("Camille", "Inès", "Sam", "Noé", "Lou", "Jules", "Nina", "Léon", "Mia", "Hugo", "Lina", "Tom", "Zoé")
    val members = names.mapIndexed { index, name ->
        Member("m$index", name, null, null, "h${index / 4}", MemberProfile.Adult, isAdmin = false, parentIds = emptyList(), uid = null, colorIndex = index)
    }
    val family = Family(listOf(Household("h0", "Foyer Un"), Household("h1", "Foyer Deux"), Household("h2", "Foyer Trois"), Household("h3", "Foyer Quatre")), members)
    FamiliaTheme {
        Surface {
            WelcomePicture(welcomeFruits(family, members.first()), time, "Bienvenue dans Familia", "Bonjour Camille, tu es chez toi.")
        }
    }
}
