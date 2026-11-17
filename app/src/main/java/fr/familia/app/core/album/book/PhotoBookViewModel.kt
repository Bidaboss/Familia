package fr.familia.app.core.album.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.MemberRepository
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

    /** No photo the member may put in a book, or the album could not be read. */
    data class Empty(val removedForChildren: Int = 0) : PhotoBookUiState

    data class Choosing(
        /** Every photo the member may use, in the order of the book: oldest first, then as moved. */
        val photos: List<BookPhoto>,
        val chosen: Set<String>,
        val coverId: String?,
        val title: String,
        val dates: String,
        val steps: List<BookStep>,
        /** The photos left out by the rule of the children (carte EXPERT-2), said on the screen. */
        val removedForChildren: Int,
        val format: PhotoBookFormat = PhotoBookFormat.Landscape,
        val isMaking: Boolean = false,
        val failed: Boolean = false,
    ) : PhotoBookUiState {
        val inBook: List<BookPhoto> get() = photos.filter { it.photo.id in chosen }
        val pageCount: Int get() = 1 + bookPages(inBook).size + if (steps.isEmpty()) 0 else 1
        val canMake: Boolean get() = chosen.isNotEmpty() && title.isNotBlank() && !isMaking
    }
}

/**
 * Le livre photo (récit 59): reads the album and the family once, keeps the photos the member may
 * print, lets them choose, order and enlarge, then [print] writes the PDF.
 */
class PhotoBookViewModel(
    private val photos: AlbumPhotoRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
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
        val memberRepo = members
        val loaded = if (albumRepo == null || memberRepo == null) null else try {
            withTimeoutOrNull(readTimeoutMillis) {
                val book = source() ?: return@withTimeoutOrNull null
                Triple(book, memberRepo.observeFamily().first(), albumRepo.observePhotos(book.folder).first())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (loaded == null) {
            _uiState.value = PhotoBookUiState.Empty()
            return
        }
        val (book, family, all) = loaded
        val (kept, removed) = all.partition { bookPhotoAllowed(it, currentMemberId, family) }
        val ordered = kept.sortedWith(compareBy<AlbumPhoto> { it.createdAt ?: Instant.MAX }.thenBy { it.id })
        _uiState.value = if (ordered.isEmpty()) {
            PhotoBookUiState.Empty(removed.size)
        } else {
            PhotoBookUiState.Choosing(
                photos = ordered.map { BookPhoto(it, caption(it)) },
                chosen = ordered.map { it.id }.toSet(),
                coverId = ordered.first().id,
                title = book.title.take(PHOTO_BOOK_TITLE_MAX),
                dates = book.dates,
                steps = book.steps,
                removedForChildren = removed.size,
            )
        }
    }

    private fun change(transform: (PhotoBookUiState.Choosing) -> PhotoBookUiState.Choosing) {
        val state = _uiState.value as? PhotoBookUiState.Choosing ?: return
        if (state.isMaking) return
        _uiState.value = transform(state).copy(failed = false)
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
        _uiState.value = state.copy(isMaking = true, failed = false)
        viewModelScope.launch {
            val file = try {
                print(content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            (_uiState.value as? PhotoBookUiState.Choosing)?.let { _uiState.value = it.copy(isMaking = false, failed = file == null) }
            _made.value = file
        }
    }

    fun shared() {
        _made.value = null
    }
}
