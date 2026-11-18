package fr.familia.app.feature.planning

import fr.familia.app.feature.home.DayItem
import fr.familia.app.feature.home.DayOpen
import fr.familia.app.feature.home.DayTone
import fr.familia.app.feature.home.HomeDay
import fr.familia.app.feature.home.WeekFrise
import fr.familia.app.feature.home.isFestive
import fr.familia.app.feature.home.shortWord
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import fr.familia.app.core.ui.aboveSnackbar
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
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.share.ShareButton
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Feature
import fr.familia.app.core.photo.CoverThumbnail
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import androidx.compose.ui.semantics.contentDescription
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.dayTitle
import fr.familia.app.core.theme.dayHeaderColor
import fr.familia.app.core.theme.dayHeaderTextStyle
import fr.familia.app.core.ui.formatDay
import fr.familia.app.core.ui.formatTime
import fr.familia.app.core.model.Trip
import fr.familia.app.feature.trips.joinNames
import fr.familia.app.feature.trips.tripDatesLabel
import fr.familia.app.feature.events.edit.TargetChoice
import fr.familia.app.feature.events.edit.targetLabel
import java.time.LocalDate
import java.time.LocalTime
import fr.familia.app.core.ui.LoadingState

/** Planning tab: upcoming events day by day, and the button to add one. Stateless. */
@Composable
fun PlanningScreen(
    state: PlanningUiState,
    onRetry: () -> Unit,
    onAddEvent: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
    /** The month first (16/11), the list one tap away. */
    initialMode: PlanningMode = PlanningMode.Month,
    /** « Toute la famille » / « Mon foyer » (carte AVIS-8); hidden when null. */
    onScopeChange: ((PlanningScope) -> Unit)? = null,
    onOpenTrip: (tripId: String) -> Unit = {},
    /** « Partager » on a birthday (PRD, récit 31); hidden when null. */
    onShareBirthday: ((Birthday) -> Unit)? = null,
    /** R1 bis (§4, R3): the frise of the week first, with « Semaine » beside « Mois » and « Liste ». */
    r1: Boolean = false,
    /** The new Planning (PRODUIT-126) once loaded: it takes the whole screen, its « + » is in the bar. */
    v2: (@Composable (PlanningUiState.Success) -> Unit)? = null,
) {
    var mode by rememberSaveable { mutableStateOf(if (r1) PlanningMode.Week else initialMode) }
    Box(modifier.fillMaxSize()) {
        when (state) {
            PlanningUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
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
            is PlanningUiState.Success -> if (v2 != null) v2(state) else Column(Modifier.fillMaxSize()) {
                // One row (audit DESIGN n°3): « Mois | Liste » on the left, « Mon foyer » on the right.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModeSwitch(mode, onModeChange = { mode = it }, modifier = Modifier.weight(1f), withWeek = r1)
                    if (onScopeChange != null) ScopeChip(state.scope, onScopeChange)
                }
                when (mode) {
                    PlanningMode.List -> if (state.days.isEmpty()) {
                        EmptyState(
                            icon = Icons.Outlined.Event,
                            title = stringResource(R.string.planning_empty_title),
                            body = stringResource(
                                if (state.scope == PlanningScope.Household) R.string.planning_scope_empty_body else R.string.planning_empty_body,
                            ),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            // Room at the bottom so the add button never hides the last event.
                            contentPadding = PaddingValues(bottom = 96.dp),
                        ) {
                            dayItems(state.days, state.today, onOpenEvent, onOpenTrip, onShareBirthday)
                        }
                    }
                    PlanningMode.Month -> MonthView(state, onOpenEvent, onOpenTrip, onShareBirthday)
                    PlanningMode.Week -> WeekView(state, onOpenEvent, onOpenTrip, onShareBirthday)
                }
            }
        }
        if (state !is PlanningUiState.Loading && v2 == null) {
            ExtendedFloatingActionButton(
                onClick = onAddEvent,
                // The text of an extended button is hidden from TalkBack (Material 3): the icon says it.
                icon = { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.planning_add)) },
                text = { Text(stringResource(R.string.planning_add)) },
                modifier = Modifier.align(Alignment.BottomEnd).aboveSnackbar().padding(16.dp),
            )
        }
    }
}

/** "Mois" or "Liste": two labelled buttons, always visible, 56 dp high, the month first (16/11). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitch(mode: PlanningMode, onModeChange: (PlanningMode) -> Unit, modifier: Modifier = Modifier, withWeek: Boolean = false) {
    val labels = listOfNotNull(
        (PlanningMode.Week to R.string.planning_mode_week).takeIf { withWeek },
        PlanningMode.Month to R.string.planning_mode_month,
        PlanningMode.List to R.string.planning_mode_list,
    )
    SingleChoiceSegmentedButtonRow(modifier) {
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

/**
 * « Mon foyer », a switch, off at first (16/11): on, it keeps the events for the whole family, for
 * my household, for its members and for the children I am responsible for, and every birthday.
 * Off is the whole family. The whole row toggles it, 56 dp high.
 */
