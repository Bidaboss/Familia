package fr.familia.app.feature.trips.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.FILM_MIN_PHOTOS
import fr.familia.app.core.album.FilmState
import fr.familia.app.core.album.albumPhotoOrder
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.video.FilmStyle
import fr.familia.app.core.video.PhotoFilmMaker
import fr.familia.app.feature.trips.detail.placeName
import fr.familia.app.feature.trips.detail.tripTimeline
import fr.familia.app.feature.trips.word.STEP_FILM_SECONDS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.floor

/** One image of the film of a trip: the name of a step, or one of its photos. */
sealed interface TripFilmShot {
    data class Title(val name: String, val number: Int) : TripFilmShot
    data class Photo(val photo: AlbumPhoto) : TripFilmShot
}

/** The film of a trip, shot by shot, with the time of each image. */
data class TripFilmPlan(val shots: List<TripFilmShot>, val secondsPerShot: Double) {
    val photoCount: Int get() = shots.count { it is TripFilmShot.Photo }
    val seconds: Double get() = shots.size * secondsPerShot
}

/** The film of the trip lasts this long at most (PRD 158). */
const val TRIP_FILM_MAX_SECONDS = 180.0

/** Under this, a photo goes by too fast to be seen: the photos are thinned out instead. */
const val TRIP_FILM_MIN_SECONDS = 1.2

/**
 * The film of the trip made of the films of its steps (récit 108, règle 32): for each step of « Le
 * trajet » that has photos, its name, then its photos oldest first; the photos of no step at the
 * end. 2,5 s an image, less for a long trip so that it stays under 3 min; past 1,2 s, the photos
 * of each step are thinned out evenly, never under one a step. Null when there are not 2 photos.
 */
fun tripFilmPlan(trip: Trip, steps: List<TripStep>, photos: List<AlbumPhoto>): TripFilmPlan? {
    if (photos.size < FILM_MIN_PHOTOS) return null
    val ordered = photos.sortedWith(albumPhotoOrder).reversed()
    val stepIds = steps.map { it.id }.toSet()
    val groups = mutableListOf<Pair<TripFilmShot.Title?, List<AlbumPhoto>>>()
    var number = 0
    tripTimeline(trip, steps).rows.forEach { row ->
        val own = ordered.filter { it.stepId == row.step.id }
        if (own.isNotEmpty()) groups += TripFilmShot.Title(placeName(row.step), ++number) to own
    }
    val loose = ordered.filter { it.stepId == null || it.stepId !in stepIds }
    if (loose.isNotEmpty()) groups += null to loose
    val maxShots = floor(TRIP_FILM_MAX_SECONDS / TRIP_FILM_MIN_SECONDS).toInt()
    val titles = groups.count { it.first != null }
    val total = titles + ordered.size
    val kept = if (total <= maxShots) {
        groups
    } else {
        // Every step keeps at least one photo; the rest is shared out as the photos were.
        val room = (maxShots - titles).coerceAtLeast(groups.size)
        groups.map { (title, list) -> title to thin(list, (list.size.toDouble() * room / ordered.size).toInt().coerceIn(1, list.size)) }
    }
    val shots = kept.flatMap { (title, list) -> listOfNotNull(title) + list.map { TripFilmShot.Photo(it) } }
    val seconds = (TRIP_FILM_MAX_SECONDS / shots.size).coerceIn(TRIP_FILM_MIN_SECONDS, STEP_FILM_SECONDS)
    return TripFilmPlan(shots, seconds)
}

/** [count] photos of [list], evenly spread, the first and the last kept. */
internal fun <T> thin(list: List<T>, count: Int): List<T> {
    if (count >= list.size) return list
    if (count <= 1) return list.take(1)
    return (0 until count).map { i -> list[(i.toDouble() * (list.size - 1) / (count - 1)).toInt()] }
}

/** Draws the card of a step, 1280 × 720, on the dark blue of the trips: its number, then its name. */
fun writeTitleCard(context: Context, title: TripFilmShot.Title, dir: File): File {
    val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.rgb(0x17, 0x33, 0x3F))
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(0xFF, 0xD8, 0x4A)
        textSize = 44f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 88f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val name = fitted(title.name, big, 1160f)
    canvas.drawText(context.getString(fr.familia.app.R.string.trip_film_step_number, title.number), 640f, 300f, small)
    canvas.drawText(name, 640f, 420f, big)
    dir.mkdirs()
    val file = File(dir, "titre-${title.number}.jpg")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    bitmap.recycle()
    return file
}

/** The name cut with « … » when it is wider than the card. */
private fun fitted(text: String, paint: Paint, width: Float): String {
    if (paint.measureText(text) <= width) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.take(end) + "…") > width) end--
    return text.take(end).trimEnd() + "…"
}

/** The film of a trip, made on the phone with the look of the trips; the same states as the film of an album. */
class TripFilmViewModel(
    private val storage: PhotoStorage?,
    private val maker: PhotoFilmMaker?,
    private val tripId: String,
    private val filmsDir: File,
    /** Writes the card of a step and gives its address; on Android, [writeTitleCard]. */
    private val titleCard: (TripFilmShot.Title, File) -> File,
) : ViewModel() {
    private val state = MutableStateFlow<FilmState>(FilmState.Idle)
    val uiState: StateFlow<FilmState> = state.asStateFlow()
    private var job: Job? = null

    fun make(plan: TripFilmPlan?) {
        val files = storage ?: return
        val film = maker ?: return
        if (plan == null || state.value == FilmState.Making) return
        state.value = FilmState.Making
        job = viewModelScope.launch {
            state.value = try {
                val cards = File(filmsDir, "titres-$tripId")
                val urls = plan.shots.map { shot ->
                    when (shot) {
                        is TripFilmShot.Title -> withContext(Dispatchers.IO) { titleCard(shot, cards).toURI().toString() }
                        is TripFilmShot.Photo -> files.downloadUrl(shot.photo.photoPath)
                    }
                }
                val output = File(filmsDir, "voyage-$tripId.mp4")
                film.make(urls, plan.secondsPerShot, output, FilmStyle.Trip)
                FilmState.Ready(output.absolutePath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FilmState.Failed
            }
        }
    }

    fun cancel() {
        job?.cancel()
        state.value = FilmState.Idle
    }
}
