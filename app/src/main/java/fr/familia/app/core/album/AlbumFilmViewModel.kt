package fr.familia.app.core.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.video.FilmStyle
import fr.familia.app.core.video.PhotoFilmMaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Where the film of the album is. */
sealed interface FilmState {
    data object Idle : FilmState
    data object Making : FilmState

    /** Made on this phone, ready to watch or share: [path] is in the cache folder films/. */
    data class Ready(val path: String) : FilmState
    data object Failed : FilmState
}

/** A film needs at least this many photos. */
const val FILM_MIN_PHOTOS = 2

/**
 * The film of an album (PRD, récit 16 for a trip, later 41 and 42): all its photos, oldest first,
 * made on the phone. About a minute long whatever the number of photos, each shown between 1.5
 * and 3 seconds. Nothing is sent: sharing goes through the reminder of EXPERT-6, then the share
 * sheet of Android. The film of [fileName] is replaced each time it is made again.
 */
class AlbumFilmViewModel(
    private val storage: PhotoStorage?,
    private val maker: PhotoFilmMaker?,
    private val fileName: String,
    private val filmsDir: File,
    /** The look of the film: still photos for the albums, the slow zoom of the trips (récit 108). */
    private val style: FilmStyle = FilmStyle.Album,
    /** The time of each photo for this many photos: about a minute in all for an album. */
    private val seconds: (Int) -> Double = ::secondsPerPhoto,
) : ViewModel() {

    private val state = MutableStateFlow<FilmState>(FilmState.Idle)
    val uiState: StateFlow<FilmState> = state.asStateFlow()
    private var job: Job? = null

    fun make(photos: List<AlbumPhoto>) {
        val files = storage ?: return
        val film = maker ?: return
        if (photos.size < FILM_MIN_PHOTOS || state.value == FilmState.Making) return
        state.value = FilmState.Making
        job = viewModelScope.launch {
            state.value = try {
                val ordered = photos.sortedWith(albumPhotoOrder).reversed()
                val urls = ordered.map { files.downloadUrl(it.photoPath) }
                val output = File(filmsDir, "$fileName.mp4")
                film.make(urls, seconds(ordered.size), output, style)
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

    companion object {
        private const val TARGET_SECONDS = 60.0

        fun secondsPerPhoto(count: Int): Double = (TARGET_SECONDS / count.coerceAtLeast(1)).coerceIn(1.5, 3.0)
    }
}
