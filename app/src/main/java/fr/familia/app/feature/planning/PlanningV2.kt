package fr.familia.app.feature.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Trip
import fr.familia.app.core.photo.CoverThumbnail
import fr.familia.app.core.share.ShareButton
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.theme.dayHeaderColor
import fr.familia.app.core.theme.dayHeaderTextStyle
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.dayTitle
import fr.familia.app.feature.home.DayBox
import fr.familia.app.feature.home.DayTone
import fr.familia.app.feature.home.HomeDay
import fr.familia.app.feature.home.WeekFrise
import fr.familia.app.feature.home.isFestive
import fr.familia.app.feature.home.toneColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The Planning tab (PRODUIT-126, maquettes PL1 à PL5, haut C), the only one since PRODUIT-131:
 * Semaine or Mois, chosen by touching the title between the two round arrows; the frise of the
 * Accueil for the week (the same word of the day), a grid of the same boxes for the month; then the
 * cards of the days, with the hour in a block on the pale color of the event. The filter and the
 * « + » are round buttons in the bar of the tab. Nothing about the gifts or the surprises shows here:
 * the planning has only the events, the birthdays and the trips.
 */
@Composable
fun PlanningV2(
    state: PlanningUiState.Success,
    me: Member?,
    onOpenEvent: (String) -> Unit,
    onOpenTrip: (String) -> Unit,
    onShareBirthday: ((Birthday) -> Unit)?,
    modifier: Modifier = Modifier,
    initialMode: PlanningMode = PlanningMode.Week,
) {
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var chosen by rememberSaveable { mutableLongStateOf(state.today.toEpochDay()) }
    val chosenDate = LocalDate.ofEpochDay(chosen)
    val byDate = state.days.associateBy { it.date }
    val child = me != null && me.profile != MemberProfile.Adult
    val cards = PlanningCards(me?.id, child, state.today, onOpenEvent, onOpenTrip, onShareBirthday)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "v2-header") {
            PlanningV2Header(
                mode = mode,
                chosen = chosenDate,
                today = state.today,
                scope = state.scope,
                child = child,
                onMode = { mode = it },
                onMove = { forward -> chosen = planningMove(mode, chosenDate, state.today, forward).toEpochDay() },
            )
        }
        when (mode) {
            PlanningMode.Month -> {
                val month = YearMonth.from(chosenDate)
                item(key = "v2-month-$month") {
                    MonthBoxes(month, state.today, chosenDate, byDate, onChoose = { chosen = it.toEpochDay() })
                }
                dayBlocks(listOf(chosenDate), byDate, cards, alwaysFirst = true)
            }
            else -> {
                val monday = weekStart(chosenDate)
                item(key = "v2-frise") {
                    val week = (0L until 7L).map { offset ->
                        val date = monday.plusDays(offset)
                        HomeDay(date, byDate[date]?.let { planningDayItems(it) }.orEmpty())
                    }
                    Box(Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp)) {
                        WeekFrise(week, state.today, chosenDate, onChoose = { chosen = it.toEpochDay() })
                    }
                }
                dayBlocks(weekDaysShown(chosenDate, byDate.keys), byDate, cards, alwaysFirst = true)
            }
        }
    }
}

/** What the cards need beside their day: who I am, and where they lead. */
private class PlanningCards(
    val meId: String?,
    val child: Boolean,
    val today: LocalDate,
    val onOpenEvent: (String) -> Unit,
    val onOpenTrip: (String) -> Unit,
    val onShareBirthday: ((Birthday) -> Unit)?,
)

/**
 * The days in [dates]: their title, « n événements » from 2, and their cards. The first day is
 * shown even empty, with « Rien de prévu. Une journée tranquille. »
 */
