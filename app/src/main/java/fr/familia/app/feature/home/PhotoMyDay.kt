package fr.familia.app.feature.home

import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.model.Event
import fr.familia.app.core.theme.FamiliaThemeId
import fr.familia.app.core.theme.PhotoSectionShadow
import fr.familia.app.core.theme.PhotoSectionText
import fr.familia.app.core.theme.photoCard
import fr.familia.app.core.ui.dayShortLabel
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.helprequests.kindLabel
import fr.familia.app.feature.helprequests.whenLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The two colors of a photo theme, for the « Ma journée » drawn on its photo (fiche A1). */
internal class PhotoColors(val accent: Color, val dark: Color) {
    /** The accent under a white text when it is dark enough (4,5:1), else the dark color. */
    val filled: Color get() = if (contrastWithWhite(accent) >= 4.5) accent else dark

    companion object {
        fun of(theme: FamiliaThemeId) = PhotoColors(Color(theme.accent ?: 0xFF5A3A2E), Color(theme.dark ?: 0xFF2B2B2B))
    }
}

internal fun contrastWithWhite(color: Color): Double = 1.05 / (color.luminance() + 0.05)

/** The text under the titles of the cards (A1 §7). */
private val PhotoSoftBrown = Color(0xFF6B4A1F)
private val BusyDot = Color(0xFFF1BF00)

/** « Rien à répondre » (A1 §6): a mint card. */
private val Mint = Color(0xE6E3F4EA)

/** « À répondre » shows this many cards; the rest is one line (règle 1). */
internal const val PHOTO_ANSWERS_SHOWN = 2

/** « À venir » shows this many lines, « Tout voir › » opens the Planning. */
internal const val PHOTO_UPCOMING_SHOWN = 5

