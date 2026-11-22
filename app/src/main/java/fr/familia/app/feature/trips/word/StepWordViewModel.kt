package fr.familia.app.feature.trips.word

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.data.TripWordRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.model.TripWord
import fr.familia.app.feature.trips.isParticipant
import fr.familia.app.feature.trips.photos.tripAlbumFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the writer has typed and chosen so far, before it is saved. */
data class WordDraft(val text: String, val photoIds: List<String>)

sealed interface StepWordUiState {
    data object Loading : StepWordUiState

    /** The trip or the step is gone, or this member no longer follows the trip. */
    data object Missing : StepWordUiState
    data object Error : StepWordUiState

    data class Ready(
        val step: TripStep,
        /** « Étape 2 sur 5 ». */
        val rank: Int,
        val total: Int,
        val word: TripWord?,
        /** The photos of the trip album, to pick from or to show. */
        val album: List<AlbumPhoto>,
        /** Who follows the trip, participants included. */
        val followers: Int,
        val currentMemberId: String,
        /** Writes the word: a participant, and for a word already there, only its author. */
        val canWrite: Boolean,
        /** A participant changes the step itself. */
        val isParticipant: Boolean,
        val draft: WordDraft,
        val hasChanges: Boolean,
        /** Those who put a heart, to show their faces and names, the current member first. */
        val hearts: List<Member>,
        val busy: Boolean = false,
        val sendFailed: Boolean = false,
    ) : StepWordUiState {
        val canPublish: Boolean get() = TripWord.clean(draft.text) != null && hasChanges
        val hearted: Boolean get() = word?.hearted(currentMemberId) == true
    }
}

/**
 * « Le mot de l'étape » (récit 88): the page of one step, to write its word and pick its photos
 * (a participant) or to read it, put a heart on it (a follower). Offline, a write shows at once and
 * leaves when the network is back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StepWordViewModel(
    private val trips: TripRepository?,
    private val words: TripWordRepository?,
    private val albums: AlbumPhotoRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val tripId: String,
    private val stepId: String,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)
    private val draft = MutableStateFlow<WordDraft?>(null)
    private val busy = MutableStateFlow(false)
    private val sendFailed = MutableStateFlow(false)
    private var me: Member? = null
    private var current: StepWordUiState.Ready? = null

    /** True once a word is published, changed or taken back: the page can close. */
    val done = MutableStateFlow(false)

    val uiState: StateFlow<StepWordUiState> = attempt
        .flatMapLatest { n ->
            if (trips == null || words == null || members == null) {
                flowOf(StepWordUiState.Missing) // Firebase not configured (previews).
            } else {
                val album: Flow<List<AlbumPhoto>> = (albums?.observePhotos(tripAlbumFolder(tripId)) ?: flowOf(emptyList()))
                    .catch { emit(emptyList()) } // An album that cannot be read leaves the word without photos.
                val data = combine(trips.observeTrip(tripId), trips.observeSteps(tripId), words.observeWord(tripId, stepId), album, members.observeFamily()) {
                        trip, steps, word, photos, family ->
                    Source(trip, steps, word, photos, family)
                }
                combine(data, draft, busy, sendFailed) { source, edit, isBusy, failed -> state(source, edit, isBusy, failed) }
                    .onStart { if (n > 0) emit(StepWordUiState.Loading) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(StepWordUiState.Error)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StepWordUiState.Loading)

    fun retry() {
        attempt.update { it + 1 }
    }

    fun setText(text: String) {
        val ready = current ?: return
        draft.value = ready.draft.copy(text = text.take(TripWord.MAX_TEXT))
    }

    /** Touches a photo of the album: chosen (up to 4), or taken off. */
    fun togglePhoto(photoId: String) {
        val ready = current ?: return
        draft.value = ready.draft.copy(photoIds = TripWord.toggled(ready.draft.photoIds, photoId))
    }

    fun publish() {
        val ready = current ?: return
        val member = me ?: return
        val text = TripWord.clean(ready.draft.text) ?: return
        if (!ready.canWrite) return
        // A removed photo drops out, so the word keeps only photos still in the album.
        val photoIds = ready.draft.photoIds.filter { id -> ready.album.any { it.id == id } }
        write(closes = true) { words!!.saveWord(tripId, stepId, text, photoIds, member, isNew = ready.word == null) }
    }

    /** « Retirer mon mot »: its hearts and comments go with it. */
    fun remove() {
        val ready = current ?: return
        if (!ready.canWrite || ready.word == null) return
        write(closes = true) { words!!.deleteWord(tripId, stepId) }
    }

    /** The heart of this member: put, or taken back. */
    fun toggleHeart() {
        val ready = current ?: return
        if (ready.word == null) return
        write(closes = false) { words!!.setHeart(tripId, stepId, currentMemberId, !ready.hearted) }
    }

    private fun write(closes: Boolean, block: suspend () -> Unit) {
        sendFailed.value = false
        busy.value = true
        viewModelScope.launch {
            val failed = try {
                // Still pending after the timeout: queued offline, Firestore sends it later.
                withTimeoutOrNull(writeTimeoutMillis) { block() }
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                true
            }
            busy.value = false
            sendFailed.value = failed
            if (!failed && closes) done.value = true
        }
    }

    private class Source(val trip: Trip?, val steps: List<TripStep>, val word: TripWord?, val album: List<AlbumPhoto>, val family: Family)

    private fun state(source: Source, edit: WordDraft?, isBusy: Boolean, failed: Boolean): StepWordUiState {
        val member = source.family.members.firstOrNull { it.id == currentMemberId }
        val step = source.steps.firstOrNull { it.id == stepId }
        if (source.trip == null || member == null || step == null) return StepWordUiState.Missing
        me = member
        val word = source.word
        val isParticipant = isParticipant(source.trip, member)
        val saved = WordDraft(word?.text.orEmpty(), word?.photoIds.orEmpty())
        val shown = edit ?: saved
        val byId = source.family.members.associateBy { it.id }
        // The current member's face comes first, then the others in the order of the family.
        val hearts = word?.heartIds.orEmpty().mapNotNull { byId[it] }.sortedBy { if (it.id == currentMemberId) 0 else 1 }
        return StepWordUiState.Ready(
            step = step,
            rank = source.steps.indexOfFirst { it.id == stepId } + 1,
            total = source.steps.size,
            word = word,
            album = source.album,
            followers = source.trip.readerIds.count { it in byId },
            currentMemberId = currentMemberId,
            canWrite = isParticipant && (word == null || word.authorId == currentMemberId),
            isParticipant = isParticipant,
            draft = shown,
            hasChanges = edit != null && edit != saved,
            hearts = hearts,
            busy = isBusy,
            sendFailed = failed,
        ).also { current = it }
    }
}
