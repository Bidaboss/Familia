package fr.familia.app.feature.home

import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.PhotoTitleShadow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.SectionLabel
import fr.familia.app.core.ui.dayShortLabel
import fr.familia.app.core.ui.dayTitle
import fr.familia.app.core.ui.formatTime
import fr.familia.app.core.ui.isLargeText
import fr.familia.app.feature.helprequests.kindLabel
import fr.familia.app.feature.helprequests.whenLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The colors of the three families of modules (R1 bis, §1): the pill, its pale background, its text. */
internal data class ToneColors(val pill: Color, val pale: Color, val text: Color)

internal fun toneColors(tone: DayTone): ToneColors = when (tone) {
    DayTone.Organise -> ToneColors(Color(0xFFC9B8F5), Color(0xFFF4F0FD), Color(0xFF4A3F8C))
    DayTone.Help -> ToneColors(Color(0xFFA8E0C2), Color(0xFFEDF7F0), Color(0xFF1F5C3C))
    DayTone.Party -> ToneColors(Color(0xFFF9B99A), Color(0xFFFDF1EA), Color(0xFF8A3A12))
}

/** What the new Accueil opens, beside [HomeActions]. */
class HomeR1bisActions(
    val onAnswer: (eventId: String, going: Boolean) -> Unit = { _, _ -> },
    val onOpenAll: () -> Unit = {},
)

/**
 * The Accueil of the R1 bis presentation (spec DESIGN du 17/11, §2), from top to bottom: the frise of
 * the 7 days, the chosen day, « À faire » with its buttons on the cards, the good news and the
 * challenge of the month side by side, « En ce moment » during a trip. « Tout Familia » stays fixed at
 * the bottom, just above the tabs, always in sight (Kévin, 17/11); « Où je suis » is in « + ». At
 * most 4 blocks between the frise and that bar. The greeting, « + », « ? » and the avatar are in the
 * bar above.
 */
