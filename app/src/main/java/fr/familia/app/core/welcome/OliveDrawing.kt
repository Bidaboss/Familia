package fr.familia.app.core.welcome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import fr.familia.app.core.theme.Fredoka
import kotlin.math.min
import kotlin.math.roundToInt

internal val OLIVE_SUN_COLOR = Color(0xFFF6D27A)
internal val OLIVE_WOOD = Color(0xFF5A3A22)

/** The small leaves of the fruits close [OLIVE_LEAVES]; [OLIVE_TWIGS] thinner than this are the stalks. */
private const val FRUIT_LEAVES = 13
private const val TWIG_WIDTH = 1.68f

/** The paths of [WelcomeOlive], read once. */
internal class OlivePaths {
    val fansRed: List<Path> = OLIVE_FANS_RED.map(::toPath)
    val fansYellow: List<Path> = OLIVE_FANS_YELLOW.map(::toPath)
    val roots: Path = toPath(OLIVE_ROOTS)
    val trunk: Path = toPath(OLIVE_TRUNK)
    val branches: Path = toPath(OLIVE_BRANCHES)
    val twigs: List<Pair<Float, Path>> = OLIVE_TWIGS.map { (width, d) -> width to toPath(d) }
    val sun: Path = Path().apply { addOval(Rect(Offset(54f, 54f), OLIVE_SUN_RADIUS)) }

    private fun toPath(d: String): Path = PathParser().parsePathString(d).toPath()
}

@Composable
internal fun rememberOlivePaths(): OlivePaths = remember { OlivePaths() }

/**
 * « L'olivier au soleil » (DESIGN, 18/11) around [center], [unit] pixels for one unit of the 108
 * grid of the icon, without its fruits. Each part grows from 0 to 1, in the order of the spec:
 * [sun], [roots], [trunk], then the leaves ([crown], which may bounce past 1). [sway] tilts the
 * leaves by that many degrees. [treeAlpha] fades all but the sun and its thin red ring.
 * [fruitDetails] keeps the stalks and the small leaves of the fruits, for a tree that has fruits.
 */