private fun LazyListScope.dayBlocks(dates: List<LocalDate>, byDate: Map<LocalDate, PlanningDay>, cards: PlanningCards, alwaysFirst: Boolean) {
    dates.forEachIndexed { index, date ->
        val day = byDate[date]
        val count = day?.let { it.birthdays.size + it.trips.size + it.items.size } ?: 0
        if (count == 0 && !(alwaysFirst && index == 0)) return@forEachIndexed
        item(key = "v2-day-$date") { DayTitle(date, cards.today, count) }
        if (day == null || count == 0) {
            item(key = "v2-empty-$date") {
                Text(
                    stringResource(R.string.home_r1_day_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
                )
            }
            return@forEachIndexed
        }
        items(day.birthdays, key = { "v2-birthday-$date-${it.member.id}" }) { birthday ->
            CardSlot { BirthdayCard(birthday, cards.onShareBirthday?.let { share -> { share(birthday) } }) }
        }
        items(day.trips, key = { "v2-trip-$date-${it.trip.id}" }) { trip ->
            CardSlot { TripCard(trip, date, cards.today, onClick = { cards.onOpenTrip(trip.trip.id) }) }
        }
        items(day.items, key = { "v2-event-${it.event.id}" }) { item ->
            CardSlot { EventCard(item, cards, onClick = { cards.onOpenEvent(item.event.id) }) }
        }
    }
}

@Composable
private fun CardSlot(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = ClaySpacing.screen, vertical = 6.dp)) { content() }
}

/** « Aujourd'hui, mercredi 18 », a thin line, then « 4 événements » from two. */
@Composable
private fun DayTitle(date: LocalDate, today: LocalDate, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 16.dp, bottom = 6.dp),
    ) {
        Text(
            dayTitle(date, today),
            style = dayHeaderTextStyle(),
            color = dayHeaderColor(),
            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
        )
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
                .clearAndSetSemantics { },
        )
        if (count >= 2) {
            Text(
                pluralStringResource(R.plurals.planning_v2_day_count, count, count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Haut C: « ‹ Du 18 au 24 novembre ▾ › », and « Semaine · toute la famille » under it. Touching the
 * title opens the choice Semaine or Mois. Arrows of 56 dp to touch, the title on two lines at a large font.
 */
@Composable
private fun PlanningV2Header(
    mode: PlanningMode,
    chosen: LocalDate,
    today: LocalDate,
    scope: PlanningScope,
    child: Boolean,
    onMode: (PlanningMode) -> Unit,
    onMove: (forward: Boolean) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    val title = if (mode == PlanningMode.Month) {
        YearMonth.from(chosen).format(MONTH).replaceFirstChar { it.titlecase(Locale.FRANCE) }
    } else {
        val (from, to) = weekRange(weekStart(chosen))
        stringResource(R.string.planning_v2_week_range, from, to)
    }
    val view = stringResource(if (mode == PlanningMode.Month) R.string.planning_mode_month else R.string.planning_mode_week)
    val who = stringResource(
        when {
            scope == PlanningScope.Family -> R.string.planning_v2_scope_family
            child -> R.string.planning_v2_scope_house
            else -> R.string.planning_v2_scope_household
        },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
    ) {
        RoundArrow(
            back = true,
            enabled = canMoveBack(mode, chosen, today),
            label = stringResource(if (mode == PlanningMode.Month) R.string.planning_month_previous else R.string.planning_week_previous),
            onClick = { onMove(false) },
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val choose = stringResource(R.string.planning_v2_choose_view)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = choose, role = Role.Button) { choosing = true }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                    )
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                }
                Text(
                    stringResource(R.string.planning_v2_subtitle, view, who),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                listOf(PlanningMode.Week to R.string.planning_mode_week, PlanningMode.Month to R.string.planning_mode_month).forEach { (value, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(label),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (value == mode) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            choosing = false
                            onMode(value)
                        },
                        modifier = Modifier.heightIn(min = 56.dp),
                    )
                }
            }
        }
        RoundArrow(
            back = false,
            enabled = canMoveForward(mode, chosen, today),
            label = stringResource(if (mode == PlanningMode.Month) R.string.planning_month_next else R.string.planning_week_next),
            onClick = { onMove(true) },
        )
    }
}

