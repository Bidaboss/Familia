package fr.familia.app.feature.trips.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.model.TripLandscape
import fr.familia.app.core.ui.SideBySide
import fr.familia.app.core.ui.frenchDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val weekdayShort = DateTimeFormatter.ofPattern("EEE d", Locale.FRANCE)

/**
 * The days of the trip, side by side: « Jour 3 », « mar 17 ». The chosen one is black; today keeps a
 * 2 dp outline when another day is chosen. TalkBack reads « Jour 3, mardi 17 novembre ».
 */
@Composable
fun TripDayStrip(days: List<LocalDate>, chosen: LocalDate, today: LocalDate, onChoose: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val chosenIndex = days.indexOf(chosen).coerceAtLeast(0)
    LaunchedEffect(chosenIndex) { listState.animateScrollToItem((chosenIndex - 1).coerceAtLeast(0)) }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(days, key = { _, day -> day.toEpochDay() }) { index, day ->
            val isChosen = day == chosen
            val shape = RoundedCornerShape(16.dp)
            val spoken = stringResource(R.string.trip_day_spoken, index + 1, frenchDate(day, "EEEE d MMMM"))
            Column(
                modifier = Modifier
                    .widthIn(min = 80.dp)
                    .heightIn(min = 56.dp)
                    .clip(shape)
                    .background(if (isChosen) TripPathColors.Ink else Color.White)
                    .then(if (day == today && !isChosen) Modifier.border(2.dp, TripPathColors.Ink, shape) else Modifier)
                    .selectable(selected = isChosen, role = Role.Tab, onClick = { onChoose(day) })
                    .semantics { contentDescription = spoken }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ink = if (isChosen) Color.White else TripPathColors.Ink
                // The column reads « Jour 3, mardi 17 novembre » as a whole.
                Text(stringResource(R.string.trip_day_number, index + 1), color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics { })
                Text(day.format(weekdayShort), color = ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}

/** How a stop is drawn: done or today in black, the chosen one in yellow, the days to come dotted. */
private enum class StopLook { Passed, Chosen, ToCome }

/**
 * The landscape of the maquette R6, with the path through the shown days ([TripDays.window]) and a
 * label beside the chosen one. Decorative for TalkBack: the strip above says the same.
 */
@Composable
fun TripPathIllustration(
    days: List<LocalDate>,
    chosen: LocalDate,
    today: LocalDate,
    label: String?,
    modifier: Modifier = Modifier,
    landscape: TripLandscape = TripLandscape.Dunes,
) {
    val palette = landscape.palette
    val measurer = rememberTextMeasurer()
    val chosenIndex = days.indexOf(chosen).coerceAtLeast(0)
    val shown = TripDays.window(days.size, chosenIndex)
    val numberStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    val labelStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Canvas(modifier.fillMaxWidth().height(300.dp).clearAndSetSemantics { }) {
        val sx = size.width / 390f
        val sy = size.height / 420f
        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
        // The sky, the sun, the three layers and the decor of the landscape of the trip (récit 81).
        drawLandscape(landscape)

        val road = Path().apply {
            // « M60 400 C150 370 300 360 280 300 S80 270 110 220 S290 190 300 150 », the S made explicit.
            to(p(60f, 400f))
            curve(p(150f, 370f), p(300f, 360f), p(280f, 300f))
            curve(p(260f, 240f), p(80f, 270f), p(110f, 220f))
            curve(p(140f, 170f), p(290f, 190f), p(300f, 150f))
        }
        drawPath(road, palette.road, style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
        drawPath(
            road,
            palette.dash,
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 8.dp.toPx()))),
        )

        val measure = PathMeasure().apply { setPath(road, false) }
        val count = shown.count()
        var chosenAt: Offset? = null
        shown.forEachIndexed { position, dayIndex ->
            val fraction = if (count == 1) 0.5f else position.toFloat() / (count - 1)
            val at = measure.getPosition(measure.length * fraction)
            val day = days[dayIndex]
            val look = when {
                day == chosen -> StopLook.Chosen
                day.isAfter(today) -> StopLook.ToCome
                else -> StopLook.Passed
            }
            if (look == StopLook.Chosen) chosenAt = at
            drawStop(at, look, "${dayIndex + 1}", measurer, numberStyle)
        }
        val at = chosenAt
        if (label != null && at != null) drawLabel(at, label, measurer, labelStyle)
    }
}

private fun Path.to(o: Offset) = moveTo(o.x, o.y)

private fun Path.curve(a: Offset, b: Offset, c: Offset) = cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)

