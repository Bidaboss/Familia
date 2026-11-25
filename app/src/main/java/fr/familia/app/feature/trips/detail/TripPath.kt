package fr.familia.app.feature.trips.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.Icon
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
 * The chosen day under the path: « Jour 3 : l'Alcázar et les jardins », then « La carte »
 * and « 18 photos », and for those who go the round « Comptes » and « Valise ».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripDayCard(
    number: Int,
    places: List<String>,
    day: LocalDate,
    photoCount: Int,
    onPhotos: () -> Unit,
    onExpenses: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** « La carte » (récit 87), once a step has a point. */
    onMap: (() -> Unit)? = null,
    /** « Valise » (récit 98): the round button under the menus, for those who go. */
    onPacking: (() -> Unit)? = null,
    packingCount: Int = 0,
    /** « Phrases » (récit 105): the round button of a trip with a step abroad, for anyone who reads it. */
    onPhrases: (() -> Unit)? = null,
    /** « Sécurité » (récit 105): the round button of a trip with a step in a country of the table of numbers. */
    onSafety: (() -> Unit)? = null,
    /** « Monnaie » (récit 105): the currency of the trip and the rate of the day, for those who go. */
    onMoney: (() -> Unit)? = null,
    /** « Documents » (récit 105, règle 41): tickets, bookings and papers, for those who go. */
    onDocuments: (() -> Unit)? = null,
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
        // The map and the photos; the program of the day is the list under (règle 23: no « Carnet »).
        SideBySide { item ->
            if (onMap != null) DayButton(stringResource(R.string.trip_map_open), onMap, item)
            DayButton(pluralStringResource(R.plurals.trip_photo_count, photoCount, photoCount), onPhotos, item)
        }
        // Then the tools, for those who go: two round buttons with their word under them.
        if (onExpenses != null || onPacking != null || onPhrases != null || onSafety != null || onMoney != null || onDocuments != null) {
            // Up to four rounds: they go on a second line rather than out of the card.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onExpenses != null) {
                    ToolRound(Icons.Outlined.Payments, stringResource(R.string.trip_expenses_short), stringResource(R.string.expenses_open), onExpenses)
                }
                if (onPhrases != null) {
                    ToolRound(Icons.Outlined.QuestionAnswer, stringResource(R.string.phrases_round_label), stringResource(R.string.phrases_round_description), onPhrases)
                }
                if (onMoney != null) {
                    ToolRound(Icons.Outlined.Euro, stringResource(R.string.money_round_label), stringResource(R.string.money_round_description), onMoney)
                }
                if (onDocuments != null) {
                    ToolRound(Icons.Outlined.Description, stringResource(R.string.documents_round_label), stringResource(R.string.documents_round_description), onDocuments)
                }
                if (onSafety != null) {
                    ToolRound(Icons.Outlined.HealthAndSafety, stringResource(R.string.safety_round_label), stringResource(R.string.safety_round_description), onSafety)
                }
                if (onPacking != null) {
                    val description = if (packingCount == 0) stringResource(R.string.packing_round_ready) else pluralStringResource(R.plurals.packing_round_todo, packingCount, packingCount)
                    ToolRound(Icons.Outlined.Luggage, stringResource(R.string.packing_round_label), description, onPacking) {
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp).size(22.dp).clip(CircleShape)
                                .background(if (packingCount == 0) Color(0xFF2E7D32) else Color(0xFFC62828)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (packingCount == 0) {
                                Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            } else {
                                Text(packingCount.coerceAtMost(99).toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
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

/**
 * A tool of the trip as a round 48 dp button with its word under it (« Comptes », « Valise »), and
 * a [badge] on its top right corner when it has one. TalkBack reads [description].
 */
@Composable
private fun ToolRound(icon: ImageVector, label: String, description: String, onClick: () -> Unit, badge: @Composable BoxScope.() -> Unit = {}) {
    Column(
        Modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 4.dp, end = 6.dp)) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color.White).border(2.dp, TripPathColors.Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = TripPathColors.Ink)
            }
            badge()
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = TripPathColors.Ink, textAlign = TextAlign.Center)
    }
}