/** A title of section on the photo (A1 §4): white with a shadow, a count pill, a small pill on the right. */
@Composable
internal fun PhotoSection(
    title: String,
    colors: PhotoColors,
    count: Int = 0,
    pill: String? = null,
    onPill: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 2.dp)) {
        Text(
            title,
            color = PhotoSectionText,
            fontSize = 19.sp,
            // titleLarge carries the title font (DESIGN-45).
            style = MaterialTheme.typography.titleLarge.copy(shadow = PhotoSectionShadow),
            modifier = Modifier.semantics { heading() },
        )
        if (count > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(start = 8.dp).heightIn(min = 20.dp).clip(CircleShape).background(colors.filled).padding(horizontal = 7.dp),
            ) { Text(count.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
        }
        Spacer(Modifier.weight(1f))
        if (pill != null && onPill != null) {
            // 26 dp drawn, 56 dp to touch (règle 5).
            Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onPill)) {
                Text(
                    pill,
                    color = PhotoBrown,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * The card « Aujourd'hui » (règles 4 à 6): how many things on the chosen day, a sentence, then the 7
 * days with a yellow dot under the busy ones. A busy day shows its lines (hour, color, title) in place
 * of the sentence, so a tap on a day previews it without leaving the Accueil.
 */
@Composable
internal fun PhotoTodayCard(days: List<HomeDay>, today: LocalDate, chosen: LocalDate, onChoose: (LocalDate) -> Unit, colors: PhotoColors, actions: HomeActions) {
    val day = days.firstOrNull { it.date == chosen } ?: days.first()
    val count = day.items.size
    Column(Modifier.fillMaxWidth().photoCard().padding(horizontal = 17.dp, vertical = 16.dp)) {
        Text(
            when {
                count == 0 -> stringResource(R.string.home_photo_nothing)
                day.date == today -> pluralStringResource(R.plurals.home_photo_today_count, count, count)
                else -> pluralStringResource(R.plurals.home_photo_day_count, count, count, dayShortLabel(day.date, today).lowercase(Locale.FRANCE))
            },
            color = PhotoBrown,
            style = MaterialTheme.typography.titleLarge,
            fontSize = 28.sp,
            lineHeight = 32.sp,
        )
        if (count == 0) {
            Text(
                if (day.date == today) stringResource(R.string.home_photo_quiet) else dayShortLabel(day.date, today),
                color = PhotoBrown,
                fontSize = 15.sp,
            )
        }
        day.items.forEach { DayLine(it, actions) }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d -> PhotoDayCell(d, d.date == day.date, colors, onChoose, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PhotoDayCell(day: HomeDay, chosen: Boolean, colors: PhotoColors, onChoose: (LocalDate) -> Unit, modifier: Modifier) {
    val name = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRANCE).replaceFirstChar { it.titlecase(Locale.FRANCE) }
    val count = day.items.size
    val spoken = if (count == 0) {
        stringResource(R.string.home_r1_day_none, "$name ${day.date.dayOfMonth}")
    } else {
        pluralStringResource(R.plurals.home_r1_day_events, count, "$name ${day.date.dayOfMonth}", count)
    }
    val text = if (chosen) Color.White else PhotoBrown
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (chosen) colors.dark else Color.Transparent)
            .clickable(role = Role.Tab) { onChoose(day.date) }
            .semantics {
                contentDescription = spoken
                selected = chosen
            }
            .padding(vertical = 6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { }) {
            Text(day.date.format(DOW).replace(".", "").replaceFirstChar { it.titlecase(Locale.FRANCE) }, color = text, fontSize = 12.sp, maxLines = 1)
            Text(day.date.dayOfMonth.toString(), color = text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Box(Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(if (count > 0) BusyDot else Color.Transparent))
        }
    }
}

private val DOW = DateTimeFormatter.ofPattern("EEE", Locale.FRANCE)

/**
 * One answer to give (A1 §6): an icon, the title, when, then « J'y vais » and « Non » side by side,
 * 40 dp drawn and 56 dp to touch (règle 5).
 */
@Composable
internal fun PhotoAnswerCard(
    todo: HomeTodo,
    today: LocalDate,
    colors: PhotoColors,
    actions: HomeActions,
    r1: HomeR1bisActions,
    /** The answer just given to this event (règle 6 de l'accueil): « ✓ Tu y vas » and « Changer » in place of the buttons. */
    answered: Boolean? = null,
    onChange: () -> Unit = {},
) {
    val (icon, title, detail, onOpen) = when (todo) {
        is HomeTodo.Answer -> AnswerText(
            if (isFestive(todo.event.title)) "🎉" else "📅",
            todo.event.title,
            todo.event.time?.let { "${dayShortLabel(todo.event.date, today)} · ${formatTime(it)}" } ?: dayShortLabel(todo.event.date, today),
        ) { actions.onOpenEvent(todo.event.id) }
        is HomeTodo.Help -> AnswerText(
            "🤝",
            todo.request.title.ifBlank { kindLabel(todo.request.kind) },
            stringResource(R.string.home_todo_help, todo.request.authorName, whenLabel(todo.request, today)),
        ) { actions.onOpenHelpRequest(todo.request.id) }
        is HomeTodo.Vote -> AnswerText("🗳️", todo.poll.question, stringResource(R.string.home_r1_poll_by, todo.poll.authorName)) { actions.onOpenPoll(todo.poll.id) }
    }
    Column(Modifier.fillMaxWidth().photoCard().clickable(onClick = onOpen).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF1E6))) {
                Text(icon, fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics { })
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, color = PhotoBrown, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(detail, color = PhotoSoftBrown, fontSize = 13.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 56.dp, top = 4.dp)) {
            when {
                todo is HomeTodo.Answer && answered != null -> {
                    Text(
                        stringResource(if (answered) R.string.home_photo_going_done else R.string.home_photo_not_going_done),
                        color = if (answered) Color(0xFF1F5C3C) else Color(0xFF8A3B2B),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.align(Alignment.CenterVertically).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    // A link, 56 dp to touch: the buttons come back to answer again.
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onChange)) {
                        Text(
                            stringResource(R.string.home_photo_change),
                            color = PhotoSoftBrown,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
                todo is HomeTodo.Answer -> {
                    PhotoButton(stringResource(R.string.home_r1_going), colors.filled, Color.White) { r1.onAnswer(todo.event.id, true) }
                    PhotoButton(stringResource(R.string.home_r1_not_going), Color.White.copy(alpha = 0.7f), Color(0xFF8A3B2B)) { r1.onAnswer(todo.event.id, false) }
                }
                todo is HomeTodo.Help -> PhotoButton(stringResource(R.string.home_r1_help_open), colors.filled, Color.White, onOpen)
                todo is HomeTodo.Vote -> PhotoButton(stringResource(R.string.home_r1_vote), colors.filled, Color.White, onOpen)
            }
        }
    }
}

private data class AnswerText(val icon: String, val title: String, val detail: String, val onOpen: () -> Unit)

/** A button of the photo: its natural width, 40 dp drawn, 56 dp to touch. */
@Composable
private fun PhotoButton(text: String, background: Color, ink: Color, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(background).padding(horizontal = 20.dp)) {
            Text(text, color = ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** « Et 3 autres » under the 2 answers shown (règle 1): a tap shows them all. */
@Composable
internal fun PhotoMoreLine(more: Int, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick)) {
        Text(
            pluralStringResource(R.plurals.home_photo_more_answers, more, more),
            color = PhotoSectionText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge.copy(shadow = PhotoSectionShadow),
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

/** Nothing to answer (A1 §6): a mint card. */
@Composable
internal fun PhotoNothingToAnswer() {
    Text(
        stringResource(R.string.home_photo_nothing_to_answer),
        color = Color(0xFF1F5C3C),
        fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Mint).padding(18.dp),
    )
}

/** « À venir » (règle 7): one glass card, a line of 56 dp per event, « dans N jours » on the right. */
@Composable
internal fun PhotoUpcoming(events: List<Event>, today: LocalDate, onOpenEvent: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().photoCard().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (events.isEmpty()) {
            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.home_photo_upcoming_none), color = PhotoBrown, fontSize = 15.sp)
            }
        }
        events.take(PHOTO_UPCOMING_SHOWN).forEachIndexed { index, event ->
            if (index > 0) HorizontalDivider(color = PhotoBrown.copy(alpha = 0.12f))
            val days = ChronoUnit.DAYS.between(today, event.date).toInt()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpenEvent(event.id) },
            ) {
                val tone = toneColors(if (isFestive(event.title)) DayTone.Party else DayTone.Organise)
                Box(Modifier.size(10.dp).clip(CircleShape).background(tone.pill))
                Text(
                    event.title,
                    color = PhotoBrown,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp).weight(1f),
                )
                Text(
                    if (days == 1) stringResource(R.string.day_tomorrow).lowercase(Locale.FRANCE) else pluralStringResource(R.plurals.home_photo_in_days, days, days),
                    color = PhotoSoftBrown,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

/** « En bref » (règle 4): the line of the fil, with its news; the challenge follows in its own card. */
@Composable
internal fun PhotoFeedLine(unseen: Int, onOpenFeed: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().photoCard().clickable(role = Role.Button, onClick = onOpenFeed).padding(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFCE4EC))) {
            Text("💬", fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics { })
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(stringResource(R.string.home_photo_feed), color = PhotoBrown, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (unseen > 0) pluralStringResource(R.plurals.home_photo_feed_news, unseen, unseen) else stringResource(R.string.home_photo_feed_none),
                color = PhotoSoftBrown,
                fontSize = 13.sp,
            )
        }
        Text("›", color = PhotoBrown, fontSize = 20.sp, modifier = Modifier.width(16.dp).clearAndSetSemantics { })
    }
}