private fun DrawScope.drawStop(at: Offset, look: StopLook, number: String, measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle) {
    val ink = TripPathColors.Ink
    val textColor = when (look) {
        StopLook.Passed -> Color.White
        StopLook.Chosen, StopLook.ToCome -> ink
    }
    when (look) {
        StopLook.Passed -> drawCircle(ink, 18.dp.toPx(), at)
        StopLook.Chosen -> {
            drawCircle(TripPathColors.Sun, 22.dp.toPx(), at)
            drawCircle(ink, 22.dp.toPx(), at, style = Stroke(4.dp.toPx()))
        }
        StopLook.ToCome -> {
            drawCircle(Color.White, 18.dp.toPx(), at)
            drawCircle(ink, 18.dp.toPx(), at, style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
        }
    }
    val text = measurer.measure(number, style.copy(color = textColor, textAlign = TextAlign.Center))
    drawText(text, topLeft = Offset(at.x - text.size.width / 2f, at.y - text.size.height / 2f))
}

/** A black pill beside the chosen stop, on its right when it fits, else on its left. */
private fun DrawScope.drawLabel(at: Offset, label: String, measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle) {
    val pad = 12.dp.toPx()
    val gap = 30.dp.toPx()
    val room = (size.width - 2 * pad - gap - 16.dp.toPx()).toInt().coerceAtLeast(1)
    val text = measurer.measure(label, style, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = androidx.compose.ui.unit.Constraints(maxWidth = room))
    val width = text.size.width + 2 * pad
    val height = 32.dp.toPx().coerceAtLeast(text.size.height + 8.dp.toPx())
    val right = at.x + gap
    val left = if (right + width <= size.width - 8.dp.toPx()) right else (at.x - gap - width).coerceAtLeast(8.dp.toPx())
    val top = at.y - height / 2f
    drawRoundRect(TripPathColors.Ink, Offset(left, top), Size(width, height), CornerRadius(height / 2f))
    drawText(text, topLeft = Offset(left + pad, top + (height - text.size.height) / 2f))
}

/**
 * The chosen day under the path: « Jour 3 : l'Alcázar et les jardins », then « 18 photos », « Carnet »
 * and « Comptes » (participants only). The buttons are 48 dp, inside the card.
 */
@Composable
fun TripDayCard(
    number: Int,
    places: List<String>,
    day: LocalDate,
    photoCount: Int,
    onPhotos: () -> Unit,
    onNotebook: () -> Unit,
    onExpenses: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** « La carte » (récit 87), once a step has a point. */
    onMap: (() -> Unit)? = null,
) {
    val title = if (places.isEmpty()) {
        stringResource(R.string.trip_day_title, number, frenchDate(day, "EEEE d MMMM"))
    } else {
        stringResource(R.string.trip_day_title, number, places.joinToString(", "))
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = TripPathColors.Ink,
            modifier = Modifier.semantics { heading() },
        )
        SideBySide { item ->
            DayButton(pluralStringResource(R.plurals.trip_photo_count, photoCount, photoCount), onPhotos, item)
            DayButton(stringResource(R.string.trip_notebook), onNotebook, item)
            if (onExpenses != null) DayButton(stringResource(R.string.trip_expenses_short), onExpenses, item)
            if (onMap != null) DayButton(stringResource(R.string.trip_map_open), onMap, item)
        }
    }
}

@Composable
private fun DayButton(text: String, onClick: () -> Unit, modifier: Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.textButtonColors(containerColor = TripPathColors.Button, contentColor = TripPathColors.Ink),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
    }
}
