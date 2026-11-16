package fr.familia.app.feature.feedback

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.FeedbackRepository
import fr.familia.app.core.model.Feedback
import fr.familia.app.core.model.FeedbackContext
import fr.familia.app.core.model.Member
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class FeedbackFormState(
    val kind: Feedback.Kind = Feedback.Kind.Idea,
    val text: String = "",
    val isSending: Boolean = false,
    val sent: Boolean = false,
    val failed: Boolean = false,
    /** The screenshot chosen on the phone, as a content URI, before it is sent. */
    val screenshot: String? = null,
    /** False when Storage is not there: no button to add one. */
    val canAddScreenshot: Boolean = false,
) {
    val canSend: Boolean get() = text.isNotBlank() && text.trim().length <= Feedback.MAX_TEXT && !isSending
}

/**
 * « Une idée ? Un souci ? » (PRD, récit 6 sexies). [context] is added on its own: the versions,
 * the phone and the screen it came from. The draft is kept in [savedState], so a text half
 * written survives Android stopping the app. Offline, Firestore keeps it and sends it later.
 */
class FeedbackFormViewModel(
    private val feedback: FeedbackRepository?,
    private val me: Member,
    private val context: FeedbackContext,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    /** Turns the chosen picture into a JPEG of at most 150 KB; null when there is no Storage. */
    private val encodeScreenshot: (suspend (String) -> ByteArray)? = null,
    private val writeTimeoutMillis: Long = 4_000,
    private val uploadTimeoutMillis: Long = 30_000,
) : ViewModel() {

    private val status = MutableStateFlow(FeedbackFormState(canAddScreenshot = encodeScreenshot != null))

    val uiState: StateFlow<FeedbackFormState> = combine(
        status,
        savedState.getStateFlow(KIND, Feedback.Kind.Idea.key),
        savedState.getStateFlow(TEXT, ""),
        savedState.getStateFlow<String?>(SHOT, null),
    ) { s, kind, text, shot ->
        s.copy(kind = Feedback.Kind.fromKey(kind) ?: Feedback.Kind.Idea, text = text, screenshot = shot.takeIf { encodeScreenshot != null })
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeedbackFormState(canAddScreenshot = encodeScreenshot != null))

    fun choose(kind: Feedback.Kind) {
        savedState[KIND] = kind.key
    }

    fun type(text: String) {
        savedState[TEXT] = text.take(Feedback.MAX_TEXT)
        status.value = status.value.copy(failed = false)
    }

    /** A picture chosen in the photo picker, or null to take it off. */
    fun pickScreenshot(uri: String?) {
        savedState[SHOT] = uri
        status.value = status.value.copy(failed = false)
    }

    fun send() {
        val repo = feedback ?: return
        val state = uiState.value
        if (!state.canSend) return
        status.value = status.value.copy(isSending = true, failed = false)
        viewModelScope.launch {
            try {
                val shot = state.screenshot
                val encode = encodeScreenshot
                if (shot != null && encode != null) {
                    // The picture needs the network: a request that cannot go is said, not kept.
                    val bytes = encode(shot)
                    withTimeoutOrNull(uploadTimeoutMillis) { repo.send(state.kind, state.text.trim(), me, context, bytes) }
                        ?: throw IllegalStateException("Too slow")
                } else {
                    // Offline, Firestore keeps it and sends it later.
                    withTimeoutOrNull(writeTimeoutMillis) { repo.send(state.kind, state.text.trim(), me, context) }
                }
                savedState[TEXT] = ""
                savedState[SHOT] = null
                status.value = status.value.copy(isSending = false, sent = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.value = status.value.copy(isSending = false, failed = true)
            }
        }
    }

    /** « Envoyer une autre demande » after the thanks. */
    fun again() {
        status.value = FeedbackFormState(canAddScreenshot = encodeScreenshot != null)
    }

    private companion object {
        const val KIND = "feedback_kind"
        const val TEXT = "feedback_text"
        const val SHOT = "feedback_screenshot"
    }
}

sealed interface FeedbackListState {
    data object Loading : FeedbackListState
    data object Error : FeedbackListState
    data class Ready(val items: List<Feedback>) : FeedbackListState
}

private fun listState(source: Flow<List<Feedback>>?): Flow<FeedbackListState> =
    (source ?: flowOf(emptyList()))
        .map<List<Feedback>, FeedbackListState> { FeedbackListState.Ready(it) }
        .catch { e -> if (e is CancellationException) throw e else emit(FeedbackListState.Error) }

/** « Retours » in Administration, for the admin principal: a state and a word for each one. */
class FeedbackListViewModel(private val feedback: FeedbackRepository?, private val writeTimeoutMillis: Long = 4_000) : ViewModel() {

    private val _actionFailed = MutableStateFlow(false)
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()

    val uiState: StateFlow<FeedbackListState> = listState(feedback?.observeAll())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedbackListState.Loading)

    fun answer(id: String, status: Feedback.Status, note: String) {
        val repo = feedback ?: return
        write { repo.setStatus(id, status, note.trim().takeIf { it.isNotEmpty() }?.take(Feedback.MAX_NOTE)) }
    }

    fun remove(id: String) {
        val repo = feedback ?: return
        val shot = (uiState.value as? FeedbackListState.Ready)?.items?.firstOrNull { it.id == id }?.screenshot
        write { repo.delete(id, shot) }
    }

    fun failureShown() {
        _actionFailed.value = false
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                withTimeoutOrNull(writeTimeoutMillis) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _actionFailed.value = true
            }
        }
    }
}

/** « Mes demandes »: what the member sent, and the answer of the admin principal. */
class MyFeedbackViewModel(feedback: FeedbackRepository?, memberId: String) : ViewModel() {
    val uiState: StateFlow<FeedbackListState> = listState(feedback?.observeMine(memberId))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedbackListState.Loading)
}
