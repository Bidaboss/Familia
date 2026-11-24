package fr.familia.app.feature.trips.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.familia.app.R
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.ui.BackButton
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The days chosen so far: the start alone after one touch, both after two. */
data class DateRange(val start: LocalDate?, val end: LocalDate?) {
    /** Nights between the start and the end; null while the end is not chosen. */
    val nights: Long? get() = if (start != null && end != null) ChronoUnit.DAYS.between(start, end) else null
}

/**
 * One touch on [day] (fiche F4): the first one is the start, the second the end; a day before the start
 * becomes the new start, and a touch once both are chosen starts again. The same day twice: one day.
 */
fun pickDay(range: DateRange, day: LocalDate): DateRange = when {
    range.start == null || range.end != null -> DateRange(day, null)
    day < range.start -> DateRange(day, null)
    ChronoUnit.DAYS.between(range.start, day) >= MAX_PAST_TRIP_DAYS -> DateRange(day, null)
    else -> DateRange(range.start, day)
}

/** How far the calendar goes back (a trip already done, in Souvenirs) and forward. */
const val DATES_MONTHS_BACK = 60L
const val DATES_MONTHS_AHEAD = 36L

/** One line of the calendar: the name of a month, or a week of it (null: a day of another month). */
sealed interface CalendarLine {
    data class Month(val month: YearMonth) : CalendarLine
    data class Week(val month: YearMonth, val days: List<LocalDate?>) : CalendarLine
}

/** The months from [first] to [last], each with its weeks from Monday; a week has seven places. */
fun calendarLines(first: YearMonth, last: YearMonth): List<CalendarLine> {
    val lines = mutableListOf<CalendarLine>()
    var month = first
    while (month <= last) {
        lines += CalendarLine.Month(month)
        val lead = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
        val cells: List<LocalDate?> = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week -> lines += CalendarLine.Week(month, week + List(7 - week.size) { null }) }
        month = month.plusMonths(1)
    }
    return lines
}

/** The cream of the page of the dates, and the sand of the days inside the trip (fiche F4). */
private val Cream = Color(0xFFFBF4EA)
private val Ink = Color(0xFF1E1B18)
private val InRange = Color(0xFFF1DFC6)
private val SaveDisabled = Color(0xFF8E979D)

private val headerDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)
private val monthName = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)
private val spokenDay = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE)

/**
 * « Dates du voyage » (fiche F4): the whole screen, the months one under the other, the trip from the
 * start to the end highlighted. Past days stay touchable, paler: a trip already done is allowed.
 */
@Composable
fun TripDatesDialog(start: LocalDate?, end: LocalDate?, onSave: (LocalDate, LocalDate) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TripDatesScreen(start, end, onSave, onDismiss)
    }
}