@Composable
private fun RoundArrow(back: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    // 56 dp to touch (règle du projet), the white round drawn at 48 dp inside.
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(FamiliaClay.colors.card),
        ) {
            Icon(
                if (back) Icons.AutoMirrored.Outlined.KeyboardArrowLeft else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = label,
            )
        }
    }
}

/**
 * The month (PRODUIT-123): a white card, the names of the days above 7 columns, then the boxes of
 * the frise, with the number, the word and the round count. The chosen day is dark.
 */
@Composable
private fun MonthBoxes(month: YearMonth, today: LocalDate, chosen: LocalDate, byDate: Map<LocalDate, PlanningDay>, onChoose: (LocalDate) -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.screen, vertical = 8.dp), contentPadding = PaddingValues(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 4.dp
            val width: Dp = (maxWidth - gap * 6) / 7
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.clearAndSetSemantics { }) {
                    WEEK_DAYS.forEach { name ->
                        Text(
                            name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(width),
                        )
                    }
                }
                monthCells(month).chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        week.forEach { date ->
                            if (date == null) {
                                Spacer(Modifier.width(width))
                            } else {
                                val items = byDate[date]?.let { planningDayItems(it) }.orEmpty()
                                DayBox(HomeDay(date, items), today, date == chosen, width, onChoose, weekday = false, minHeight = 56.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The block on the left of a card: its pale color, and inside the hour, a cake, or the day of a trip. */
@Composable
private fun TimeTile(tone: DayTone, spoken: String, content: @Composable () -> Unit) {
    val colors = toneColors(tone)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(68.dp)
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.pale)
            .padding(vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun HourTile(time: LocalTime?, tone: DayTone) {
    val ink = toneColors(tone).text
    if (time == null) {
        val allDay = stringResource(R.string.planning_all_day)
        TimeTile(tone, allDay) { Text(allDay, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        return
    }
    val (hours, minutes) = hourParts(time)
    TimeTile(tone, fr.familia.app.core.ui.formatTime(time)) {
        Text(hours, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 30.sp, color = ink)
        Text(minutes, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = ink)
    }
}

/** The middle of a card: title, place, note, then the avatars and « 4 y vont ». */
@Composable
private fun CardBody(
    title: String,
    place: String?,
    note: String?,
    tone: DayTone,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        if (!place.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(place, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!note.isNullOrBlank()) {
            Text(note, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = toneColors(tone).text)
        }
        footer()
    }
}

@Composable
private fun CardRow(onClick: (() -> Unit)?, onClickLabel: String?, content: @Composable RowScope.() -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, onClickLabel = onClickLabel, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun EventCard(item: PlanningItem, cards: PlanningCards, onClick: () -> Unit) {
    val event = item.event
    val tone = eventTone(event)
    CardRow(onClick, stringResource(R.string.planning_open_action)) {
        HourTile(event.time, tone)
        val forWhom = if (event.target is EventTarget.ForFamily) null else stringResource(R.string.planning_for, fr.familia.app.feature.events.edit.targetLabel(item.target))
        CardBody(
            title = event.title,
            place = listOfNotNull(event.place?.takeIf { it.isNotBlank() }, forWhom).joinToString(" · ").ifEmpty { null },
            note = event.note,
            tone = tone,
            modifier = Modifier.weight(1f),
        ) {
            Comers(item.comers, cards.meId)
            if (cards.child && cards.meId != null && event.authorId == cards.meId) {
                Text(
                    stringResource(R.string.planning_v2_added_by_you),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val cover = event.coverPhoto?.takeIf { LocalFeatureFlags.current.isOn(Feature.MorePhotos) }
        if (cover != null) CoverThumbnail(cover, size = 56.dp)
    }
}

/** Up to 4 avatars of those who go, then « +2 », and the green « 4 y vont » or « Tu y vas ». */
@Composable
private fun Comers(comers: List<Member>, meId: String?) {
    if (comers.isEmpty()) return
    val label = if (comers.size == 1 && comers.first().id == meId) {
        stringResource(R.string.planning_v2_you_go)
    } else {
        pluralStringResource(R.plurals.planning_v2_going, comers.size, comers.size)
    }
    val shown = comers.take(MAX_AVATARS)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.clearAndSetSemantics { }) {
            shown.forEachIndexed { index, member ->
                MemberAvatar(member, size = 32.dp, modifier = Modifier.offset(x = (-8 * index).dp))
            }
        }
        val overlap = (-8 * (shown.size - 1)).dp
        Row(Modifier.offset(x = overlap), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (comers.size > shown.size) {
                Text(
                    "+${comers.size - shown.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
            val going = toneColors(DayTone.Help)
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = going.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(going.pale)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun BirthdayCard(birthday: Birthday, onShare: (() -> Unit)?) {
    CardRow(onClick = null, onClickLabel = null) {
        val allDay = stringResource(R.string.planning_all_day)
        val ink = toneColors(DayTone.Party).text
        TimeTile(DayTone.Party, allDay) {
            Icon(Icons.Outlined.Cake, contentDescription = null, tint = ink)
            Text(allDay, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        CardBody(
            title = stringResource(R.string.planning_birthday_of, birthday.member.firstName),
            place = null,
            note = pluralStringResource(R.plurals.birthday_age, birthday.age, birthday.age),
            tone = DayTone.Party,
            modifier = Modifier.weight(1f),
        ) {
            Box(Modifier.clearAndSetSemantics { }) { MemberAvatar(birthday.member, size = 32.dp) }
        }
        if (onShare != null) ShareButton(onClick = onShare)
    }
}

@Composable
private fun TripCard(item: PlanningTrip, date: LocalDate, today: LocalDate, onClick: () -> Unit) {
    val trip = item.trip
    val tone = DayTone.Help
    val ink = toneColors(tone).text
    CardRow(onClick, stringResource(R.string.trips_open_action)) {
        val (day, of) = tripDay(trip, date)
        TimeTile(tone, stringResource(R.string.planning_v2_trip_day_spoken, day, of)) {
            Text(stringResource(R.string.planning_v2_trip_day), color = ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(day.toString(), fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 30.sp, color = ink)
            Text(stringResource(R.string.planning_v2_trip_of, of), color = ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        val (from, to) = tripRange(trip)
        CardBody(
            title = stringResource(R.string.planning_trip, trip.title),
            place = stringResource(R.string.planning_v2_trip_dates, from, to),
            note = item.participants.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.trip_with, fr.familia.app.feature.trips.joinNames(it)) },
            tone = tone,
            modifier = Modifier.weight(1f),
        )
    }
}

private const val MAX_AVATARS = 4

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)
private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)
private val SHORT_DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)
private val WEEK_DAYS = listOf("lun", "mar", "mer", "jeu", "ven", "sam", "dim")

/** The color of an event: terracotta for a party, violet otherwise, as in the frise of the Accueil. */
internal fun eventTone(event: Event): DayTone = if (isFestive(event.title)) DayTone.Party else DayTone.Organise

/** « 18 » and « h00 » for 18:00. */
internal fun hourParts(time: LocalTime): Pair<String, String> = time.hour.toString() to "h%02d".format(time.minute)

/**
 * The two ends of « Du … au … » for the week from [monday]: « 18 » and « 24 novembre » in one month,
 * « 30 novembre » and « 6 décembre » across two, with the years across two years.
 */
internal fun weekRange(monday: LocalDate): Pair<String, String> {
    val sunday = monday.plusDays(6)
    return when {
        monday.year != sunday.year -> dayLabel(monday, DAY_MONTH_YEAR) to dayLabel(sunday, DAY_MONTH_YEAR)
        monday.month != sunday.month -> dayLabel(monday, DAY_MONTH) to dayLabel(sunday, DAY_MONTH)
        else -> (if (monday.dayOfMonth == 1) "1er" else monday.dayOfMonth.toString()) to dayLabel(sunday, DAY_MONTH)
    }
}

/** « 6 décembre », and « 1er décembre » for the first day of a month, as one says it in French. */
private fun dayLabel(date: LocalDate, format: DateTimeFormatter): String {
    val text = date.format(format)
    return if (date.dayOfMonth == 1) "1er" + text.removePrefix("1") else text
}

/** « 27 » and « 29 nov. », the dates of a trip on its card. */
internal fun tripRange(trip: Trip): Pair<String, String> =
    (if (trip.startDate.month == trip.endDate.month) trip.startDate.dayOfMonth.toString() else trip.startDate.format(SHORT_DAY_MONTH)) to
        trip.endDate.format(SHORT_DAY_MONTH)

/** Which day of the trip [date] is, and how many days it lasts: « JOUR 2 sur 3 ». */
internal fun tripDay(trip: Trip, date: LocalDate): Pair<Int, Int> {
    val total = ChronoUnit.DAYS.between(trip.startDate, trip.endDate).toInt() + 1
    val day = (ChronoUnit.DAYS.between(trip.startDate, date).toInt() + 1).coerceIn(1, total.coerceAtLeast(1))
    return day to total.coerceAtLeast(1)
}

/** The chosen day, then the next days of its week that have something, as on PL1. */
internal fun weekDaysShown(chosen: LocalDate, busy: Set<LocalDate>): List<LocalDate> {
    val sunday = weekStart(chosen).plusDays(6)
    return listOf(chosen) + generateSequence(chosen.plusDays(1)) { it.plusDays(1) }.takeWhile { it <= sunday }.filter { it in busy }
}

/**
 * The day chosen after an arrow: a week or a month away, today when the arrow comes back to the
 * period of today, the 1st of the month otherwise.
 */
internal fun planningMove(mode: PlanningMode, chosen: LocalDate, today: LocalDate, forward: Boolean): LocalDate {
    val step = if (forward) 1L else -1L
    val target = if (mode == PlanningMode.Month) {
        YearMonth.from(chosen).plusMonths(step).atDay(1).let { if (YearMonth.from(it) == YearMonth.from(today)) today else it }
    } else {
        weekStart(chosen).plusWeeks(step).let { if (weekStart(it) == weekStart(today)) today else it }
    }
    return target
}

/** The planning shows what is to come: no going back before the period of today. */
internal fun canMoveBack(mode: PlanningMode, chosen: LocalDate, today: LocalDate): Boolean =
    if (mode == PlanningMode.Month) YearMonth.from(chosen) > YearMonth.from(today) else weekStart(chosen) > weekStart(today)

/** At most a year ahead, the span of the birthdays. */
internal fun canMoveForward(mode: PlanningMode, chosen: LocalDate, today: LocalDate): Boolean =
    if (mode == PlanningMode.Month) {
        YearMonth.from(chosen) < YearMonth.from(today).plusMonths(MONTHS_AHEAD.toLong())
    } else {
        weekStart(chosen).plusWeeks(1) <= today.plusMonths(MONTHS_AHEAD.toLong())
    }

@FamiliaPreviews
@Composable
private fun PlanningV2WeekPreview() {
    val me = Member("a", "Camille", null, null, "h1", MemberProfile.Adult, false, emptyList(), null)
    FamiliaTheme { Surface { PlanningV2(previewPlanning(), me, {}, {}, null) } }
}

@FamiliaPreviews
@Composable
private fun PlanningV2MonthPreview() {
    val me = Member("c", "Noé", null, null, "h1", MemberProfile.ChildWithApp, false, listOf("a"), null)
    FamiliaTheme { Surface { PlanningV2(previewPlanning(), me, {}, {}, null, initialMode = PlanningMode.Month) } }
}