@Composable
private fun ScopeChip(scope: PlanningScope, onScopeChange: (PlanningScope) -> Unit) {
    val on = scope == PlanningScope.Household
    Row(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onScopeChange(if (it) PlanningScope.Household else PlanningScope.Family) })
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.planning_scope_household), style = MaterialTheme.typography.labelLarge)
        Switch(checked = on, onCheckedChange = null)
    }
}

/** The headers and rows of the given days, shared by the list and the month view. */
private fun LazyListScope.dayItems(
    days: List<PlanningDay>,
    today: LocalDate,
    onOpenEvent: (String) -> Unit,
    onOpenTrip: (String) -> Unit = {},
    onShareBirthday: ((Birthday) -> Unit)? = null,
    /** R1 bis week: the small avatars of those who come under each event (spec §4). */
    comers: Boolean = false,
) {
    days.forEach { day ->
        item(key = "day-${day.date}") { DayHeader(day.date, today) }
        items(day.birthdays, key = { "birthday-${day.date}-${it.member.id}" }) { birthday ->
            BirthdayRow(birthday, onShare = onShareBirthday?.let { share -> { share(birthday) } })
        }
        items(day.trips, key = { "trip-${day.date}-${it.trip.id}" }) { trip ->
            TripRow(trip, today, onClick = { onOpenTrip(trip.trip.id) })
        }
        items(day.items, key = { "event-${it.event.id}" }) { item ->
            EventRow(item, comers, onClick = { onOpenEvent(item.event.id) })
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
private fun MonthView(
    state: PlanningUiState.Success,
    onOpenEvent: (String) -> Unit,
    onOpenTrip: (String) -> Unit = {},
    onShareBirthday: ((Birthday) -> Unit)? = null,
) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val month = YearMonth.from(state.today).plusMonths(offset.toLong())
    val monthDays = state.days.filter { YearMonth.from(it.date) == month }
    // A trip marks every one of its days (PRD, récit 11), not only the day it is listed on.
    val busy = monthDays.map { it.date }.toSet() + state.tripDays.filter { YearMonth.from(it) == month }
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
            dayItems(monthDays, state.today, onOpenEvent, onOpenTrip, onShareBirthday)
        }
    }
}

@Composable
private fun MonthNavigation(month: YearMonth, canGoBack: Boolean, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        style = dayHeaderTextStyle(),
        color = dayHeaderColor(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun EventRow(item: PlanningItem, comers: Boolean, onClick: () -> Unit) {
    val event = item.event
    val openLabel = stringResource(R.string.planning_open_action)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = openLabel, onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = ClaySpacing.screen, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = event.time?.let(::formatTime) ?: stringResource(R.string.planning_all_day),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 72.dp),
            )
            Spacer(Modifier.width(12.dp))
            // Two lines (DESIGN-5): the title, then where, for whom when it is not the whole family,
            // and who comes. The author is the avatar at the end.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                val forWhom = if (event.target is EventTarget.ForFamily) null else stringResource(R.string.planning_for, targetLabel(item.target))
                val details = listOfNotNull(event.place, forWhom).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                answersLabel(item)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (comers && item.comers.isNotEmpty()) ComersRow(item.comers)
            }
            // The thumbnail of its cover (récit 44), before the author.
            val cover = event.coverPhoto?.takeIf { LocalFeatureFlags.current.isOn(Feature.MorePhotos) }
            if (cover != null) {
                Spacer(Modifier.width(12.dp))
                CoverThumbnail(cover)
            }
            val by = stringResource(R.string.planning_by, item.authorName)
            if (item.author != null) {
                Spacer(Modifier.width(12.dp))
                MemberAvatar(item.author, size = 32.dp, modifier = Modifier.semantics { contentDescription = by })
            } else {
                Spacer(Modifier.width(12.dp))
                Text(item.authorName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(start = 100.dp))
    }
}