internal fun DrawScope.drawOlive(
    paths: OlivePaths,
    center: Offset,
    unit: Float,
    sun: Float = 1f,
    roots: Float = 1f,
    trunk: Float = 1f,
    crown: Float = 1f,
    sway: Float = 0f,
    treeAlpha: Float = 1f,
    fruitDetails: Boolean = true,
) {
    if (sun > 0f) {
        scale(sun, pivot = center) {
            drawCircle(OLIVE_SUN_COLOR, radius = OLIVE_SUN_RADIUS * unit, center = center, alpha = sun.coerceIn(0f, 1f))
            drawCircle(
                OLIVE_RING_COLOR,
                radius = OLIVE_RING_RADIUS * unit,
                center = center,
                alpha = sun.coerceIn(0f, 1f),
                style = Stroke(width = OLIVE_RING_WIDTH * unit),
            )
        }
    }
    if (treeAlpha <= 0f) return
    withTransform({
        translate(center.x - 54f * unit, center.y - 54f * unit)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        if (roots > 0f) {
            clipPath(paths.sun) {
                scale(roots, pivot = Offset(OLIVE_FAN_X, OLIVE_FAN_Y)) {
                    // The pans are stored twice as high: squashed back, their round gradient becomes the oval one of the SVG.
                    scale(scaleX = 1f, scaleY = 0.5f, pivot = Offset(OLIVE_FAN_X, OLIVE_FAN_Y)) {
                        paths.fansRed.forEach { drawPath(it, fanBrush(FAN_RED), alpha = treeAlpha) }
                        paths.fansYellow.forEach { drawPath(it, fanBrush(FAN_YELLOW), alpha = treeAlpha) }
                    }
                    drawPath(paths.roots, OLIVE_WOOD, alpha = treeAlpha)
                }
            }
        }
        if (trunk > 0f) {
            scale(scaleX = 1f, scaleY = trunk, pivot = Offset(54f, 72.5f)) {
                drawPath(paths.trunk, OLIVE_WOOD, alpha = treeAlpha)
                drawPath(paths.branches, OLIVE_WOOD, alpha = treeAlpha, style = Stroke(width = 2.4f, cap = StrokeCap.Round))
            }
        }
        if (crown > 0f) {
            rotate(sway, pivot = Offset(54f, 60f)) {
                scale(crown, pivot = Offset(54f, 45f)) {
                    paths.twigs.forEach { (width, path) ->
                        if (fruitDetails || width >= TWIG_WIDTH) {
                            drawPath(path, OLIVE_WOOD, alpha = treeAlpha, style = Stroke(width = width, cap = StrokeCap.Round))
                        }
                    }
                    val leaves = OLIVE_LEAVES.size / 6 - if (fruitDetails) 0 else FRUIT_LEAVES
                    for (i in 0 until leaves) {
                        val x = OLIVE_LEAVES[i * 6]
                        val y = OLIVE_LEAVES[i * 6 + 1]
                        val rx = OLIVE_LEAVES[i * 6 + 2]
                        val ry = OLIVE_LEAVES[i * 6 + 3]
                        rotate(OLIVE_LEAVES[i * 6 + 4], pivot = Offset(x, y)) {
                            drawOval(
                                OLIVE_LEAF_COLORS[OLIVE_LEAVES[i * 6 + 5].toInt()],
                                topLeft = Offset(x - rx, y - ry),
                                size = Size(2 * rx, 2 * ry),
                                alpha = treeAlpha,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val FAN_RED = Color(0xFFAA151B) to floatArrayOf(0f, 0.26f, 0.4f)
private val FAN_YELLOW = Color(0xFFF1BF00) to floatArrayOf(0f, 0.42f, 0.6f)

private fun fanBrush(fan: Pair<Color, FloatArray>): Brush = Brush.radialGradient(
    0.2f to fan.first.copy(alpha = fan.second[0]),
    0.6f to fan.first.copy(alpha = fan.second[1]),
    1f to fan.first.copy(alpha = fan.second[2]),
    center = Offset(OLIVE_FAN_X, OLIVE_FAN_Y),
    radius = OLIVE_FAN_RADIUS,
)

/**
 * A member drawn in the tree (règle de Kévin, 18/11): their profile photo, round, in a white ring
 * and an outline of their [color]; without a photo, their [initials] in white on their color.
 */
internal fun DrawScope.drawMedallion(
    measurer: TextMeasurer,
    center: Offset,
    radius: Float,
    color: Color,
    photo: ImageBitmap?,
    initials: String,
    alpha: Float = 1f,
) {
    if (radius <= 0f) return
    drawCircle(color, radius = radius, center = center, alpha = alpha)
    if (photo != null) {
        val outline = radius * 0.12f
        drawCircle(Color.White, radius = radius - outline, center = center, alpha = alpha)
        drawPhoto(photo, center, radius - outline - radius * 0.1f, alpha)
    } else if (radius >= 3f) {
        val size = radius * if (initials.length > 1) 0.8f else 1f
        val layout = measurer.measure(
            initials,
            TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = size.toSp(), color = Color.White.copy(alpha = alpha)),
        )
        drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
    }
}

/** [photo] cropped to its center square, in a circle of [radius] around [center]. */
internal fun DrawScope.drawPhoto(photo: ImageBitmap, center: Offset, radius: Float, alpha: Float) {
    if (radius <= 0f) return
    val side = min(photo.width, photo.height)
    val circle = Path().apply { addOval(Rect(center, radius)) }
    clipPath(circle) {
        drawImage(
            image = photo,
            srcOffset = IntOffset((photo.width - side) / 2, (photo.height - side) / 2),
            srcSize = IntSize(side, side),
            dstOffset = IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt()),
            dstSize = IntSize((2 * radius).roundToInt(), (2 * radius).roundToInt()),
            alpha = alpha,
        )
    }
}
