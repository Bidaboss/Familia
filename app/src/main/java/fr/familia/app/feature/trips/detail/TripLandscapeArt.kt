package fr.familia.app.feature.trips.detail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import fr.familia.app.core.model.TripLandscape
import kotlin.math.max

/**
 * The colors of one landscape (récit 81, DESIGN-53): the sky, the sun, the three layers from far to
 * near, the path and its dashes. [near] is also the ground under the card of the day. Kept the
 * same in light and dark, like the inside of a trip.
 */
data class LandscapePalette(
    val sky: Color,
    val sun: Color,
    val far: Color,
    val mid: Color,
    val near: Color,
    val road: Color,
    val dash: Color,
)

val TripLandscape.palette: LandscapePalette
    get() = when (this) {
        TripLandscape.Dunes -> LandscapePalette(
            TripPathColors.Sky, TripPathColors.Sun, TripPathColors.DuneFar, TripPathColors.DuneMid, TripPathColors.DuneNear,
            TripPathColors.Road, TripPathColors.RoadDash,
        )
        TripLandscape.Sea -> LandscapePalette(rgb(0xD3EEF6), TripPathColors.Sun, rgb(0x7CC6DA), rgb(0x4FA9C4), rgb(0xF3DDB0), Color.White, rgb(0xB08A5B))
        TripLandscape.Mountain -> LandscapePalette(rgb(0xE2EDF8), TripPathColors.Sun, rgb(0xAFC2D8), rgb(0x8DBA84), rgb(0x6A9B5A), TripPathColors.Road, rgb(0x6B4A2E))
        TripLandscape.Country -> LandscapePalette(rgb(0xE3F2F9), TripPathColors.Sun, rgb(0xD9E7A2), rgb(0xB5CF6E), rgb(0x88B058), TripPathColors.Road, rgb(0x7A5A35))
        TripLandscape.City -> LandscapePalette(rgb(0xFBE3D4), rgb(0xF9B99A), rgb(0xCDBFF0), rgb(0xA898DC), rgb(0xE8DED2), Color.White, rgb(0x8A8178))
        TripLandscape.Snow -> LandscapePalette(rgb(0xDCE7F3), rgb(0xFFF2C2), rgb(0xF4F8FC), rgb(0xE6EEF6), Color.White, rgb(0xD2DEEB), rgb(0x7A8CA3))
    }

private fun rgb(value: Long) = Color(0xFF000000 or value)

/** The width and height of the drawing of reference (paysages-dessins.py). */
private const val W = 390f
private const val H = 300f

/**
 * Draws [landscape] over the whole of this scope, without the path: the sky, the sun, the three
 * layers and the decor of paysages-dessins.py, scaled to cover the scope and centered (the
 * « slice » of the maquette).
 */
fun DrawScope.drawLandscape(landscape: TripLandscape) {
    val p = landscape.palette
    drawRect(p.sky)
    val scale = max(size.width / W, size.height / H)
    val dx = (size.width - W * scale) / 2
    val dy = (size.height - H * scale) / 2
    withTransform({
        translate(dx, dy)
        scale(scale, scale, Offset.Zero)
    }) {
        drawCircle(p.sun, 26f, Offset(318f, 44f))
        when (landscape) {
            TripLandscape.Sea -> sea(p)
            TripLandscape.Mountain -> peaks(p)
            TripLandscape.City -> city(p)
            else -> rolling(p, rows = landscape == TripLandscape.Country)
        }
        decor(landscape)
    }
    // Below the reference drawing (a tall canvas), the near ground goes on to the bottom.
    val bottom = dy + H * scale
    if (bottom < size.height) drawRect(p.near, Offset(0f, bottom - 1f), Size(size.width, size.height - bottom + 1f))
}

/** « M x0 y0 Q c p T p2 T p3 … V300 H0 z »: a smooth layer down to the bottom. */
private fun layer(start: Offset, control: Offset, vararg points: Offset): Path = Path().apply {
    moveTo(start.x, start.y)
    var c = control
    var from = start
    points.forEach { to ->
        quadraticTo(c.x, c.y, to.x, to.y)
        c = Offset(2 * to.x - c.x, 2 * to.y - c.y)
        from = to
    }
    lineTo(from.x, H)
    lineTo(0f, H)
    close()
}