/** The cards of the Accueil that keep their own drawing on the photo. */
internal class PhotoSlots(
    val top: @Composable () -> Unit,
    val joinMe: (@Composable () -> Unit)?,
    val firstSteps: (@Composable () -> Unit)?,
    val helpTodos: (@Composable () -> Unit)?,
    val nowTravelling: (@Composable () -> Unit)?,
    val challenge: (@Composable (Modifier, wide: Boolean) -> Unit)?,
    /** Only while there is no fil: « Publier » lives in the fil (règle 8). */
    val goodNews: (@Composable (Modifier, compact: Boolean) -> Unit)?,
    val end: @Composable () -> Unit,
    val surprises: List<fr.familia.app.core.model.Surprise> = emptyList(),
    val toRate: List<PhotoSurpriseToRate> = emptyList(),
)

/** A surprise of mine with proposals I have not rated yet: who it is for, and how many. */
data class PhotoSurpriseToRate(val surpriseId: String, val beneficiaryName: String, val count: Int)

/**
 * « Ma journée » on the photo (récit 113, règles 1 à 8, fiche A1): « Aujourd'hui » and its card, « À
 * répondre » (2 cards, the rest in one line), « À venir » (30 days), « En bref » (the fil, the
 * challenge). One column at every width, the photo being behind the whole screen.
 */