@Composable
fun HomeR1bisScreen(
    state: HomeUiState,
    actions: HomeActions,
    r1: HomeR1bisActions,
    /** The good news card; [compact] beside the challenge (DESIGN-20), full width alone. */
    goodNews: (@Composable (Modifier, compact: Boolean) -> Unit)? = null,
    challenge: (@Composable (Modifier, wide: Boolean) -> Unit)? = null,
    nowTravelling: (@Composable () -> Unit)? = null,
    allBadge: Int = 0,
    /** The coups de main of « À faire », with their own « J'y vais » / « Non » (help-requests thread). */
    helpTodos: (@Composable () -> Unit)? = null,
    /** « Nouveau bon plan » (récit 45), at the end of the list, above the bar « Tout Familia ». */
    goodDeal: (@Composable () -> Unit)? = null,
    /** « Notre année en famille », from 26/12 to 10/01, at the end of the list (it shows nothing otherwise). */
    yearInReview: (@Composable () -> Unit)? = null,
    /** « Mes premiers pas » (récit 61), right after the chosen day, only while it has something to show. */
    firstSteps: (@Composable () -> Unit)? = null,
    /**
     * « Rejoignez-moi » (récit 33): the places I was sent in the last 24 h and the one I share, at the
     * very top, above the frise, only while there is one (Kévin, 20/11).
     */
    joinMe: (@Composable () -> Unit)? = null,
    /** « Aujourd'hui » | « Le fil » (récit 67), at the top while the fil is on. */
    switcher: (@Composable () -> Unit)? = null,
    /** « Le fil », shown instead of the day while chosen; « Tout Familia » stays under it. */
    feed: (@Composable () -> Unit)? = null,
    /**
     * The top of the Accueil on the photo of the theme (récit 113, règles 2, 3 et 13): the greeting,
     * the free part of the photo and the switch, scrolling with the day; [switcher] is then in it.
     */
    top: (@Composable () -> Unit)? = null,
) {
    val onPhoto = top != null
    val ready = state as? HomeUiState.Ready ?: return
    var chosen by rememberSaveable { mutableStateOf(ready.today.toEpochDay()) }
    val days = ready.days.ifEmpty { homeDays(ready.today) }
    val day = days.firstOrNull { it.date.toEpochDay() == chosen } ?: days.first()
    val frise: @Composable () -> Unit = { WeekFrise(days, ready.today, day.date, onChoose = { chosen = it.toEpochDay() }) }
    val detail: @Composable () -> Unit = { DayDetail(day, ready.today, actions) }
    val hasTodo = ready.todos.isNotEmpty() || helpTodos != null
    val calm = !hasTodo && nowTravelling == null && challenge != null
    val todo: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hasTodo) SectionLabel(stringResource(R.string.home_r1_todo), color = if (onPhoto) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            ready.todos.forEach { TodoR1Card(it, ready.today, actions, r1) }
            helpTodos?.invoke()
            if (calm) {
                Text(
                    stringResource(R.string.home_r1_calm),
                    style = if (onPhoto) MaterialTheme.typography.bodyLarge.copy(shadow = PhotoTitleShadow) else MaterialTheme.typography.bodyLarge,
                    color = if (onPhoto) Color.White else Color.Unspecified,
                )
            }
        }
    }
    val newsAndChallenge: @Composable () -> Unit = {
        if (calm && challenge != null) {
            // A calm day (§2.5): « Profites-en pour le défi ! », then the challenge in a large card,
            // and the good news under it, full width (DESIGN, 17/11).
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                challenge(Modifier.fillMaxWidth(), true)
                goodNews?.invoke(Modifier.fillMaxWidth(), false)
            }
        } else if (goodNews != null && challenge != null && isLargeText()) {
            // Large text: one under the other, or half a screen cuts words (retour d'essai du 18/11).
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                goodNews(Modifier.fillMaxWidth(), false)
                challenge(Modifier.fillMaxWidth(), true)
            }
        } else if (goodNews != null && challenge != null) {
            // Both cards take the height of the taller one (DESIGN, 17/11).
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                goodNews(Modifier.weight(1f).fillMaxHeight(), true)
                challenge(Modifier.weight(1f).fillMaxHeight(), false)
            }
        } else if (goodNews != null) {
            goodNews(Modifier.fillMaxWidth(), false)
        } else {
            challenge?.invoke(Modifier.fillMaxWidth(), true)
        }
    }
    val buttons: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            yearInReview?.invoke()
            goodDeal?.invoke()
        }
    }
    val padding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 4.dp, bottom = 24.dp)
    Column(Modifier.fillMaxSize()) {
        if (switcher != null && top == null) {
            Box(Modifier.padding(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 4.dp, bottom = 12.dp)) { switcher() }
        }
        if (feed != null) {
            Box(Modifier.fillMaxWidth().weight(1f)) { feed() }
        } else BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            when {
                // Expanded (§5): two panes, each scrolling on its own.
                maxWidth >= 840.dp -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(horizontal = ClaySpacing.screen)) {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (top != null) item(key = "top") { top() }
                        if (joinMe != null) item(key = "joinMe") { joinMe() }
                        item(key = "frise") { frise() }
                        item(key = "day") { detail() }
                        if (firstSteps != null) item(key = "firstSteps") { firstSteps() }
                        item(key = "buttons") { buttons() }
                    }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item(key = "todo") { todo() }
                        item(key = "news-challenge") { newsAndChallenge() }
                        if (nowTravelling != null) item(key = "now") { nowTravelling() }
                    }
                }
                // Medium (§5): one column, with « À faire » beside the good news, the challenge and the trip.
                maxWidth >= 600.dp -> LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (top != null) item(key = "top") { top() }
                    if (joinMe != null) item(key = "joinMe") { joinMe() }
                    item(key = "frise") { frise() }
                    item(key = "day") { detail() }
                    if (firstSteps != null) item(key = "firstSteps") { firstSteps() }
                    item(key = "columns") {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.weight(1f)) { todo() }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                newsAndChallenge()
                                nowTravelling?.invoke()
                            }
                        }
                    }
                    item(key = "buttons") { buttons() }
                }
                else -> LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (top != null) item(key = "top") { top() }
                    if (joinMe != null) item(key = "joinMe") { joinMe() }
                    item(key = "frise") { frise() }
                    item(key = "day") { detail() }
                    if (firstSteps != null) item(key = "firstSteps") { firstSteps() }
                    if (hasTodo || calm) item(key = "todo") { todo() }
                    if (goodNews != null || challenge != null) item(key = "news-challenge") { newsAndChallenge() }
                    if (nowTravelling != null) item(key = "now") { nowTravelling() }
                    item(key = "buttons") { buttons() }
                }
            }
        }
    }
}

