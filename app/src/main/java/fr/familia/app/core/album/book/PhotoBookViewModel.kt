package fr.familia.app.core.album.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.print.PrintException
import fr.familia.app.core.print.PrintProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant

/** What the album is: read by the route from the trip or the event. */
data class BookSource(val folder: String, val title: String, val dates: String, val steps: List<BookStep> = emptyList())

sealed interface PhotoBookUiState {
    data object Loading : PhotoBookUiState

    /** No photo in the album yet, or the album could not be read. */
    data object Empty : PhotoBookUiState

    data class Choosing(
        /** Every photo of the album, in the order of the book: oldest first, then as moved. */
        val photos: List<BookPhoto>,
        val chosen: Set<String>,
        val coverId: String?,
        val title: String,
        val dates: String,
        val steps: List<BookStep>,
        val format: PhotoBookFormat = PhotoBookFormat.Landscape,
        val isMaking: Boolean = false,
        val failed: Boolean = false,
        /** The room or the memory of the phone, when that is why it failed; else the connection. */
        val problem: PrintProblem? = null,
    ) : PhotoBookUiState {
        val inBook: List<BookPhoto> get() = photos.filter { it.photo.id in chosen }
        val pageCount: Int get() = 1 + bookPages(inBook).size + if (steps.isEmpty()) 0 else 1
        val canMake: Boolean get() = chosen.isNotEmpty() && title.isNotBlank() && !isMaking
    }
}

/**
 * Le livre photo (récit 59): reads the album once, lets the member choose, order and enlarge its
 * photos, then [print] writes the PDF.
 */
class PhotoBookViewModel(
    private val photos: AlbumPhotoRepository?,
    /** The title, the dates and the steps of the album, or null when they cannot be read. */
    private val source: suspend () -> BookSource?,
    private val caption: (AlbumPhoto) -> String = { bookCaption(it) },
    /** Writes the book and gives its file, or null when it failed. */
    private val print: suspend (PhotoBookContent) -> File?,
    private val readTimeoutMillis: Long = 10_000,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PhotoBookUiState>(PhotoBookUiState.Loading)
    val uiState: StateFlow<PhotoBookUiState> = _uiState.asStateFlow()

    private val _made = MutableStateFlow<File?>(null)

    /** The book just made, for the share sheet; [shared] clears it. */
    val made: StateFlow<File?> = _made.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val albumRepo = photos
        val loaded = if (albumRepo == null) null else try {
            withTimeoutOrNull(readTimeoutMillis) {
                val book = source() ?: return@withTimeoutOrNull null
                book to albumRepo.observePhotos(book.folder).first()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (loaded == null) {
            _uiState.value = PhotoBookUiState.Empty
            return
        }
        val (book, all) = loaded
        val ordered = all.sortedWith(compareBy<AlbumPhoto> { it.createdAt ?: Instant.MAX }.thenBy { it.id })
        _uiState.value = if (ordered.isEmpty()) {
            PhotoBookUiState.Empty
        } else {
            PhotoBookUiState.Choosing(
                photos = ordered.map { BookPhoto(it, caption(it)) },
                chosen = ordered.map { it.id }.toSet(),
                coverId = ordered.first().id,
                title = book.title.take(PHOTO_BOOK_TITLE_MAX),
                dates = book.dates,
                steps = book.steps,
            )
        }
    }

    private fun change(transform: (PhotoBookUiState.Choosing) -> PhotoBookUiState.Choosing) {
        val state = _uiState.value as? PhotoBookUiState.Choosing ?: return
        if (state.isMaking) return
        _uiState.value = transform(state).copy(failed = false, problem = null)
    }

    fun setTitle(title: String) = change { it.copy(title = title.take(PHOTO_BOOK_TITLE_MAX)) }

    fun setFormat(format: PhotoBookFormat) = change { it.copy(format = format) }

    /** Takes a photo in or out; the cover goes to the first photo left when it is taken out. */
    fun toggle(id: String) = change { state ->
        val chosen = if (id in state.chosen) state.chosen - id else state.chosen + id
        val cover = state.coverId?.takeIf { it in chosen } ?: state.photos.firstOrNull { it.photo.id in chosen }?.photo?.id
        state.copy(chosen = chosen, coverId = cover)
    }

    fun setCover(id: String) = change { if (id in it.chosen) it.copy(coverId = id) else it }

    fun toggleBig(id: String) = change { state ->
        state.copy(photos = state.photos.map { if (it.photo.id == id) it.copy(big = !it.big) else it })
    }

    /** Moves a photo one place earlier (-1) or later (+1) in the book. */
    fun move(id: String, by: Int) = change { state ->
        val from = state.photos.indexOfFirst { it.photo.id == id }
        val to = from + by
        if (from < 0 || to !in state.photos.indices) {
            state
        } else {
            state.copy(photos = state.photos.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    fun make() {
        val state = _uiState.value as? PhotoBookUiState.Choosing ?: return
        if (!state.canMake) return
        val content = PhotoBookContent(
            title = state.title.trim(),
            dates = state.dates,
            format = state.format,
            cover = state.photos.firstOrNull { it.photo.id == state.coverId }?.photo,
            pages = bookPages(state.inBook),
            steps = state.steps,
        )
        _uiState.value = state.copy(isMaking = true, failed = false, problem = null)
        viewModelScope.launch {
            var problem: PrintProblem? = null
            val file = try {
                print(content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PrintException) {
                problem = e.problem
                null
            } catch (e: Exception) {
                null
            }
            (_uiState.value as? PhotoBookUiState.Choosing)?.let { _uiState.value = it.copy(isMaking = false, failed = file == null, problem = problem) }
            _made.value = file
        }
    }

    fun shared() {
        _made.value = null
    }
}
