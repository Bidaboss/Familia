package fr.familia.app.core.album

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.PhotoRefusedException
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.photo.PhotoUploader
import fr.familia.app.core.photo.UnreadablePhotoException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    /** A photo could not be sent (network): the others went on. */
    SendFailed,

    /** The chosen file is not a picture the phone can read. */
    Unreadable,

    /** The server refused the photo: retrying will not help, someone must look. */
    Refused,

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
    /** Photos of this send already gone, out of [sent] + [sending]: the bar of progress. */
    val sent: Int = 0,
) {
    /** From 0 to 1, for the bar of progress. */
    val sendProgress: Float
        get() = if (sending == 0) 0f else sent.toFloat() / (sent + sending)

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
    private val savedState: SavedStateHandle = SavedStateHandle(),
    /** [AlbumSends.shared] in the app, so leaving the screen never stops a send; else this screen's own. */
    sends: AlbumSends? = null,
) : ViewModel() {

    private val sends: AlbumSends = sends ?: AlbumSends(viewModelScope)

    private val queueKey = "album_queue_$folder"

    /**
     * Photos chosen and not sent yet, kept with the screen: Android may stop the app while the
     * gallery is open, or while a long list of photos goes. They leave once sent, or refused.
     */
    private var queue: List<String>
        get() = savedState.get<ArrayList<String>>(queueKey).orEmpty()
        set(value) {
            savedState[queueKey] = ArrayList(value)
        }

    /** The step each photo of [queue] was chosen from, in a trip (récit 104), kept with the queue. */
    private var queueSteps: Map<String, String>
        get() = savedState.get<HashMap<String, String>>("album_steps_$folder").orEmpty()
        set(value) {
            savedState["album_steps_$folder"] = HashMap(value)
        }

    /** Photos of [queue] that wait for the album to be read (the app has just been reopened). */
    private var waiting: List<String> = queue

    private var me: Member? = null
    private val empty = AlbumPhotosUiState(currentMemberId = currentMemberId, maxPerMember = maxPerMember)

    val uiState: StateFlow<AlbumPhotosUiState> =
        if (photos == null || rights == null) {
            MutableStateFlow(empty) // Firebase not configured (previews).
        } else {
            combine(photos.observePhotos(folder), rights, this.sends.progress(folder)) { list, r, progress ->
                me = r.me
                if (r.me != null && r.canAdd && waiting.isNotEmpty()) {
                    val ready = waiting
                    waiting = emptyList()
                    send(ready, list, r.me)
                }
                if (r.me == null) {
                    empty
                } else {
                    AlbumPhotosUiState(list, currentMemberId, r.canAdd, r.canManage, progress.slots.size, progress.problem, maxPerMember, progress.done)
                }
            }
                // The screen of the module says when it cannot be read; here, simply no photos.
                .catch { e -> if (e is CancellationException) throw e else emit(empty) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), empty)
        }

    /**
     * Sends the photos chosen on the phone ([sources] are content URIs), one after the other, each
     * in a free place of this member. Beyond the places left, the extra photos are not sent.
     */
    fun addPhotos(sources: List<String>) = addPhotos(sources, null)

    /** The same, from the step [stepId] of a trip: the photos are filed under it (récit 104, règle 31). */
    fun addPhotos(sources: List<String>, stepId: String?) {
        if (photos == null || uploader == null || sources.isEmpty()) return
        if (stepId != null) queueSteps = queueSteps + sources.associateWith { stepId }
        val member = me
        if (member == null) {
            // The album is not read yet (the app has just been reopened on the gallery's answer).
            queue = (queue + sources).distinct()
            waiting = (waiting + sources).distinct()
            return
        }
        val state = uiState.value
        if (!state.canAdd) return
        send(sources, state.photos, member)
    }

    private fun send(sources: List<String>, album: List<AlbumPhoto>, member: Member) {
        val repo = photos ?: return
        val upload = uploader ?: return
        val taken = album.filter { it.authorId == member.id }.map { it.slot }.toSet() + sends.current(folder).slots
        val chosen = sources.zip(AlbumPhoto.freeSlots(taken, maxPerMember))
        queue = (queue + chosen.map { it.first }).distinct() - sources.drop(chosen.size).toSet()
        queueSteps = queueSteps - sources.drop(chosen.size).toSet()
        if (chosen.isEmpty()) {
            sends.setProblem(folder, PhotoProblem.TooMany)
            return
        }
        sends.start(folder, chosen.map { it.second })
        sends.setProblem(folder, if (chosen.size < sources.size) PhotoProblem.TooMany else null)
        // Not on the screen's scope: leaving the album never stops the photos (the evening of a party).
        sends.launch {
            for ((source, slot) in chosen) {
                val photo = AlbumPhoto(folder, member.id, member.firstName, slot, createdAt = null)
                try {
                    val takenAt = upload.takenAt(source)
                    upload.upload(source, photo.photoPath, photo.thumbnailPath)
                    // The files are there: the record may wait offline, Firestore sends it later.
                    val stepId = queueSteps[source]
                    withTimeoutOrNull(writeTimeoutMillis) {
                        if (stepId == null) repo.addPhoto(folder, slot, member, takenAt) else repo.addPhoto(folder, slot, member, takenAt, stepId)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    sends.setProblem(
                        folder,
                        when (e) {
                            is UnreadablePhotoException -> PhotoProblem.Unreadable
                            is PhotoRefusedException -> PhotoProblem.Refused
                            else -> PhotoProblem.SendFailed
                        },
                    )
                } finally {
                    queue = queue - source
                    queueSteps = queueSteps - source
                    sends.finish(folder, slot)
                }
            }
        }
    }

    /** Removes a photo: its two files first, then its record, so nobody sees a broken photo. */
    fun removePhoto(photo: AlbumPhoto) {
        val repo = photos ?: return
        if (!uiState.value.canRemove(photo)) return
        sends.setProblem(folder, null)
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
                sends.setProblem(folder, PhotoProblem.RemoveFailed)
            }
        }
    }

    fun dismissProblem() {
        sends.setProblem(folder, null)
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