/** Stateless but for the days being chosen. */
@Composable
fun TripDatesScreen(start: LocalDate?, end: LocalDate?, onSave: (LocalDate, LocalDate) -> Unit, onBack: () -> Unit) {
    var range by rememberSaveable(stateSaver = DateRangeSaver) { mutableStateOf(DateRange(start, end)) }
    val today = remember { familyToday() }
    val opensOn = YearMonth.from(start ?: today)
    val first = minOf(YearMonth.from(today).minusMonths(DATES_MONTHS_BACK), opensOn)
    val last = maxOf(YearMonth.from(today).plusMonths(DATES_MONTHS_AHEAD), YearMonth.from(end ?: opensOn))
    val lines = remember(first, last) { calendarLines(first, last) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = lines.indexOf(CalendarLine.Month(opensOn)).coerceAtLeast(0))
    Surface(Modifier.fillMaxSize(), color = Cream, contentColor = Ink) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.shadow(4.dp).background(Cream)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(shape = CircleShape, color = Color.White, contentColor = Ink, shadowElevation = 2.dp) { BackButton(onClick = onBack) }
                    Text(
                        stringResource(R.string.trip_dates_title),
                        fontFamily = Fredoka,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeaderDay(stringResource(R.string.trip_dates_start), range.start, Modifier.weight(1f), TextAlign.Start)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                    HeaderDay(stringResource(R.string.trip_dates_end), range.end, Modifier.weight(1f), TextAlign.End)
                }
                Row(Modifier.fillMaxWidth().height(36.dp).clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
                    DayOfWeek.entries.forEach { day ->
                        Text(
                            day.getDisplayName(TextStyle.NARROW, Locale.FRANCE),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = Ink.copy(alpha = 0.75f),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
                items(lines, key = { line -> line.key() }) { line ->
                    when (line) {
                        is CalendarLine.Month -> Text(
                            line.month.format(monthName),
                            fontFamily = Fredoka,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 6.dp).semantics { heading() },
                        )
                        is CalendarLine.Week -> Row(Modifier.fillMaxWidth()) {
                            line.days.forEach { day ->
                                Box(Modifier.weight(1f).height(56.dp)) {
                                    if (day != null) DayCell(day, range, past = day < today, onPick = { range = pickDay(range, day) })
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.background(Cream).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { range = DateRange(null, null) },
                        enabled = range.start != null,
                        modifier = Modifier.heightIn(min = 56.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF1F3B4D)),
                    ) {
                        Text(stringResource(R.string.trip_dates_clear), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textDecoration = TextDecoration.Underline)
                    }
                    Spacer(Modifier.weight(1f))
                    val nights = range.nights
                    Text(
                        when {
                            nights == null && range.start == null -> stringResource(R.string.trip_dates_pick_start)
                            nights == null -> stringResource(R.string.trip_dates_pick_end)
                            nights == 0L -> stringResource(R.string.trip_dates_single_day)
                            else -> pluralStringResource(R.plurals.trip_dates_nights, nights.toInt(), nights.toInt())
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink.copy(alpha = 0.75f),
                        textAlign = TextAlign.End,
                    )
                }
                val chosenStart = range.start
                val chosenEnd = range.end
                Button(
                    onClick = { if (chosenStart != null && chosenEnd != null) onSave(chosenStart, chosenEnd) },
                    enabled = chosenStart != null && chosenEnd != null,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ink,
                        contentColor = Color.White,
                        disabledContainerColor = SaveDisabled,
                        disabledContentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(stringResource(R.string.trip_dates_save), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

private fun CalendarLine.key(): String = when (this) {
    is CalendarLine.Month -> "m$month"
    is CalendarLine.Week -> "w$month-${days.firstNotNullOf { it }}"
}

@Composable
private fun HeaderDay(label: String, day: LocalDate?, modifier: Modifier, align: TextAlign) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink.copy(alpha = 0.75f), textAlign = align, modifier = Modifier.fillMaxWidth())
        Text(
            day?.format(headerDate) ?: stringResource(R.string.trip_dates_none),
            fontFamily = Fredoka,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = align,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A day of 56 high: dark round at the start and the end, the sand band between, paler in the past. */
@Composable
private fun DayCell(day: LocalDate, range: DateRange, past: Boolean, onPick: () -> Unit) {
    val start = range.start
    val end = range.end
    val isStart = day == start
    val isEnd = day == end
    val inside = start != null && end != null && day > start && day < end
    val spoken = day.format(spokenDay)
    val described = when {
        isStart && isEnd -> stringResource(R.string.trip_dates_day_only, spoken)
        isStart -> stringResource(R.string.trip_dates_day_start, spoken)
        isEnd -> stringResource(R.string.trip_dates_day_end, spoken)
        inside -> stringResource(R.string.trip_dates_day_in, spoken)
        else -> spoken
    }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(role = Role.Button, onClick = onPick)
            .semantics(mergeDescendants = true) { contentDescription = described },
        contentAlignment = Alignment.Center,
    ) {
        // The band of the trip goes under the rounds, from the middle of the start to the middle of the end.
        val band = start != null && end != null && start != end && (inside || isStart || isEnd)
        if (band) {
            Row(Modifier.fillMaxWidth().height(44.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight().background(if (isStart) Color.Transparent else InRange))
                Box(Modifier.weight(1f).fillMaxHeight().background(if (isEnd) Color.Transparent else InRange))
            }
        }
        if (isStart || isEnd) Box(Modifier.size(44.dp).background(Ink, CircleShape))
        Text(
            day.dayOfMonth.toString(),
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = when {
                isStart || isEnd -> Color.White
                past -> Ink.copy(alpha = 0.6f)
                else -> Ink
            },
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

private val DateRangeSaver = androidx.compose.runtime.saveable.listSaver<DateRange, String?>(
    save = { listOf(it.start?.toString(), it.end?.toString()) },
    restore = { DateRange(it[0]?.let(LocalDate::parse), it[1]?.let(LocalDate::parse)) },
)
