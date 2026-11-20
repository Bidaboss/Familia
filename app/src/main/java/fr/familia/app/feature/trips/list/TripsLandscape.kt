package fr.familia.app.feature.trips.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.feature.trips.detail.TripPathColors

/** Where the path of the list leads: to a flag, through the days of a trip, or to a question mark. */
sealed interface LandscapeGoal {
    /** [bubble]: « Dans 142 jours ». */
    data class Next(val bubble: String) : LandscapeGoal

    /** Day [day] of [days], its stop in yellow; [bubble]: « Jour 3 sur 6 ». */
    data class Ongoing(val day: Int, val days: Int, val bubble: String) : LandscapeGoal

    /** No trip to come: « Et la prochaine aventure ? ». */
    data class Nothing(val bubble: String) : LandscapeGoal
}

/**
 * The landscape at the top of « Voyages » (DESIGN-52 B): the sky, the sun, the dunes of the inside
 * of a trip, and a path from « Aujourd'hui » up to a flag. The same colors whatever the theme.
 * Decorative for TalkBack: the card under it says the same.
 */
@Composable
fun TripsLandscape(goal: LandscapeGoal, todayLabel: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val bubbleStyle = TextStyle(color = TripPathColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val todayStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val numberStyle = TextStyle(color = TripPathColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    Canvas(modifier.fillMaxWidth().height(330.dp).clearAndSetSemantics { }) {
        val sx = size.width / 390f
        val sy = size.height / 420f
        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
        drawRect(TripPathColors.Sky)
        drawCircle(TripPathColors.Sun, radius = 34.dp.toPx(), center = p(330f, 70f))
        dune(TripPathColors.DuneFar, 190f, p(110f, 120f), p(220f, 170f), p(330f, 220f), 140f)
        dune(TripPathColors.DuneMid, 280f, p(120f, 230f), p(240f, 260f), p(360f, 290f), 230f)
        dune(TripPathColors.DuneNear, 360f, p(140f, 330f), p(260f, 350f), p(380f, 370f), 330f)

        val road = Path().apply {
            moveTo(p(46f, 396f).x, p(46f, 396f).y)
            cubicTo(p(160f, 372f).x, p(160f, 372f).y, p(340f, 360f).x, p(340f, 360f).y, p(320f, 290f).x, p(320f, 290f).y)
            cubicTo(p(300f, 230f).x, p(300f, 230f).y, p(90f, 250f).x, p(90f, 250f).y, p(120f, 190f).x, p(120f, 190f).y)
            cubicTo(p(150f, 140f).x, p(150f, 140f).y, p(300f, 170f).x, p(300f, 170f).y, p(318f, 128f).x, p(318f, 128f).y)
        }
        drawPath(road, TripPathColors.Road, style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
        drawPath(
            road,
            TripPathColors.RoadDash,
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 8.dp.toPx()))),
        )
        val measure = PathMeasure().apply { setPath(road, false) }
        val start = measure.getPosition(0f)
        val end = measure.getPosition(measure.length)

        when (goal) {
            is LandscapeGoal.Next -> flag(end)
            is LandscapeGoal.Ongoing -> {
                flag(end)
                // The days of the trip along the last third of the path, today in yellow.
                val count = goal.days.coerceIn(1, 8)
                val current = ((goal.day - 1) * (count - 1) / (goal.days - 1).coerceAtLeast(1)).coerceIn(0, count - 1)
                repeat(count) { i ->
                    val at = measure.getPosition(measure.length * (0.55f + 0.42f * i / (count - 1).coerceAtLeast(1)))
                    when {
                        i < current -> drawCircle(TripPathColors.Ink, 9.dp.toPx(), at)
                        i == current -> {
                            drawCircle(TripPathColors.Sun, 14.dp.toPx(), at)
                            drawCircle(TripPathColors.Ink, 14.dp.toPx(), at, style = Stroke(3.dp.toPx()))
                            val n = measurer.measure("${goal.day}", numberStyle)
                            drawText(n, topLeft = Offset(at.x - n.size.width / 2f, at.y - n.size.height / 2f))
                        }
                        else -> {
                            drawCircle(Color.White, 9.dp.toPx(), at)
                            drawCircle(TripPathColors.Ink, 9.dp.toPx(), at, style = Stroke(2.dp.toPx()))
                        }
                    }
                }
            }
            is LandscapeGoal.Nothing -> {
                drawCircle(Color.White, 24.dp.toPx(), end)
                drawCircle(TripPathColors.Ink, 24.dp.toPx(), end, style = Stroke(3.dp.toPx()))
                val q = measurer.measure("?", bubbleStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold))
                drawText(q, topLeft = Offset(end.x - q.size.width / 2f, end.y - q.size.height / 2f))
            }
        }
        val bubble = when (goal) {
            is LandscapeGoal.Next -> goal.bubble
            is LandscapeGoal.Ongoing -> goal.bubble
            is LandscapeGoal.Nothing -> goal.bubble
        }
        pill(measure.getPosition(measure.length * 0.8f), bubble, measurer, bubbleStyle, Color.White, centered = true)

        // « Aujourd'hui »: a black dot where the path starts, and its black label.
        drawCircle(TripPathColors.Ink, 12.dp.toPx(), start)
        pill(Offset(start.x + 18.dp.toPx(), start.y), todayLabel, measurer, todayStyle, TripPathColors.Ink, centered = false)
    }
}

private fun DrawScope.dune(color: Color, startY: Float, control: Offset, middle: Offset, mirror: Offset, endY: Float) {
    val sy = size.height / 420f
    val path = Path().apply {
        moveTo(0f, startY * sy)
        quadraticTo(control.x, control.y, middle.x, middle.y)
        quadraticTo(mirror.x, mirror.y, size.width, endY * sy)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, color)
}

/** The red flag at the end of the path. */
private fun DrawScope.flag(at: Offset) {
    val pole = 46.dp.toPx()
    drawLine(TripPathColors.Ink, at, Offset(at.x, at.y - pole), strokeWidth = 3.dp.toPx())
    val top = at.y - pole
    val w = 30.dp.toPx()
    val h = 18.dp.toPx()
    drawPath(
        Path().apply {
            moveTo(at.x, top)
            lineTo(at.x + w, top)
            lineTo(at.x + w * 0.75f, top + h / 2)
            lineTo(at.x + w, top + h)
            lineTo(at.x, top + h)
            close()
        },
        FlagRed,
    )
}

/** A pill with [text]: centered on [at], or starting at it. Kept inside the drawing. */
private fun DrawScope.pill(at: Offset, text: String, measurer: TextMeasurer, style: TextStyle, color: Color, centered: Boolean) {
    val pad = 14.dp.toPx()
    val room = (size.width - 2 * pad - 16.dp.toPx()).toInt().coerceAtLeast(1)
    val layout = measurer.measure(text, style, maxLines = 1, constraints = androidx.compose.ui.unit.Constraints(maxWidth = room))
    val width = layout.size.width + 2 * pad
    val height = 36.dp.toPx().coerceAtLeast(layout.size.height + 8.dp.toPx())
    val left = (if (centered) at.x - width / 2 else at.x).coerceIn(8.dp.toPx(), (size.width - width - 8.dp.toPx()).coerceAtLeast(8.dp.toPx()))
    val top = at.y - height / 2
    drawRoundRect(color, Offset(left, top), Size(width, height), CornerRadius(height / 2))
    drawText(layout, topLeft = Offset(left + pad, top + (height - layout.size.height) / 2))
}

private val FlagRed = Color(0xFFE5675A)
