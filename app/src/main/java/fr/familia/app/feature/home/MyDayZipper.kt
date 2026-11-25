package fr.familia.app.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.feature.trips.list.animationsOff
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * La fermeture éclair de « Ma journée » (brief §7, PR 8 du brief). Un curseur doré de tirette (style
 * fermeture de jean) dépasse du haut de la gazette, avec la pastille rouge des actions en attente.
 * On le tire vers le bas : la gazette s'ouvre en V (ruban denim, couture orange, dents dorées) et
 * Ma journée se voit derrière. Au-delà de 35 % de la hauteur, l'ouverture se termine seule ; en
 * dessous, elle revient. Ouverte, la fermeture est en bas, curseur retourné, sans pastille : on la
 * tire vers le haut (en deçà de 65 %, elle se referme). Un toucher fait l'animation complète. La
 * ligne de résumé de l'en-tête et le ✕ restent l'accès sans geste ; animations réduites : pas de
 * geste animé, Ma journée s'ouvre et se ferme d'un coup.
 */

/** The numbers of the brief §7, apart so they are tested without Android. */
internal object Zipper {
    /** Released past this share of the height, an opening goes on by itself. */
    const val OPEN_AT = 0.35f

    /** Released above this share of the height, a closing goes on by itself. */
    const val CLOSE_AT = 0.65f
    const val SETTLE_MS = 260
    const val BACK_MS = 200
    const val TAP_MS = 520

    /** Half the width of the V at the top edge, for each pixel of the slider's depth. */
    const val V_SLOPE = 0.28f

    /** Where a released slider goes: [from] is where the gesture started (0 closed, 1 open), [at] where it was let go. */
    fun settle(from: Float, at: Float): Float =
        if (from < 0.5f) (if (at > OPEN_AT) 1f else 0f) else (if (at < CLOSE_AT) 0f else 1f)

    /** How long the slider takes from [at] to [target] once let go: it finishes, or it comes back. */
    fun settleMs(from: Float, target: Float): Int = if (target == from.roundToNearest()) BACK_MS else SETTLE_MS

    private fun Float.roundToNearest(): Float = if (this < 0.5f) 0f else 1f

    /**
     * Half the width of the opening at the top edge for a slider at [progress] of [height]: 0,28 × its
     * depth (brief §7), widened over the second half so that the V reaches the whole screen at the end.
     */
    fun halfWidth(progress: Float, height: Float, width: Float): Float =
        V_SLOPE * progress * height + ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f) * width

    /** The red badge: nothing at 0, « 9+ » past 9. */
    fun badge(count: Int): String? = when {
        count <= 0 -> null
        count > 9 -> "9+"
        else -> count.toString()
    }
}

/** The opening of the gazette: a V whose point is the slider, the whole screen once open. */
private class ZipperShape(private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (progress >= 1f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
        val depth = progress * size.height
        val half = Zipper.halfWidth(progress, size.height, size.width)
        val cx = size.width / 2
        return Outline.Generic(
            Path().apply {
                moveTo(cx - half, 0f)
                lineTo(cx + half, 0f)
                lineTo(cx, depth)
                close()
            },
        )
    }
}

private val Denim = Color(0xFF41607F)
private val Stitch = Color(0xFFE3B25B)
private val Brass = Color(0xFFD4A537)
private val BrassDark = Color(0xFF8A6A1F)
private val BadgeRed = Color(0xFFD8452F)

/**
 * The gazette with « Ma journée » behind its zipper. [open] is the state kept by the Accueil;
 * [onOpen] changes it, by a gesture, a touch, the line of the header or the ✕. [waiting] feeds the
 * red badge of the slider.
 */
