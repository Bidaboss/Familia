package fr.familia.app.feature.events.comments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.CommentPage
import fr.familia.app.core.data.EventCommentRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.EventComment
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.canEditComment
import fr.familia.app.core.model.canRemoveComment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A comment, and what the current member may do with it. */
data class EventCommentRow(val comment: EventComment, val canEdit: Boolean, val canRemove: Boolean)

enum class EventCommentProblem { SendFailed, EditFailed, RemoveFailed, MuteFailed }

data class EventCommentsUiState(
    val rows: List<EventCommentRow> = emptyList(),
    val hasMore: Boolean = false,
    val isLoading: Boolean = true,
    val draft: String = "",
    val isSending: Boolean = false,
    /** « Ne plus être prévenu » for this event. */
    val muted: Boolean = false,
    val problem: EventCommentProblem? = null,
) {
    val canSend: Boolean get() = draft.isNotBlank() && draft.trim().length <= EventComment.MAX_TEXT && !isSending
}

/**
 * The comments under an event (PRD, récit 28): every member reads them and writes their own. The
 * 30 most recent show first, « Voir plus » adds 30 older ones. The draft is kept in [savedState],
 * so a comment half written survives Android stopping the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventCommentsViewModel(
    private val comments: EventCommentRepository?,
    members: MemberRepository?,
    private val currentMemberId: String,
    private val eventId: String,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val writeTimeoutMillis: Long = 4_000,
    /** Who removes a comment: for an event, [canRemoveComment]; a good news has its own rule (récit 67). */
    private val canRemove: (Family, EventComment, Member) -> Boolean = { family, comment, me -> family.canRemoveComment(comment, me) },
) : ViewModel() {

    private val draft = savedState.getStateFlow(DRAFT, "")
    private val limit = savedState.getStateFlow(LIMIT, EventComment.PAGE)
    private val sending = MutableStateFlow(false)
    private val problem = MutableStateFlow<EventCommentProblem?>(null)
    private var me: Member? = null

    val uiState: StateFlow<EventCommentsUiState> =
        if (comments == null || members == null) {
            MutableStateFlow(EventCommentsUiState(isLoading = false)) // Firebase not configured (previews).
        } else {
            val page = limit.flatMapLatest { comments.observe(eventId, it) }
            val local = combine(draft, sending, problem) { text, isSending, issue -> Triple(text, isSending, issue) }
            combine(page, members.observeFamily(), comments.observeMuted(eventId, currentMemberId), local) {
                    shown: CommentPage, family, muted, (text, isSending, issue) ->
                val current = family.members.firstOrNull { it.id == currentMemberId }
                me = current
                EventCommentsUiState(
                    rows = shown.comments.map { c ->
                        EventCommentRow(
                            c,
                            canEdit = current != null && canEditComment(c, current),
                            canRemove = current != null && canRemove(family, c, current),
                        )
                    },
                    hasMore = shown.hasMore,
                    isLoading = false,
                    draft = text,
                    isSending = isSending,
                    muted = muted,
                    problem = issue,
                )
            }
                .catch { e -> if (e is CancellationException) throw e else emit(EventCommentsUiState(isLoading = false)) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventCommentsUiState())
        }

    fun type(text: String) {
        savedState[DRAFT] = text.take(EventComment.MAX_TEXT)
        problem.value = null
    }

    fun showMore() {
        savedState[LIMIT] = limit.value + EventComment.PAGE
    }

    /** Sends the draft; offline, Firestore keeps it and sends it later. */
    fun send() {
        val repo = comments ?: return
        val author = me ?: return
        val state = uiState.value
        if (!state.canSend) return
        val text = state.draft.trim()
        sending.value = true
        viewModelScope.launch {
            try {
                withTimeoutOrNull(writeTimeoutMillis) { repo.add(eventId, text, author) }
                savedState[DRAFT] = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem.value = EventCommentProblem.SendFailed
            } finally {
                sending.value = false
            }
        }
    }

    /** Returns false when [text] cannot be saved (empty or too long), so the dialog stays open. */
    fun edit(row: EventCommentRow, text: String): Boolean {
        val repo = comments ?: return false
        val clean = text.trim()
        if (!row.canEdit || clean.isEmpty() || clean.length > EventComment.MAX_TEXT) return false
        if (clean == row.comment.text) return true
        write(EventCommentProblem.EditFailed) { repo.edit(eventId, row.comment.id, clean) }
        return true
    }

    fun remove(row: EventCommentRow) {
        val repo = comments ?: return
        if (!row.canRemove) return
        write(EventCommentProblem.RemoveFailed) { repo.delete(eventId, row.comment.id) }
    }

    fun setMuted(muted: Boolean) {
        val repo = comments ?: return
        if (muted == uiState.value.muted) return
        write(EventCommentProblem.MuteFailed) { repo.setMuted(eventId, currentMemberId, muted) }
    }

    fun dismissProblem() {
        problem.value = null
    }

    private fun write(onFailure: EventCommentProblem, block: suspend () -> Unit) {
        problem.value = null
        viewModelScope.launch {
            try {
                withTimeoutOrNull(writeTimeoutMillis) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem.value = onFailure
            }
        }
    }

    private companion object {
        const val DRAFT = "event_comment_draft"
        const val LIMIT = "event_comment_limit"
    }
}