/** The frise, also given empty days while the state has none (previews, Firebase not configured). */
private fun homeDays(today: LocalDate): List<HomeDay> = (0 until HomeWindow.WEEK_DAYS).map { HomeDay(today.plusDays(it), emptyList()) }

/**
 * 7 white boxes from today (§2.2): the day, its number, and the word of its first event on a pill of
 * its color, with a round count from 2 events. The chosen day is dark, today keeps a 2 dp outline.
 * At a large font size the boxes keep a readable width and the frise scrolls sideways.
 */
@Composable
internal fun WeekFrise(days: List<HomeDay>, today: LocalDate, chosen: LocalDate, onChoose: (LocalDate) -> Unit) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 6.dp
        val fitted = (maxWidth - gap * (days.size - 1)) / days.size
        val width: Dp = if (fitted < 48.dp * fontScale) 48.dp * fontScale else fitted
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            days.forEach { day -> DayBox(day, today, day.date == chosen, width, onChoose) }
        }
    }
}

/**
 * One box of the frise. The month grid of the new Planning (PRODUIT-126) uses the same box, without
 * the name of the day ([weekday] false), the names being above the columns.
 */
@Composable
internal fun DayBox(day: HomeDay, today: LocalDate, chosen: Boolean, width: Dp, onChoose: (LocalDate) -> Unit, weekday: Boolean = true, minHeight: Dp = 72.dp) {
    val surface = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(16.dp)
    // The boxes are cards (spec §2: « 7 cases blanches »): in the dark themes the background is the card of the theme.
    val background = if (chosen) ink else FamiliaClay.colors.card
    val text = if (chosen) surface else ink
    val first = day.first
    val count = day.items.size
    val name = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRANCE).replaceFirstChar { it.titlecase(Locale.FRANCE) }
    val spoken = if (count == 0) {
        stringResource(R.string.home_r1_day_none, "$name ${day.date.dayOfMonth}")
    } else {
        pluralStringResource(R.plurals.home_r1_day_events, count, "$name ${day.date.dayOfMonth}", count)
    }
    Box(
        modifier = Modifier
            .width(width)
            .heightIn(min = minHeight)
            .clip(shape)
            .background(background)
            .then(if (day.date == today && !chosen) Modifier.border(BorderStroke(2.dp, ink), shape) else Modifier)
            .clickable(role = Role.Tab) { onChoose(day.date) }
            .semantics {
                contentDescription = spoken
                selected = chosen
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 2.dp).clearAndSetSemantics { },
        ) {
            if (weekday) {
                Text(
                    day.date.format(DOW).replace(".", "").replaceFirstChar { it.titlecase(Locale.FRANCE) },
                    fontSize = 12.sp,
                    color = text,
                    maxLines = 1,
                )
            }
            Text(day.date.dayOfMonth.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = text, maxLines = 1)
            if (first != null) {
                val tone = toneColors(first.tone)
                Spacer(Modifier.height(2.dp))
                Text(
                    first.word,
                    fontSize = 12.sp,
                    color = tone.text,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(tone.pill)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        if (count >= 2 && first != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(toneColors(first.tone).pill)
                    .clearAndSetSemantics { },
            ) {
                Text(count.toString(), fontSize = 12.sp, lineHeight = 12.sp, color = toneColors(first.tone).text, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private val DOW = DateTimeFormatter.ofPattern("EEE", Locale.FRANCE)

/** The chosen day (§2.3): its title, « Tout le planning », then hour, a line of color, what and who. */
@Composable
private fun DayDetail(day: HomeDay, today: LocalDate, actions: HomeActions) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                dayTitle(day.date, today),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            FilledTonalButton(
                onClick = actions.onOpenPlanning,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = toneColors(DayTone.Organise).pale,
                    contentColor = toneColors(DayTone.Organise).text,
                ),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.home_week_all)) }
        }
        if (day.items.isEmpty()) {
            Text(
                stringResource(R.string.home_r1_day_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        day.items.forEach { item -> DayLine(item, actions) }
    }
}

@Composable
private fun DayLine(item: DayItem, actions: HomeActions) {
    val open: (() -> Unit)? = when (val target = item.open) {
        is DayOpen.Event -> ({ actions.onOpenEvent(target.id) })
        is DayOpen.Help -> ({ actions.onOpenHelpRequest(target.id) })
        is DayOpen.Birthday -> actions.onOpenWishes?.let { wishes -> { wishes(target.memberId) } }
    }
    val tone = toneColors(item.tone)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (open != null) Modifier.clickable(onClick = open) else Modifier)
            .padding(vertical = 6.dp),
    ) {
        Text(
            item.time?.let { formatTime(it) } ?: stringResource(R.string.home_r1_all_day),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 64.dp),
        )
        Box(Modifier.padding(horizontal = 10.dp).width(4.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(tone.pill))
        Column(Modifier.weight(1f)) {
            val what = if (item.open is DayOpen.Birthday) stringResource(R.string.home_r1_birthday, item.what) else item.what
            Text(what, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.who?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A card of « À faire » with its buttons right on it (§2.4), 48 dp each. */
@Composable
private fun TodoR1Card(todo: HomeTodo, today: LocalDate, actions: HomeActions, r1: HomeR1bisActions) {
    when (todo) {
        is HomeTodo.Answer -> TodoShell(
            title = todo.event.title,
            detail = todo.event.time?.let { "${dayShortLabel(todo.event.date, today)} · ${formatTime(it)}" } ?: dayShortLabel(todo.event.date, today),
            onOpen = { actions.onOpenEvent(todo.event.id) },
        ) {
            FilledTonalButton(onClick = { r1.onAnswer(todo.event.id, true) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.home_r1_going))
            }
            OutlinedButton(onClick = { r1.onAnswer(todo.event.id, false) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.home_r1_not_going))
            }
        }
        is HomeTodo.Help -> TodoShell(
            title = todo.request.title.ifBlank { kindLabel(todo.request.kind) },
            detail = stringResource(R.string.home_todo_help, todo.request.authorName, whenLabel(todo.request, today)),
            onOpen = { actions.onOpenHelpRequest(todo.request.id) },
        ) {
            FilledTonalButton(onClick = { actions.onOpenHelpRequest(todo.request.id) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.home_r1_help_open))
            }
        }
        is HomeTodo.Vote -> TodoShell(
            title = todo.poll.question,
            detail = stringResource(R.string.home_r1_poll_by, todo.poll.authorName),
            onOpen = { actions.onOpenPoll(todo.poll.id) },
        ) {
            FilledTonalButton(onClick = { actions.onOpenPoll(todo.poll.id) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.home_r1_vote))
            }
        }
    }
}

@Composable
private fun TodoShell(title: String, detail: String, onOpen: () -> Unit, buttons: @Composable () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) { buttons() }
    }
}

