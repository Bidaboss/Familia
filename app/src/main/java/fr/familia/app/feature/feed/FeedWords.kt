package fr.familia.app.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.data.TripWordRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.model.TripWord
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.trips.tripAudience
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate

/** The trips whose words the fil listens to, at most: one listener each (récit 88, 4/4). */
const val FEED_WORDS_MAX = 20

/** A trip whose steps are older than the window by more than this still gets new words. */
const val FEED_WORD_MARGIN_DAYS = 30L

/**
 * The trips whose words the fil may show, before any word is read: those the whole [tripAudience]
 * reads (a surprise, or any trip hidden from someone, never shows, even to those who follow it: a
 * phone shown around must never give it away), started by [today] and not ended before [since]
 * less a margin, the most recent first, [FEED_WORDS_MAX] at most.
 */
fun feedWordTrips(trips: List<Trip>, family: Family, since: LocalDate, today: LocalDate): List<Trip> {
    val from = since.minusDays(FEED_WORD_MARGIN_DAYS)
    val audience = tripAudience(family)
    return trips
        .filter { trip -> audience.all { it.id in trip.readerIds } && !trip.startDate.isAfter(today) && !trip.endDate.isBefore(from) }
        .sortedWith(compareByDescending<Trip> { it.startDate }.thenBy { it.id })
        .take(FEED_WORDS_MAX)
}

/**
 * The cards of the words: one per word published since [since] on a step that still exists, newest
 * first, [limit] cards at most. A word just sent, with no server time yet, waits for it.
 */
fun wordCards(trips: List<Trip>, words: List<Map<String, TripWord>>, steps: List<List<TripStep>>, since: Instant, limit: Int): List<WordFeedCard> =
    trips.indices.flatMap { i ->
        val places = steps[i].associate { it.id to it.place }
        words[i].values.mapNotNull { word ->
            val place = places[word.stepId]
            val at = word.createdAt
            if (place == null || at == null || at.isBefore(since)) null else WordFeedCard(trips[i].id, trips[i].title, place, word)
        }
    }
        .sortedWith(compareByDescending<WordFeedCard> { it.at }.thenBy { it.key })
        .take(limit)

/**
 * « Le mot de l'étape » (récit 88, 4/4): the words the participants publish, in the fil voyage. Only
 * for the trips the whole family follows, under the switches of the fil and of the trips.
 */
class WordFeedSource(
    private val trips: TripRepository?,
    private val words: TripWordRepository?,
    private val family: Family,
    private val meId: String,
    private val today: LocalDate,
) : FeedSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> {
        val repository = trips
        val wordRepository = words
        if (repository == null || wordRepository == null) return flowOf(emptyList())
        val sinceDay = window.since.atZone(FAMILY_ZONE).toLocalDate()
        return repository.observeFollowedTrips(meId).catch { emit(emptyList()) }
            .distinctUntilChanged()
            .flatMapLatest { followed ->
                val list = feedWordTrips(followed, family, sinceDay, today)
                if (list.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    // A trip that cannot be read counts as empty: the rest of the fil still shows.
                    val perTrip = list.map { trip ->
                        combine(
                            wordRepository.observeWords(trip.id).catch { e -> if (e is CancellationException) throw e else emit(emptyMap()) },
                            repository.observeSteps(trip.id).catch { e -> if (e is CancellationException) throw e else emit(emptyList()) },
                        ) { w, s -> w to s }
                    }
                    combine(perTrip) { pairs -> wordCards(list, pairs.map { it.first }, pairs.map { it.second }, window.since, window.limit) }
                }
            }
    }
}

class WordFeedCard(
    val tripId: String,
    val tripTitle: String,
    val place: String,
    val word: TripWord,
) : FeedCard {
    override val key: String = "word:$tripId:${word.stepId}"
    override val at: Instant = word.createdAt ?: Instant.EPOCH
    override val authorId: String = word.authorId

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Green
        val author = context.member(word.authorId)
        FeedCardShell(
            tone = tone,
            onOpen = { navigation.onOpenWord(tripId, word.stepId) },
            header = {
                FeedHeader(
                    member = author,
                    name = author?.firstName ?: word.authorName,
                    action = stringResource(R.string.feed_action_word),
                    emoji = "🧳",
                    time = formatTime(at.atZone(FAMILY_ZONE).toLocalTime()),
                )
            },
        ) {
            Text(
                stringResource(R.string.feed_word_where, place, tripTitle),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(word.text, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (word.photoIds.isNotEmpty() || word.heartIds.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (word.photoIds.isNotEmpty()) {
                        Text(pluralStringResource(R.plurals.feed_word_photos, word.photoIds.size, word.photoIds.size), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (word.heartIds.isNotEmpty()) {
                        Text(pluralStringResource(R.plurals.feed_word_hearts, word.heartIds.size, word.heartIds.size), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