/** Up to 6 small avatars of those who come, then « +N »; the line above already says how many, for TalkBack. */
@Composable
private fun ComersRow(comers: List<Member>) {
    val shown = comers.take(MAX_COMERS)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp).clearAndSetSemantics { },
    ) {
        shown.forEach { MemberAvatar(it, size = 24.dp) }
        if (comers.size > shown.size) {
            Text("+${comers.size - shown.size}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val MAX_COMERS = 6

/** "2 viennent · 1 peut-être", or nothing while nobody has answered yes or maybe. */
@Composable
private fun answersLabel(item: PlanningItem): String? {
    val parts = buildList {
        if (item.coming > 0) add(pluralStringResource(R.plurals.planning_coming, item.coming, item.coming))
        if (item.maybe > 0) add(pluralStringResource(R.plurals.planning_maybe, item.maybe, item.maybe))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** A trip the member follows (PRD, récit 11): « Voyage : Corse », its dates and who takes part. */
@Composable
private fun TripRow(item: PlanningTrip, today: LocalDate, onClick: () -> Unit) {
    val trip = item.trip
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.trips_open_action), onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = ClaySpacing.screen, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.widthIn(min = 72.dp)) {
                Icon(Icons.Outlined.CardTravel, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.planning_trip, trip.title), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (item.ongoing) {
                        stringResource(R.string.planning_trip_ongoing, formatDay(trip.endDate, today).lowercase(Locale.FRANCE))
                    } else {
                        tripDatesLabel(trip, today)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.participants.isNotEmpty()) {
                    Text(
                        stringResource(R.string.trip_with, joinNames(item.participants)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 100.dp))
    }
}

/** A birthday in the planning: read only, it comes from the member's birth date. */
@Composable
private fun BirthdayRow(birthday: Birthday, onShare: (() -> Unit)? = null) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = ClaySpacing.screen, vertical = 12.dp),
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
            if (onShare != null) {
                Spacer(Modifier.weight(1f))
                ShareButton(onClick = onShare)
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
        PlanningItem(Event(id, title, date, time, place, null, target.target, "a", author), target, author, canManage = manage)
    val today = LocalDate.of(2026, 12, 19)
    val day1 = LocalDate.of(2026, 12, 20)
    val day2 = LocalDate.of(2026, 12, 24)
    val noe = Member("c", "Noé", null, LocalDate.of(2016, 12, 20), "h1", MemberProfile.ChildWithoutApp, false, listOf("a"), null)
    return PlanningUiState.Success(
        today = today,
        tripDays = setOf(LocalDate.of(2026, 12, 22), LocalDate.of(2026, 12, 23)),
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
                LocalDate.of(2026, 12, 22),
                items = emptyList(),
                trips = listOf(
                    PlanningTrip(
                        Trip("t1", "Ski à Morzine", LocalDate.of(2026, 12, 22), LocalDate.of(2026, 12, 23), listOf("a"), listOf("a"), "a", "Camille"),
                        listOf("Camille", "Inès"),
                        ongoing = false,
                    ),
                ),
            ),
            PlanningDay(
                day2,
                listOf(item("3", "Repas de Noël", day2, LocalTime.of(19, 30), "Chez les parents", TargetChoice(EventTarget.ForFamily, null), "Camille", true).copy(coming = 7, maybe = 2)),
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

/**
 * The week (R1 bis, §4, R3): the frise of the Accueil from Monday, « Aujourd'hui » to come back to
 * this week, and the chosen day under it. The arrows go a week back or forward.
 */
@Composable
private fun WeekView(
    state: PlanningUiState.Success,
    onOpenEvent: (String) -> Unit,
    onOpenTrip: (String) -> Unit,
    onShareBirthday: ((Birthday) -> Unit)?,
) {
    var start by rememberSaveable { mutableStateOf(weekStart(state.today).toEpochDay()) }
    var chosen by rememberSaveable { mutableStateOf(state.today.toEpochDay()) }
    val monday = LocalDate.ofEpochDay(start)
    val byDate = state.days.associateBy { it.date }
    val week = (0L until 7L).map { offset ->
        val date = monday.plusDays(offset)
        HomeDay(date, byDate[date]?.let { planningDayItems(it) }.orEmpty())
    }
    val chosenDate = LocalDate.ofEpochDay(chosen).takeIf { it in monday..monday.plusDays(6) } ?: monday
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item(key = "week-nav") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.screen),
            ) {
                IconButton(onClick = { start = monday.minusWeeks(1).toEpochDay() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = stringResource(R.string.planning_week_previous))
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = {
                        start = weekStart(state.today).toEpochDay()
                        chosen = state.today.toEpochDay()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.day_today)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { start = monday.plusWeeks(1).toEpochDay() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.planning_week_next))
                }
            }
        }
        item(key = "week-frise") {
            Box(Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp)) {
                WeekFrise(week, state.today, chosenDate, onChoose = { chosen = it.toEpochDay() })
            }
        }
        val day = byDate[chosenDate]
        if (day == null) {
            item(key = "week-empty") {
                Column {
                    DayHeader(chosenDate, state.today)
                    Text(
                        stringResource(R.string.home_r1_day_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
                    )
                }
            }
        } else {
            dayItems(listOf(day), state.today, onOpenEvent, onOpenTrip, onShareBirthday, comers = true)
        }
    }
}

/** A day of the planning as the frise shows it: the birthdays, then the events by hour. */
internal fun planningDayItems(day: PlanningDay): List<DayItem> =
    day.birthdays.map {
        DayItem(null, it.member.firstName, null, shortWord(it.member.firstName), DayTone.Party, DayOpen.Birthday(it.member.id))
    } + day.items.map {
        DayItem(it.event.time, it.event.title, null, shortWord(it.event.title), if (isFestive(it.event.title)) DayTone.Party else DayTone.Organise, DayOpen.Event(it.event.id))
    }.sortedWith(compareBy(nullsLast()) { it.time })