private fun DrawScope.rolling(p: LandscapePalette, rows: Boolean) {
    drawPath(layer(Offset(0f, 110f), Offset(90f, 70f), Offset(190f, 100f), Offset(390f, 85f)), p.far)
    drawPath(layer(Offset(0f, 180f), Offset(120f, 140f), Offset(240f, 170f), Offset(390f, 160f)), p.mid)
    if (rows) {
        repeat(12) { i ->
            drawLine(rgb(0x9DBB5C).copy(alpha = 0.7f), Offset(-20f + i * 40, 300f), Offset(60f + i * 28, 175f), strokeWidth = 3f)
        }
    }
    drawPath(layer(Offset(0f, 250f), Offset(140f, 215f), Offset(260f, 240f), Offset(390f, 232f)), p.near)
}

private fun DrawScope.sea(p: LandscapePalette) {
    val waves = (1..5).map { Offset(it * 80f, 110f) }.toTypedArray()
    drawPath(layer(Offset(0f, 110f), Offset(40f, 104f), *waves), p.far)
    val waves2 = (1..5).map { Offset(it * 80f, 170f) }.toTypedArray()
    drawPath(layer(Offset(0f, 170f), Offset(40f, 162f), *waves2), p.mid)
    drawPath(layer(Offset(0f, 235f), Offset(120f, 200f), Offset(240f, 225f), Offset(390f, 215f)), p.near)
}

private fun DrawScope.peaks(p: LandscapePalette) {
    drawPath(
        polygon(0f to 140f, 60f to 70f, 110f to 125f, 170f to 50f, 240f to 130f, 300f to 60f, 390f to 140f, 390f to H, 0f to H),
        p.far,
    )
    // Snow on the tops.
    listOf(
        polygon(152f to 72f, 170f to 50f, 188f to 72f, 178f to 68f, 170f to 76f, 162f to 68f),
        polygon(284f to 80f, 300f to 60f, 316f to 80f, 306f to 76f, 300f to 84f, 292f to 76f),
        polygon(52f to 80f, 60f to 70f, 69f to 81f),
    ).forEach { drawPath(it, Color.White) }
    drawPath(layer(Offset(0f, 190f), Offset(120f, 140f), Offset(240f, 175f), Offset(390f, 165f)), p.mid)
    drawPath(layer(Offset(0f, 255f), Offset(140f, 220f), Offset(260f, 245f), Offset(390f, 235f)), p.near)
}

private fun DrawScope.city(p: LandscapePalette) {
    val window = rgb(0xFFF6DA).copy(alpha = 0.8f)
    listOf(
        Triple(0f, 40f, 90f), Triple(36f, 30f, 130f), Triple(70f, 46f, 70f), Triple(118f, 28f, 150f), Triple(150f, 50f, 100f),
        Triple(204f, 34f, 120f), Triple(240f, 50f, 80f), Triple(292f, 30f, 140f), Triple(326f, 64f, 95f),
    ).forEach { (x, w, h) ->
        drawRect(p.far, Offset(x, 170f - h), Size(w, h + 20f))
        var wy = 170f - h + 10f
        while (wy < 170f) {
            var wx = x + 6f
            while (wx < x + w - 6f) {
                drawRect(window, Offset(wx, wy), Size(5f, 7f))
                wx += 12f
            }
            wy += 18f
        }
    }
    listOf(
        Triple(10f, 50f, 30f), Triple(70f, 40f, 55f), Triple(120f, 60f, 25f), Triple(190f, 44f, 45f), Triple(250f, 56f, 30f), Triple(316f, 60f, 50f),
    ).forEach { (x, w, h) ->
        drawRoundRect(p.mid, Offset(x, 215f - h), Size(w, h + 20f), androidx.compose.ui.geometry.CornerRadius(4f))
    }
    drawRect(p.near, Offset(0f, 240f), Size(W, 60f))
}

