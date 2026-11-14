package fr.familia.app.feature.planning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.dayTitle
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.events.edit.TargetChoice
import fr.familia.app.feature.events.edit.targetLabel
import java.time.LocalDate
import java.time.LocalTime

/** Planning tab: upcoming events day by day, and the button to add one. Stateless. */
@Composable
fun PlanningScreen(
    state: PlanningUiState,
    onRetry: () -> Unit,
    onAddEvent: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
    initialMode: PlanningMode = PlanningMode.List,
) {
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    Box(modifier.fillMaxSize()) {
        when (state) {
            PlanningUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            PlanningUiState.Error -> EmptyState(
                icon = Icons.Outlined.Event,
                title = stringResource(R.string.planning_error_title),
                body = stringResource(R.string.planning_error_body),
                action = {
                    Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                },
            )
            is PlanningUiState.Success -> Column(Modifier.fillMaxSize()) {
                ModeSwitch(mode, onModeChange = { mode = it })
                when (mode) {
                    PlanningMode.List -> if (state.days.isEmpty()) {
                        EmptyState(
                            icon = Icons.Outlined.Event,
                            title = stringResource(R.string.planning_empty_title),
                            body = stringResource(R.string.planning_empty_body),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            // Room at the bottom so the add button never hides the last event.
                            contentPadding = PaddingValues(bottom = 96.dp),
                        ) {
                            dayItems(state.days, state.today, onOpenEvent)
                        }
                    }
                    PlanningMode.Month -> MonthView(state, onOpenEvent)
                }
            }
        }
        if (state !is PlanningUiState.Loading) {
            ExtendedFloatingActionButton(
                onClick = onAddEvent,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.planning_add)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

/** "Liste" or "Mois": two labelled buttons, always visible, 56 dp high. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitch(mode: PlanningMode, onModeChange: (PlanningMode) -> Unit) {
    val labels = listOf(PlanningMode.List to R.string.planning_mode_list, PlanningMode.Month to R.string.planning_mode_month)
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        labels.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onModeChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The headers and rows of the given days, shared by the list and the month view. */
private fun LazyListScope.dayItems(days: List<PlanningDay>, today: LocalDate, onOpenEvent: (String) -> Unit) {
    days.forEach { day ->
        item(key = "day-${day.date}") { DayHeader(day.date, today) }
        items(day.birthdays, key = { "birthday-${day.date}-${it.member.id}" }) { BirthdayRow(it) }
        items(day.items, key = { "event-${it.event.id}" }) { item ->
            EventRow(item, onClick = if (item.canManage) ({ onOpenEvent(item.event.id) }) else null)
        }
    }
}

/**
 * Month view: a grid that shows at a glance which days have something, then the list of those
 * days below it. The grid is a picture, not buttons: seven columns on a 360 dp screen cannot give
 * 56 dp touch targets, and everything it shows is in the list right under it, which TalkBack reads.
 * Only the current month and the next twelve: the planning shows what is to come.
 */
@Composable
private fun MonthView(state: PlanningUiState.Success, onOpenEvent: (String) -> Unit) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val month = YearMonth.from(state.today).plusMonths(offset.toLong())
    val monthDays = state.days.filter { YearMonth.from(it.date) == month }
    val busy = monthDays.map { it.date }.toSet()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item(key = "month-nav") {
            MonthNavigation(
                month = month,
                canGoBack = offset > 0,
                canGoForward = offset < MONTHS_AHEAD,
                onPrevious = { offset-- },
                onNext = { offset++ },
            )
        }
        item(key = "month-grid-$month") { MonthGrid(month, state.today, busy) }
        if (monthDays.isEmpty()) {
            item(key = "month-empty") {
                Text(
                    stringResource(R.string.planning_month_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            dayItems(monthDays, state.today, onOpenEvent)
        }
    }
}

@Composable
private fun MonthNavigation(month: YearMonth, canGoBack: Boolean, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            month.format(monthFormatter).replaceFirstChar { it.titlecase(Locale.FRANCE) },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPrevious, enabled = canGoBack, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.planning_month_previous), textAlign = TextAlign.Center)
            }
            OutlinedButton(onClick = onNext, enabled = canGoForward, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.planning_month_next), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, today: LocalDate, busy: Set<LocalDate>) {
    val cells = monthCells(month)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clearAndSetSemantics {}, // The list below says the same thing, in words.
    ) {
        Row(Modifier.fillMaxWidth()) {
            weekDayInitials.forEach { initial ->
                Text(
                    initial,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date -> DayCell(date, today, date in busy, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate?, today: LocalDate, hasSomething: Boolean, modifier: Modifier) {
    Column(
        modifier.heightIn(min = 48.dp).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (date == null) return@Column
        val isToday = date == today
        val color = when {
            isToday -> MaterialTheme.colorScheme.onPrimary
            date < today -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.onSurface
        }
        Box(
            Modifier
                .clip(CircleShape)
                .then(if (isToday) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium, color = color)
        }
        // A dot, not just a color: a day with an event or a birthday.
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (hasSomething) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate) {
    Text(
        text = dayTitle(date, today),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun EventRow(item: PlanningItem, onClick: (() -> Unit)?) {
    val event = item.event
    val editLabel = stringResource(R.string.members_edit_action)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClickLabel = editLabel, onClick = onClick) else Modifier)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = event.time?.let(::formatTime) ?: stringResource(R.string.planning_all_day),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 72.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                event.place?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    stringResource(R.string.planning_for_by, targetLabel(item.target), item.authorName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(Modifier.padding(start = 100.dp))
    }
}

/** A birthday in the planning: read only, it comes from the member's birth date. */
@Composable
private fun BirthdayRow(birthday: Birthday) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.widthIn(min = 72.dp)) {
                Icon(Icons.Outlined.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.planning_birthday_of, birthday.member.firstName),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    pluralStringResource(R.plurals.birthday_age, birthday.age, birthday.age),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(Modifier.padding(start = 100.dp))
    }
}

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)
private val weekDayInitials = listOf("L", "M", "M", "J", "V", "S", "D")

/** Fictional planning for previews and screenshots. */
internal fun previewPlanning(): PlanningUiState.Success {
    fun item(id: String, title: String, date: LocalDate, time: LocalTime?, place: String?, target: TargetChoice, author: String, manage: Boolean) =
        PlanningItem(Event(id, title, date, time, place, null, target.target, "a", author), target, author, manage)
    val today = LocalDate.of(2026, 12, 19)
    val day1 = LocalDate.of(2026, 12, 20)
    val day2 = LocalDate.of(2026, 12, 24)
    val noe = Member("c", "Noé", null, LocalDate.of(2016, 12, 20), "h1", MemberProfile.ChildWithoutApp, false, listOf("a"), null)
    return PlanningUiState.Success(
        today = today,
        days = listOf(
            PlanningDay(
                today,
                listOf(item("0", "Cinéma", today, LocalTime.of(14, 30), null, TargetChoice(EventTarget.ForSelf("a"), null), "Camille", true)),
            ),
            PlanningDay(
                day1,
                birthdays = listOf(Birthday(noe, day1, 10)),
                items = listOf(
                    item("1", "Piscine", day1, LocalTime.of(10, 0), "Centre aquatique", TargetChoice(EventTarget.ForChild("c"), "Noé"), "Camille", true),
                    item("2", "Marché de Noël", day1, null, null, TargetChoice(EventTarget.ForHousehold("h2"), "Foyer Bernard"), "Inès", false),
                ),
            ),
            PlanningDay(
                day2,
                listOf(item("3", "Repas de Noël", day2, LocalTime.of(19, 30), "Chez les parents", TargetChoice(EventTarget.ForFamily, null), "Camille", true)),
            ),
        ),
    )
}

@FamiliaPreviews
@Composable
private fun PlanningScreenPreview() {
    FamiliaTheme { Surface { PlanningScreen(previewPlanning(), {}, {}, {}) } }
}

@FamiliaPreviews
@Composable
private fun PlanningScreenMonthPreview() {
    FamiliaTheme { Surface { PlanningScreen(previewPlanning(), {}, {}, {}, initialMode = PlanningMode.Month) } }
}

@FamiliaPreviews
@Composable
private fun PlanningScreenEmptyPreview() {
    FamiliaTheme { Surface { PlanningScreen(PlanningUiState.Success(emptyList(), LocalDate.of(2026, 12, 19)), {}, {}, {}) } }
}
