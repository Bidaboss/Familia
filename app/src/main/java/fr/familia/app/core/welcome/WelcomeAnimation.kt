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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.TextStyle
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

/**
 * How much slower the clock runs than the steps below, which keep the timings of the spec DESIGN
 * (« animation d'ouverture », part 2). Kévin, 18/11: « 8 s, tout ralenti », every step in proportion.
 */
private const val SLOW = 1.6f

/** Where the picture is complete and stays still, on the timings of the spec: the circle and the words. */
private const val STILL = 3_400f

/**
 * When the last picture starts to fade, in real milliseconds: it is held 2 s on the family around
 * the sun and « Bonjour {prénom}, tu es chez toi. » (Kévin, 18/11), which also leaves the app time to load.
 */
private const val FADE = STILL * SLOW + 2_000f

/** The whole welcome, in real milliseconds: 8 s. */
private const val END = 8_000f

/** How long the last picture alone stays when the animations are off on the phone. */
private const val STILL_HOLD = 3_000L

/**
 * The welcome of each opening in « Longue » (spec DESIGN, 17/11, DESIGN-22): the tree of the icon grows, its
 * fruits become the family around the sun, each in the member's color with their letters, then
 * « Bienvenue dans Familia » and « Bonjour {prénom}, tu es chez toi. ». 8 s in all, the last picture
 * held 2 s before it fades; a touch anywhere goes straight on. Animations off on the phone: the last
 * picture alone, for 3 s.
 */
@Composable
fun WelcomeAnimation(family: Family, me: Member, photos: Map<String, ImageBitmap> = emptyMap(), onDone: () -> Unit) {
    val fruits = remember(family, me) { welcomeFruits(family, me) }
    val context = LocalContext.current
    val still = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val done by rememberUpdatedState(onDone)
    val time = remember { Animatable(if (still) STILL * SLOW else 0f) }
    LaunchedEffect(still) {
        if (still) {
            delay(STILL_HOLD)
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
        photos = photos,
        time = pictureTime(time.value),
        fade = welcomeFade(time.value),
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

/**
 * The picture at [time] ms on the timings of the spec, faded by [fade] from 0 to 1: drawn apart
 * from the clock, for the previews.
 */
@Composable
internal fun WelcomePicture(
    fruits: List<WelcomeFruit>,
    time: Float,
    title: String,
    hello: String,
    modifier: Modifier = Modifier,
    photos: Map<String, ImageBitmap> = emptyMap(),
    fade: Float = 0f,
) {
    val sunColor = OLIVE_SUN_COLOR
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .alpha(1f - fade),
    ) {
        // The circle shrinks on a narrow phone so that every fruit stays on screen.
        val ring: Dp = min(128f, maxWidth.value / 2f - 26f - 16f).dp
        val centerY: Dp = maxHeight * 0.40f
        val olive = rememberOlivePaths()
        Canvas(Modifier.fillMaxSize()) {
            val unit = 220.dp.toPx() / 108f
            val center = Offset(size.width / 2f, centerY.toPx())
            fun icon(x: Float, y: Float) = Offset(center.x + (x - 54f) * unit, center.y + (y - 54f) * unit)

            // The olive tree of the icon (DESIGN, 18/11), in the order of the start screen: the sun
            // and its thin red ring (0 to 350 ms), which stay, the roots (150 to 400 ms), the trunk
            // (250 to 500 ms), then the leaves with a light bounce (350 to 700 ms). The tree fades
            // away while the fruits leave for the circle (1.6 to 2.1 s).
            val treeAlpha = 1f - phase(time, 1_600f, 2_100f)
            drawOlive(
                olive,
                center,
                unit,
                sun = easeOutBack(phase(time, 0f, 350f)),
                roots = phase(time, 150f, 400f),
                trunk = phase(time, 250f, 500f),
                crown = easeOutBack(phase(time, 350f, 700f)),
                treeAlpha = treeAlpha,
            )
            if (treeAlpha > 0f) {
                // The fruits of the icon that are nobody's go with the tree.
                TREE_SLOTS.drop(fruits.size).forEachIndexed { index, fruit ->
                    val grown = easeOutBack(phase(time, 550f + 30f * (fruits.size + index), 670f + 30f * (fruits.size + index)))
                    drawCircle(fruit.color.copy(alpha = treeAlpha), radius = fruit.radius * unit * grown, center = icon(fruit.center.x, fruit.center.y))
                }
            }

            // Each fruit is a member (règle de Kévin, 18/11): their photo, or their letters on their
            // color, from the moment it comes out on the tree (550 to 900 ms). Then it goes to its
            // place on the circle, growing from about 20 to 52 dp (1.6 to 2.5 s).
            val travel = easeInOut(phase(time, 1_600f, 2_500f))
            val big = 26.dp.toPx()
            fruits.forEach { fruit ->
                val start = fruit.tree?.let { icon(it.center.x, it.center.y) } ?: center
                val startRadius = (fruit.tree?.radius ?: 0f) * unit
                val order = if (fruit.tree != null) fruit.place else 13
                val out = phase(time, 550f + 30f * order, 670f + 30f * order)
                val angle = -Math.PI / 2 + 2 * Math.PI * fruit.place / fruits.size
                val end = Offset(center.x + ring.toPx() * cos(angle).toFloat(), center.y + ring.toPx() * sin(angle).toFloat())
                val at = Offset(start.x + (end.x - start.x) * travel, start.y + (end.y - start.y) * travel)
                val radius = (startRadius + (big - startRadius) * travel) * if (fruit.tree != null) easeOutBack(out) else travel
                if (radius <= 0f) return@forEach
                drawCircle(sunColor, radius = radius + 0.9f * unit, center = at)
                val shown = if (fruit.tree != null) out else travel
                drawMedallion(measurer, at, radius, fruit.color, photos[fruit.memberId], fruit.initials, shown)
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

/** Where the picture is, on the timings of the spec, at [real] ms of the welcome. */
internal fun pictureTime(real: Float): Float = min(real / SLOW, STILL)

/** How far the last picture has faded at [real] ms of the welcome, from 0 to 1. */
internal fun welcomeFade(real: Float): Float = phase(real, FADE, END)

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
