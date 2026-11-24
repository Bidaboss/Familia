package fr.familia.app.feature.trips.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.R
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.FILM_MIN_PHOTOS
import fr.familia.app.core.album.albumPhotoOrder
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.data.FilmPublication
import fr.familia.app.core.data.TripFilm
import fr.familia.app.core.data.TripFilmRepository
import fr.familia.app.core.data.TripFilms
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.share.FilmDownloadLine
import fr.familia.app.core.share.FilmSource
import fr.familia.app.core.share.galleryFilmName
import fr.familia.app.core.share.playVideoUrl
import fr.familia.app.core.share.rememberFilmDownload
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.core.video.FilmStyle
import fr.familia.app.core.video.PhotoFilmMaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import kotlin.math.ceil

// --- Film de la journée (récit 108, règle 32, PRD 156 à 158) ---------------------------------------

/** The day of a photo: the day of Paris it was added, as the rest of the app counts days. */
fun photoDay(photo: AlbumPhoto): LocalDate? = photo.createdAt?.atZone(FAMILY_ZONE)?.toLocalDate()

/** The photos of the film of [day]: those of the step added that day. */
fun dayFilmPhotos(stepPhotos: List<AlbumPhoto>, day: LocalDate): List<AlbumPhoto> = stepPhotos.filter { photoDay(it) == day }

/**
 * The day whose film can be published from this step: the most recent day of the trip, up to
 * [today], with at least 2 photos of the step and no film yet. Null when there is none.
 */
fun dayToPublish(stepPhotos: List<AlbumPhoto>, films: List<TripFilm>, stepId: String, trip: Trip, today: LocalDate): LocalDate? {
    val published = films.filter { it.stepId == stepId }.map { it.day }.toSet()
    return stepPhotos.mapNotNull(::photoDay)
        .groupingBy { it }.eachCount()
        .filter { (day, count) ->
            count >= FILM_MIN_PHOTOS && day !in published && !day.isAfter(today) && !day.isBefore(trip.startDate) && !day.isAfter(trip.endDate)
        }
        .keys.maxOrNull()
}

/** The reminder of the next day (PRD 157): a line in the step, no notice, for a day or two only. */
const val DAY_FILM_REMINDER_DAYS = 2L

fun remindsDayFilm(day: LocalDate?, today: LocalDate): Boolean =
    day != null && day.isBefore(today) && !day.isBefore(today.minusDays(DAY_FILM_REMINDER_DAYS))

/** How long the film of [count] photos lasts, in whole seconds, as its record says. */
fun dayFilmSeconds(count: Int): Int = ceil(count * STEP_FILM_SECONDS).toInt().coerceIn(1, TripFilms.MAX_SECONDS)

/** The films published for [stepId], the most recent day first. */
fun stepFilms(films: List<TripFilm>, stepId: String): List<TripFilm> = films.filter { it.stepId == stepId }.sortedByDescending { it.day }

sealed interface DayFilmState {
    data object Idle : DayFilmState
    data class Working(val day: LocalDate) : DayFilmState
    data class Done(val day: LocalDate, val alreadyThere: Boolean) : DayFilmState

    /** [tooBig]: over the 10 Mo the rules allow. */
    data class Failed(val tooBig: Boolean = false) : DayFilmState
}

/**
 * « Publier le film de la journée » (PRD 156): makes the film of the photos of that day on this
 * phone, then sends it once for all the followers. A film already published by someone else for the
 * day stays; nothing of ours is left in Storage (C2 of #559).
 */
class DayFilmPublisher(
    private val storage: PhotoStorage?,
    private val maker: PhotoFilmMaker?,
    private val films: TripFilmRepository?,
    private val tripId: String,
    private val stepId: String,
    private val filmsDir: File,
) : ViewModel() {

    private val state = MutableStateFlow<DayFilmState>(DayFilmState.Idle)
    val uiState: StateFlow<DayFilmState> = state.asStateFlow()
    private var job: Job? = null

    val canPublish: Boolean get() = storage != null && maker != null && films != null

    fun publish(me: Member, day: LocalDate, stepPhotos: List<AlbumPhoto>) {
        val files = storage ?: return
        val film = maker ?: return
        val repository = films ?: return
        val photos = dayFilmPhotos(stepPhotos, day).sortedWith(albumPhotoOrder).reversed()
        if (photos.size < FILM_MIN_PHOTOS || state.value is DayFilmState.Working) return
        state.value = DayFilmState.Working(day)
        job = viewModelScope.launch {
            val output = File(filmsDir, "jour-$tripId-${TripFilms.id(stepId, day)}.mp4")
            state.value = try {
                val urls = photos.take(TripFilms.MAX_PHOTOS).map { files.downloadUrl(it.photoPath) }
                film.make(urls, STEP_FILM_SECONDS, output, FilmStyle.Trip)
                if (output.length() > TripFilms.MAX_BYTES) {
                    DayFilmState.Failed(tooBig = true)
                } else {
                    val result = repository.publish(tripId, me, stepId, day, output, dayFilmSeconds(urls.size), urls.size)
                    DayFilmState.Done(day, alreadyThere = result == FilmPublication.AlreadyThere)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DayFilmState.Failed()
            } finally {
                output.delete()
            }
        }
    }

    fun cancel() {
        job?.cancel()
        state.value = DayFilmState.Idle
    }
}

/**
 * Under the film of the step: the reminder of the next day, the publication under way, and the
 * films published for the step, to watch from Storage (PRD 156).
 */
@Composable
fun DayFilmsPart(
    state: DayFilmState,
    published: List<TripFilm>,
    reminderDay: LocalDate?,
    onPublish: (LocalDate) -> Unit,
    onCancel: () -> Unit,
    playUrl: suspend (TripFilm) -> String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val download = rememberFilmDownload()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            is DayFilmState.Working -> {
                Text(stringResource(R.string.day_film_sending, frenchDate(state.day, "d MMMM")), style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.album_film_cancel)) }
            }
            is DayFilmState.Done -> Text(
                stringResource(if (state.alreadyThere) R.string.day_film_already else R.string.day_film_done, frenchDate(state.day, "d MMMM")),
                style = MaterialTheme.typography.bodyMedium,
            )
            is DayFilmState.Failed -> Text(
                stringResource(if (state.tooBig) R.string.day_film_too_big else R.string.day_film_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            DayFilmState.Idle -> Unit
        }
        if (reminderDay != null && state !is DayFilmState.Working) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.day_film_reminder, frenchDate(reminderDay, "d MMMM")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onPublish(reminderDay) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.day_film_publish_short))
                }
            }
        }
        published.forEach { film ->
            val day = frenchDate(film.day, "d MMMM")
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { scope.launch { playUrl(film)?.let { playVideoUrl(context, it) } } },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.day_film_published, day, film.authorName), modifier = Modifier.weight(1f))
                }
                // « Télécharger » (règle 32): the published film into the gallery, from Storage.
                if (download != null) {
                    IconButton(
                        onClick = { download.download(FilmSource.Published({ playUrl(film) }, galleryFilmName("film", film.id))) },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(Icons.Outlined.SaveAlt, contentDescription = stringResource(R.string.film_download_film, day))
                    }
                }
            }
        }
        FilmDownloadLine(download)
    }
}