@Composable
internal fun PhotoDayList(
    ready: HomeUiState.Ready,
    days: List<HomeDay>,
    chosen: LocalDate,
    onChoose: (LocalDate) -> Unit,
    colors: PhotoColors,
    actions: HomeActions,
    r1: HomeR1bisActions,
    slots: PhotoSlots,
    feedUnseen: Int,
    onOpenFeed: (() -> Unit)?,
) {
    var allAnswers by rememberSaveable { mutableStateOf(false) }
    // The answers given here stay on their card, « ✓ Tu y vas » with « Changer » (règle 6), until the
    // Accueil is left; the event has already left « À répondre » once the answer is written.
    var given by remember { mutableStateOf(mapOf<String, Given>()) }
    val pendingKeys = ready.todos.map { it.key }.toSet()
    val shownTodos = (ready.todos + given.values.map { it.todo }.filter { it.key !in pendingKeys }).sortedBy { it.date }
    val answers = if (allAnswers) shownTodos else shownTodos.take(PHOTO_ANSWERS_SHOWN)
    val more = shownTodos.size - answers.size
    val surprise = remember(slots.surprises, ready.today) { fr.familia.app.core.model.surprisesToConfirm(slots.surprises, ready.today) }
        ?.takeIf { actions.onOpenSurprise != null }
    val toRate = slots.toRate.takeIf { actions.onOpenSurprise != null }.orEmpty()
    val answering = HomeR1bisActions(
        onAnswer = { id, going ->
            ready.todos.firstOrNull { it is HomeTodo.Answer && it.event.id == id }?.let { todo ->
                given = given + (id to Given(todo as HomeTodo.Answer, going, changing = false))
            } ?: given[id]?.let { given = given + (id to it.copy(going = going, changing = false)) }
            r1.onAnswer(id, going)
        },
        onOpenAll = r1.onOpenAll,
    )
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalMyDayBottom.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "top") { slots.top() }
        slots.joinMe?.let { item(key = "joinMe") { it() } }
        item(key = "today-title") {
            PhotoSection(stringResource(R.string.home_photo_today), colors, pill = stringResource(R.string.home_photo_planning), onPill = actions.onOpenPlanning)
        }
        item(key = "today") { PhotoTodayCard(days, ready.today, chosen, onChoose, colors, actions) }
        slots.firstSteps?.let { item(key = "firstSteps") { it() } }
        item(key = "answer-title") { PhotoSection(stringResource(R.string.home_photo_to_answer), colors, count = ready.todos.size + (surprise?.count ?: 0) + toRate.size) }
        surprise?.let { s ->
            item(key = "answer-surprise") {
                PhotoSurpriseCard(s, colors) {
                    if (s.count == 1) actions.onOpenSurprise?.invoke(s.firstId) else actions.onOpenGifts()
                }
            }
        }
        items(answers, key = { "answer-" + it.key }) { todo ->
            // « ✓ Tu y vas » only once the answer is written: it left « À répondre ». A failed answer keeps the buttons.
            val reply = (todo as? HomeTodo.Answer)?.let { given[it.event.id] }?.takeIf { !it.changing && todo.key !in pendingKeys }
            PhotoAnswerCard(
                todo, ready.today, colors, actions, answering,
                answered = reply?.going,
                onChange = { (todo as? HomeTodo.Answer)?.let { a -> given[a.event.id]?.let { given = given + (a.event.id to it.copy(changing = true)) } } },
            )
        }
        items(toRate, key = { "answer-rate-" + it.surpriseId }) { rate ->
            PhotoSurpriseToRateCard(rate) { actions.onOpenSurprise?.invoke(rate.surpriseId) }
        }
        if (more > 0) item(key = "answer-more") { PhotoMoreLine(more) { allAnswers = true } }
        slots.helpTodos?.let { item(key = "help") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { it() } } }
        if (shownTodos.isEmpty() && slots.helpTodos == null && surprise == null && toRate.isEmpty()) item(key = "answer-none") { PhotoNothingToAnswer() }
        slots.nowTravelling?.let { item(key = "now") { it() } }
        item(key = "upcoming-title") {
            PhotoSection(stringResource(R.string.home_photo_upcoming), colors, pill = stringResource(R.string.home_photo_see_all), onPill = actions.onOpenPlanning)
        }
        item(key = "upcoming") { PhotoUpcoming(ready.upcoming, ready.today, actions.onOpenEvent) }
        if (onOpenFeed != null || slots.challenge != null || slots.goodNews != null) {
            item(key = "brief-title") { PhotoSection(stringResource(R.string.home_photo_brief), colors) }
        }
        onOpenFeed?.let { open -> item(key = "brief-feed") { PhotoFeedLine(feedUnseen, open) } }
        slots.goodNews?.let { item(key = "brief-news") { it(Modifier.fillMaxWidth(), false) } }
        slots.challenge?.let { item(key = "brief-challenge") { it(Modifier.fillMaxWidth(), true) } }
        item(key = "end") { slots.end() }
    }
}

