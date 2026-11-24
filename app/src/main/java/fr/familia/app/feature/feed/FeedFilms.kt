package fr.familia.app.feature.feed

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import fr.familia.app.R
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.data.TripFilm
import fr.familia.app.core.data.TripFilmRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.ui.formatTime
import fr.familia.app.core.ui.frenchDate
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

/**
 * The cards of the films of the day: one per film published since [since] on a step that still
 * exists, newest first, [limit] cards at most. A film just sent, with no server time yet, waits.
 */
fun filmCards(trips: List<Trip>, films: List<List<TripFilm>>, steps: List<List<TripStep>>, since: Instant, limit: Int): List<FilmFeedCard> =
    trips.indices.flatMap { i ->
        val places = steps[i].associate { it.id to it.place }
        films[i].mapNotNull { film ->
            val place = places[film.stepId]
            val at = film.createdAt
            if (place == null || at == null || at.isBefore(since)) null else FilmFeedCard(trips[i].id, trips[i].title, place, film)
        }
    }
        .sortedWith(compareByDescending<FilmFeedCard> { it.at }.thenBy { it.key })
        .take(limit)

/**
 * « Film du jour » (récit 108, PRD 156): the films of the day published by the participants, in the
 * fil voyage. The same trips as the words (feedWordTrips): a trip hidden from anyone never shows.
 */
class FilmFeedSource(
    private val trips: TripRepository?,
    private val films: TripFilmRepository?,
    private val family: Family,
    private val meId: String,
    private val today: LocalDate,
) : FeedSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> {
        val repository = trips
        val filmRepository = films
        if (repository == null || filmRepository == null) return flowOf(emptyList())
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
                            filmRepository.observe(trip.id).catch { e -> if (e is CancellationException) throw e else emit(emptyList()) },
                            repository.observeSteps(trip.id).catch { e -> if (e is CancellationException) throw e else emit(emptyList()) },
                        ) { f, s -> f to s }
                    }
                    combine(perTrip) { pairs -> filmCards(list, pairs.map { it.first }, pairs.map { it.second }, window.since, window.limit) }
                }
            }
    }
}

class FilmFeedCard(
    val tripId: String,
    val tripTitle: String,
    val place: String,
    val film: TripFilm,
) : FeedCard {
    override val key: String = "film:$tripId:${film.id}"
    override val at: Instant = film.createdAt ?: Instant.EPOCH
    override val authorId: String = film.authorId

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Green
        val author = context.member(film.authorId)
        FeedCardShell(
            tone = tone,
            // The step, where the film plays (StepFilm).
            onOpen = { navigation.onOpenWord(tripId, film.stepId) },
            header = {
                FeedHeader(
                    member = author,
                    name = author?.firstName ?: film.authorName,
                    time = formatTime(at.atZone(FAMILY_ZONE).toLocalTime()),
                    label = stringResource(R.string.feed_label_film),
                    tone = tone,
                )
            },
        ) {
            Text(
                stringResource(R.string.feed_word_where, place, tripTitle),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.feed_film_text, frenchDate(film.day, "d MMMM"), film.photoCount),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
