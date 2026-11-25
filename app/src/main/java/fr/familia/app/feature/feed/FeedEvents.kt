package fr.familia.app.feature.feed

import fr.familia.app.core.data.gazetteThreadId
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.EventCommentRepository
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.RsvpRepository
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.core.model.nextBirthday
import fr.familia.app.core.ui.dayShortLabel
import fr.familia.app.core.ui.formatTime
import fr.familia.app.core.ui.frenchDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * « Nouvel événement du Planning » (récit 67): the events put in the Planning in the window, read
 * like the Planning reads them (every member reads every event). The events of a series are left
 * out: one weekly event would fill the fil. [comments] is null while the comments are off.
 */
class EventFeedSource(
    private val events: EventRepository,
    private val rsvps: RsvpRepository?,
    private val comments: EventCommentRepository?,
    private val meId: String,
) : FeedSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> {
        val today = window.now.atZone(FAMILY_ZONE).toLocalDate()
        // The events of the next 3 days come back up (récit 116, règle 2), even planned long ago.
        val soon = events.observeBetween(today, today.plusDays(RESURFACE_DAYS)).catch { emit(emptyList()) }
        return combine(events.observeCreatedSince(window.since, window.limit), soon) { created, coming ->
            (created + coming).distinctBy { it.id }
        }
            .map(::newEvents)
            .flatMapLatest { list ->
                val answers = rsvps?.observeAnswersOf(meId, list.map { it.id })?.catch { emit(emptyMap()) } ?: flowOf(null)
                answers.map { answered ->
                    list.map { event ->
                        EventFeedCard(
                            event = event,
                            // Unknown answers (no rsvps, or not readable): never ask.
                            canAnswer = rsvps != null && answered != null && event.id !in answered,
                            rsvps = rsvps,
                            comments = comments,
                            at = feedEventAt(event, window.now),
                        )
                    }
                        // An event of the next days still before its 8 h stays out until it comes back up.
                        .filter { !it.at.isBefore(window.since) }
                }
            }
    }
}

/** How many days before its date an event comes back up in la gazette (règle 2). */
const val RESURFACE_DAYS = 3L

/** An event comes back up at 8 h, 3 days before its date (règle 2). */
private val RESURFACE_TIME = java.time.LocalTime.of(8, 0)

/**
 * When the card of [event] stands in la gazette: when it was planned, then, from 8 h three days before
 * its date, at that time: the same card comes back up (règle 2: « la carte remonte », never a second
 * one). An event planned in those 3 days stays where it was planned.
 */
fun feedEventAt(event: Event, now: Instant): Instant {
    val planned = event.createdAt ?: Instant.EPOCH
    val back = event.date.minusDays(RESURFACE_DAYS).atTime(RESURFACE_TIME).atZone(FAMILY_ZONE).toInstant()
    return if (!now.isBefore(back) && planned.isBefore(back)) back else planned
}

/** The events of the fil: with their creation time, and not one of a series. */
fun newEvents(events: List<Event>): List<Event> = events.filter { it.createdAt != null && it.seriesId == null }

/**
 * The day the card shows. An event dated more than a year ago is an anniversary typed with its first
 * date (« Anniversaire de … » on the birth date, retour d'essai 20/11): it shows its next return,
 * and « J'y vais » stays hidden since the event itself is past.
 */
fun feedEventDay(event: Event, today: LocalDate): LocalDate =
    if (event.date.isBefore(today.minusYears(1))) nextBirthday(event.date, today) else event.date

class EventFeedCard(
    val event: Event,
    /** I have not answered yet: « J'y vais » shows while the event is to come. */
    val canAnswer: Boolean,
    private val rsvps: RsvpRepository?,
    private val comments: EventCommentRepository?,
    /** Where it stands in la gazette (feedEventAt): when planned, or 3 days before. */
    override val at: Instant = event.createdAt ?: Instant.EPOCH,
) : FeedCard {
    override val key: String = "event:${event.id}"
    override val authorId: String = event.authorId

    /** J'aime on its thread; its comments stay those of the event (récit 28), off with them. */
    override val social: FeedSocial = FeedSocial(gazetteThreadId(key), own = comments?.let { EventOwnComments(it, event.id) }, commentable = false)

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Lilac
        val day = feedEventDay(event, context.today)
        var answered by remember(event.id) { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        FeedCardShell(
            tone = tone,
            onOpen = { navigation.onOpenEvent(event.id) },
        ) {
            // Retour d'essai (20/11): one row centered, [day] [PLANNING / title / when] [J'y vais], the
            // small label at the top of the title like « ANNIVERSAIRE », no header line of its own.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    Modifier
                        .widthIn(min = 46.dp)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tone.labelBackground)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        frenchDate(day, "EEE").removeSuffix(".").uppercase(Locale.FRANCE),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = tone.label,
                    )
                    Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = tone.label)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.feed_label_planning).uppercase(Locale.FRANCE),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = tone.label,
                    )
                    Text(
                        event.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val dayLabel = dayShortLabel(day, context.today)
                    Text(
                        event.time?.let { "$dayLabel · ${formatTime(it)}" } ?: dayLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canAnswer && !answered && !context.readOnly && rsvps != null && !event.date.isBefore(context.today)) {
                    Button(
                        onClick = {
                            answered = true
                            scope.launch {
                                try {
                                    rsvps.setRsvp(event.id, context.me.id, RsvpStatus.Yes, context.me.id)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    answered = false
                                }
                            }
                        },
                        modifier = Modifier.heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.feed_event_going)) }
                }
            }
        }
    }
}