/**
 * « C'est le jour J : confirme » (audit Cadeaux, point 8): a surprise of mine has reached its day and
 * waits for « C'est offert ». First in « À répondre »; the whole card opens it (or Cadeaux for several).
 */
@Composable
private fun PhotoSurpriseCard(surprise: fr.familia.app.core.model.SurprisesToConfirm, colors: PhotoColors, onOpen: () -> Unit) {
    val detail = if (surprise.count == 1) {
        surprise.firstTitle
    } else {
        pluralStringResource(R.plurals.home_photo_surprises_count, surprise.count, surprise.count)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .photoCard()
            .clickable(role = Role.Button, onClick = onOpen)
            .heightIn(min = 72.dp)
            .padding(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFE8D6))) {
            Text("🎁", fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics { })
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(stringResource(R.string.home_photo_surprise_day), color = PhotoBrown, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(detail, color = PhotoSoftBrown, fontSize = 14.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Text(
            stringResource(R.string.home_photo_surprise_confirm),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(20.dp)).background(colors.filled).padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** « Surprise de X : n idées à noter »: opens the surprise, on its « Propositions » before the day. */
@Composable
private fun PhotoSurpriseToRateCard(rate: PhotoSurpriseToRate, onOpen: () -> Unit) {
    val title = if (rate.beneficiaryName.isBlank()) {
        stringResource(R.string.home_photo_surprise_rate_anonymous)
    } else {
        stringResource(R.string.home_photo_surprise_rate_title, rate.beneficiaryName)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .photoCard()
            .clickable(role = Role.Button, onClick = onOpen)
            .heightIn(min = 72.dp)
            .padding(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF1E6))) {
            Text("🎁", fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics { })
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, color = PhotoBrown, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(pluralStringResource(R.plurals.home_photo_surprise_rate_count, rate.count, rate.count), color = PhotoSoftBrown, fontSize = 13.sp)
        }
        Text("›", color = PhotoBrown, fontSize = 20.sp, modifier = Modifier.width(16.dp).clearAndSetSemantics { })
    }
}

/** A key that stays the same for one answer while the list changes. */
private val HomeTodo.key: String
    get() = when (this) {
        is HomeTodo.Answer -> "event-" + event.id
        is HomeTodo.Help -> "help-" + request.id
        is HomeTodo.Vote -> "poll-" + poll.id
    }

/** An answer given on the Accueil: the event, the answer, and whether « Changer » shows the buttons again. */
private data class Given(val todo: HomeTodo.Answer, val going: Boolean, val changing: Boolean)
