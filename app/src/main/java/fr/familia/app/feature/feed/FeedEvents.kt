package fr.familia.app.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.R
import fr.familia.app.core.data.EventCommentRepository
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
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> =
        events.observeCreatedSince(window.since, window.limit)
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
                        )
                    }
                }
            }
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
) : FeedCard {
    override val key: String = "event:${event.id}"
    override val at: Instant = event.createdAt ?: Instant.EPOCH
    override val authorId: String = event.authorId

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Lilac
        val day = feedEventDay(event, context.today)
        var answered by remember(event.id) { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val last = remember(event.id, comments) {
            comments?.observe(event.id, 1)?.map { it.comments.lastOrNull() }?.catch { emit(null) } ?: flowOf(null)
        }
        val lastComment by last.collectAsStateWithLifecycle(initialValue = null)
        FeedCardShell(
            tone = tone,
            onOpen = { navigation.onOpenEvent(event.id) },
            // Retour d'essai (20/11): no photo, name or time of who added it, only the label.
            header = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FeedLabel(stringResource(R.string.feed_label_planning), tone)
                }
            },
        ) {
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
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.feed_event_going)) }
                }
            }
            lastComment?.let { FeedLastComment(it.authorName, it.text) }
        }
    }
}