private fun polygon(vararg points: Pair<Float, Float>): Path = Path().apply {
    moveTo(points[0].first, points[0].second)
    points.drop(1).forEach { (x, y) -> lineTo(x, y) }
    close()
}

/** A fir tree of the maquette: three steps of green and a small trunk, [s] its size. */
private fun DrawScope.fir(x: Float, y: Float, s: Float, color: Color) {
    drawPath(
        polygon(
            x to y - 34 * s, x + 14 * s to y - 12 * s, x + 8 * s to y - 12 * s, x + 18 * s to y + 2 * s,
            x - 18 * s to y + 2 * s, x - 8 * s to y - 12 * s, x - 14 * s to y - 12 * s,
        ),
        color,
    )
    drawRect(rgb(0x6B4A2E), Offset(x - 2 * s, y + 2 * s), Size(4 * s, 6 * s))
}

private fun DrawScope.decor(landscape: TripLandscape) {
    when (landscape) {
        TripLandscape.Dunes -> {
            drawRect(Color.White, Offset(250f, 62f), Size(18f, 44f))
            drawPath(polygon(247f to 62f, 259f to 48f, 271f to 62f), TripPathColors.Roof)
            drawRect(Color.White, Offset(276f, 78f), Size(30f, 28f))
        }
        TripLandscape.Sea -> {
            drawPath(polygon(70f to 70f, 70f to 36f, 92f to 66f), Color.White)
            drawPath(polygon(60f to 72f, 100f to 72f, 94f to 80f, 66f to 80f), TripPathColors.Roof)
            val gull = Path().apply {
                moveTo(196f, 56f)
                quadraticTo(210f, 62f, 224f, 56f)
                quadraticTo(238f, 50f, 252f, 56f)
            }
            drawPath(gull, Color.White, style = Stroke(3f, cap = StrokeCap.Round))
            drawLine(rgb(0x5E554C), Offset(330f, 205f), Offset(330f, 241f), strokeWidth = 3f)
            val parasol = Path().apply {
                moveTo(304f, 209f)
                quadraticTo(330f, 183f, 356f, 209f)
                close()
            }
            drawPath(parasol, rgb(0xE36A5C))
        }
        TripLandscape.Mountain -> {
            val green = rgb(0x2F5D3A)
            fir(40f, 170f, 1f, green)
            fir(70f, 180f, 0.8f, green)
            fir(340f, 200f, 1.1f, green)
            fir(365f, 215f, 0.8f, green)
            drawRect(TripPathColors.Roof, Offset(190f, 200f), Size(34f, 24f))
            drawPath(polygon(184f to 202f, 207f to 184f, 230f to 202f), rgb(0x6B4A2E))
        }
        TripLandscape.Country -> {
            val leaves = rgb(0x5E8F3E)
            val trunk = rgb(0x6B4A2E)
            drawCircle(leaves, 22f, Offset(335f, 150f))
            drawRect(trunk, Offset(332f, 168f), Size(6f, 18f))
            drawCircle(leaves, 16f, Offset(40f, 128f))
            drawRect(trunk, Offset(37f, 141f), Size(5f, 14f))
            drawRect(rgb(0xE36A5C), Offset(240f, 70f), Size(30f, 24f))
            drawPath(polygon(236f to 72f, 255f to 58f, 274f to 72f), rgb(0x8A3A12))
        }
        TripLandscape.City -> Unit
        TripLandscape.Snow -> {
            val green = rgb(0x2C4F3A)
            fir(46f, 150f, 1f, green)
            fir(330f, 190f, 1.2f, green)
            fir(360f, 205f, 0.8f, green)
            listOf(40f to 40f, 120f to 25f, 170f to 60f, 230f to 30f, 90f to 80f, 260f to 70f, 360f to 90f, 20f to 100f).forEach { (x, y) ->
                drawCircle(Color.White, 2.5f, Offset(x, y))
            }
        }
    }
}