@Composable
internal fun MyDayZipper(
    open: Boolean,
    onOpen: (Boolean) -> Unit,
    waiting: Int,
    gazette: @Composable () -> Unit,
    myDay: @Composable () -> Unit,
    /** Screenshots only: the slider held half-way, to see the V. */
    startAt: Float? = null,
) {
    val context = LocalContext.current
    val still = remember { animationsOff(context) }
    val progress = remember { Animatable(startAt ?: if (open) 1f else 0f) }
    val scope = rememberCoroutineScope()
    // How the next change of [open] moves: a touch (the whole animation) or a release (the end of it).
    var nextMs by remember { mutableIntStateOf(Zipper.TAP_MS) }
    var dragFrom by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(open) {
        if (startAt != null) return@LaunchedEffect
        val target = if (open) 1f else 0f
        if (progress.value != target) dragFrom = 1f - target
        if (still) progress.snapTo(target) else progress.animateTo(target, tween(nextMs, easing = FastOutSlowInEasing))
        nextMs = Zipper.TAP_MS
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val p = progress.value
        Box(Modifier.fillMaxSize().then(if (p >= 1f) Modifier.clearAndSetSemantics { } else Modifier)) { gazette() }
        if (p > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (p < 1f) Modifier.clip(ZipperShape(p)) else Modifier)
                    .then(if (p < 1f) Modifier.clearAndSetSemantics { } else Modifier),
            ) { myDay() }
        }
        if (p > 0f && p < 1f) {
            Canvas(Modifier.fillMaxSize()) { drawRibbons(p) }
        }
        val drag = rememberDraggableState { delta ->
            scope.launch { progress.snapTo((progress.value + delta / height).coerceIn(0f, 1f)) }
        }
        val toggle = { onOpen(!open) }
        val label = if (open) {
            stringResource(R.string.home_zipper_close)
        } else {
            val count = waiting.coerceAtLeast(0)
            if (count == 0) {
                stringResource(R.string.home_zipper_open)
            } else {
                stringResource(R.string.home_zipper_open_waiting, pluralStringResource(R.plurals.home_myday_to_answer, count, count))
            }
        }
        val density = LocalDensity.current
        val sliderSize = 56.dp
        val sliderPx = with(density) { sliderSize.toPx() }
        // At rest it hangs from the top edge; open, it sits at the bottom, turned over.
        val y = (p * height - (if (p >= 1f) sliderPx else 0f)).coerceIn(0f, height - sliderPx)
        val turned = p >= 1f || (p > 0f && dragFrom >= 0.5f)
        val swing = if (!still && p == 0f && waiting > 0) {
            rememberInfiniteTransition(label = "zipper").animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = InfiniteRepeatableSpec(
                    keyframes {
                        durationMillis = 4000
                        0f at 0
                        7f at 180
                        -6f at 380
                        3f at 560
                        0f at 740
                        0f at 4000
                    },
                    RepeatMode.Restart,
                ),
                label = "swing",
            ).value
        } else {
            0f
        }
        val measurer = rememberTextMeasurer()
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, y.roundToInt()) }
                .size(sliderSize)
                .zipperSemantics(label, toggle)
                .draggable(
                    drag,
                    Orientation.Vertical,
                    onDragStarted = { dragFrom = progress.value },
                    onDragStopped = {
                        val target = Zipper.settle(dragFrom, progress.value)
                        nextMs = Zipper.settleMs(dragFrom, target)
                        val wanted = target >= 1f
                        if (wanted != open) {
                            onOpen(wanted)
                        } else {
                            val ms = nextMs
                            nextMs = Zipper.TAP_MS
                            if (still) progress.snapTo(target) else progress.animateTo(target, tween(ms, easing = FastOutSlowInEasing))
                        }
                    },
                )
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = toggle),
        ) {
            Canvas(Modifier.fillMaxSize().rotate(if (turned) 180f + swing else swing)) {
                drawSlider(if (turned) null else Zipper.badge(waiting), measurer, turned)
            }
        }
    }
}

/** The slider is a button « Ouvrir Ma journée » or « Fermer Ma journée », with its action for TalkBack, early in the order. */
private fun Modifier.zipperSemantics(label: String, toggle: () -> Unit): Modifier =
    clearAndSetSemantics {
        contentDescription = label
        role = Role.Button
        traversalIndex = -1f
        onClick(label = label) { toggle(); true }
        customActions = listOf(CustomAccessibilityAction(label) { toggle(); true })
    }

/** The two ribbons of the V: denim, an orange stitch, and golden teeth one side then the other. */
private fun DrawScope.drawRibbons(progress: Float) {
    val depth = progress * size.height
    val half = Zipper.halfWidth(progress, size.height, size.width)
    val cx = size.width / 2
    val band = 12.dp.toPx()
    val tooth = 5.dp.toPx()
    val step = 9.dp.toPx()
    listOf(-1f, 1f).forEachIndexed { side, sign ->
        val top = Offset(cx + sign * half, 0f)
        val point = Offset(cx, depth)
        val dx = point.x - top.x
        val dy = point.y - top.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        // Outward, on the side of the gazette.
        val along = Offset(dx / length, dy / length)
        // Perpendicular to the edge, away from the opening: on the side of the gazette.
        val out = Offset(sign * along.y, -sign * along.x)
        val shift = out * (band / 2)
        drawLine(Denim, top + shift, point + shift, strokeWidth = band, cap = StrokeCap.Butt)
        drawLine(
            Stitch, top + shift, point + shift, strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
        var t = if (side == 0) 0f else step / 2
        while (t < length) {
            val at = top + along * t
            drawLine(Brass, at, at + out * tooth, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            t += step
        }
    }
}

/** The slider of a jean zipper: its body, its pull with a hole, and the red badge when something waits. */
private fun DrawScope.drawSlider(badge: String?, measurer: TextMeasurer, turned: Boolean) {
    val w = size.width
    val body = Size(w * 0.46f, w * 0.30f)
    val bodyAt = Offset((w - body.width) / 2, 0f)
    drawRoundRect(Brass, bodyAt, body, CornerRadius(6.dp.toPx()))
    drawRoundRect(BrassDark, bodyAt, body, CornerRadius(6.dp.toPx()), style = Stroke(1.5.dp.toPx()))
    val pull = Size(w * 0.30f, w * 0.52f)
    val pullAt = Offset((w - pull.width) / 2, body.height - 2.dp.toPx())
    drawRoundRect(Brass, pullAt, pull, CornerRadius(pull.width / 2))
    drawRoundRect(BrassDark, pullAt, pull, CornerRadius(pull.width / 2), style = Stroke(1.5.dp.toPx()))
    drawRoundRect(
        BrassDark,
        Offset(w / 2 - pull.width * 0.18f, pullAt.y + pull.height * 0.52f),
        Size(pull.width * 0.36f, pull.height * 0.30f),
        CornerRadius(pull.width * 0.18f),
    )
    if (badge != null && !turned) {
        val r = 10.dp.toPx()
        val center = Offset(bodyAt.x + body.width + r * 0.4f, r)
        drawCircle(BadgeRed, r, center)
        drawCircle(Color.White, r, center, style = Stroke(1.5.dp.toPx()))
        val text = measurer.measure(badge, TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        drawText(text, topLeft = center - Offset(text.size.width / 2f, text.size.height / 2f))
    }
}
