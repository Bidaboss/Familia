package fr.familia.app.core.album

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.photo.PhotoUploader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the module of the album lets the current member do: a participant of the trip, or later
 * a member who said « je viens », adds photos; the creator or the admin principal removes any.
 * [me] is null when the member is unknown, or the album is gone.
 */
data class AlbumRights(val me: Member?, val canAdd: Boolean, val canManage: Boolean)

/** What went wrong with the last photos, said under the photos until the next try. */
enum class PhotoProblem {
    /** A photo could not be read or sent: the others went on. */
    SendFailed,

    /** More photos were chosen than places left: only the first ones were kept. */
    TooMany,
    RemoveFailed,
}

data class AlbumPhotosUiState(
    val photos: List<AlbumPhoto> = emptyList(),
    val currentMemberId: String = "",
    /** Adds photos of their own. */
    val canAdd: Boolean = false,
    /** Removes any photo of the album. */
    val canManage: Boolean = false,
    /** Photos of this phone still being sent. */
    val sending: Int = 0,
    val problem: PhotoProblem? = null,
    /** Places of each member in this album: 30 for a trip or an event, 3 for a recipe. */
    val maxPerMember: Int = AlbumPhoto.MAX_PER_MEMBER,
) {
    /** Places left to this member, the photos being sent included. */
    val placesLeft: Int
        get() = (maxPerMember - photos.count { it.authorId == currentMemberId } - sending).coerceAtLeast(0)

    fun canRemove(photo: AlbumPhoto): Boolean = photo.authorId == currentMemberId || canManage
}

/**
 * The photos of one album. A member who may add photos adds up to 30 of their own; each is
 * reduced on the phone (about 300 KB, and a 30 KB thumbnail) and sent to Storage before its
 * record is written, so the family never sees an empty photo.
 */
class AlbumPhotosViewModel(
    private val photos: AlbumPhotoRepository?,
    rights: Flow<AlbumRights>?,
    private val uploader: PhotoUploader?,
    private val storage: PhotoStorage?,
    private val currentMemberId: String,
    private val folder: String,
    private val writeTimeoutMillis: Long = 4_000,
    private val maxPerMember: Int = AlbumPhoto.MAX_PER_MEMBER,
) : ViewModel() {

    private val sending = MutableStateFlow(0)
    private val problem = MutableStateFlow<PhotoProblem?>(null)

    /** Places taken by photos still being sent, so two quick choices never take the same one. */
    private val reserved = mutableSetOf<Int>()
    private var me: Member? = null
    private val empty = AlbumPhotosUiState(currentMemberId = currentMemberId, maxPerMember = maxPerMember)

    val uiState: StateFlow<AlbumPhotosUiState> =
        if (photos == null || rights == null) {
            MutableStateFlow(empty) // Firebase not configured (previews).
        } else {
            combine(photos.observePhotos(folder), rights, sending, problem) { list, r, n, p ->
                me = r.me
                if (r.me == null) empty else AlbumPhotosUiState(list, currentMemberId, r.canAdd, r.canManage, n, p, maxPerMember)
            }
                // The screen of the module says when it cannot be read; here, simply no photos.
                .catch { e -> if (e is CancellationException) throw e else emit(empty) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), empty)
        }

    /**
     * Sends the photos chosen on the phone ([sources] are content URIs), one after the other, each
     * in a free place of this member. Beyond the places left, the extra photos are not sent.
     */
    fun addPhotos(sources: List<String>) {
        val repo = photos ?: return
        val upload = uploader ?: return
        val member = me ?: return
        val state = uiState.value
        if (sources.isEmpty() || !state.canAdd) return
        val taken = state.photos.filter { it.authorId == member.id }.map { it.slot }.toSet() + reserved
        val chosen = sources.zip(AlbumPhoto.freeSlots(taken, maxPerMember))
        if (chosen.isEmpty()) {
            problem.value = PhotoProblem.TooMany
            return
        }
        reserved += chosen.map { it.second }
        sending.update { it + chosen.size }
        problem.value = if (chosen.size < sources.size) PhotoProblem.TooMany else null
        viewModelScope.launch {
            for ((source, slot) in chosen) {
                val photo = AlbumPhoto(folder, member.id, member.firstName, slot, createdAt = null)
                try {
                    upload.upload(source, photo.photoPath, photo.thumbnailPath)
                    // The files are there: the record may wait offline, Firestore sends it later.
                    withTimeoutOrNull(writeTimeoutMillis) { repo.addPhoto(folder, slot, member) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    problem.value = PhotoProblem.SendFailed
                } finally {
                    reserved -= slot
                    sending.update { it - 1 }
                }
            }
        }
    }

    /** Removes a photo: its two files first, then its record, so nobody sees a broken photo. */
    fun removePhoto(photo: AlbumPhoto) {
        val repo = photos ?: return
        if (!uiState.value.canRemove(photo)) return
        problem.value = null
        viewModelScope.launch {
            try {
                storage?.let { files ->
                    for (path in listOf(photo.photoPath, photo.thumbnailPath)) {
                        // Already gone (a send cut in the middle): nothing to remove.
                        runCatching { files.delete(path) }.onFailure { if (it is CancellationException) throw it }
                    }
                }
                withTimeoutOrNull(writeTimeoutMillis) { repo.deletePhoto(photo) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem.value = PhotoProblem.RemoveFailed
            }
        }
    }

    fun dismissProblem() {
        problem.value = null
    }
}

/** The actions of the photo views for [viewModel], given once per ViewModel. */
@Composable
fun rememberPhotoActions(viewModel: AlbumPhotosViewModel, onSeeAll: () -> Unit = {}): PhotoActions =
    remember(viewModel, onSeeAll) {
        PhotoActions(
            onAdd = viewModel::addPhotos,
            onRemove = viewModel::removePhoto,
            onSeeAll = onSeeAll,
            onDismissProblem = viewModel::dismissProblem,
        )
    }
