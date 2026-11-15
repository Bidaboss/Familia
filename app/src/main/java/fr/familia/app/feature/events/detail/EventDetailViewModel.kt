package fr.familia.app.feature.events.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.RsvpRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.feature.events.answerableMembers
import fr.familia.app.feature.events.authorDisplayName
import fr.familia.app.feature.events.canManage
import fr.familia.app.feature.events.edit.TargetChoice
import fr.familia.app.feature.events.targetName
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
import java.time.LocalDate

/**
 * One event, read only, with "qui vient" (PRD, récit 4): the member answers for themselves and
 * for their children. Everyone can open it; only those who may manage it see "Modifier".
 * Offline, an answer shows at once from the cache and leaves when the network is back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModel(
    private val events: EventRepository?,
    private val members: MemberRepository?,
    private val rsvps: RsvpRepository?,
    private val currentMemberId: String,
    private val eventId: String,
    private val writeTimeoutMillis: Long = 4_000,
    private val today: Flow<LocalDate> = currentDayFlow(),
) : ViewModel() {

    private val attempt = MutableStateFlow(0)
    private val sendFailed = MutableStateFlow(false)

    val uiState: StateFlow<EventDetailUiState> = attempt
        .flatMapLatest { n ->
            if (events == null || members == null || rsvps == null) {
                flowOf(EventDetailUiState.Missing) // Firebase not configured (previews).
            } else {
                combine(
                    events.observeEvent(eventId),
                    members.observeFamily(),
                    rsvps.observeRsvps(eventId),
                    today,
                    sendFailed,
                ) { event, family, answers, day, failed -> state(event, family, answers, day, failed) }
                    .onStart { if (n > 0) emit(EventDetailUiState.Loading) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(EventDetailUiState.Error)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventDetailUiState.Loading)

    fun retry() {
        attempt.update { it + 1 }
    }

    /** Answers for [memberId] (the member signed in, or one of their children). */
    fun answer(memberId: String, status: RsvpStatus) {
        val repo = rsvps ?: return
        viewModelScope.launch {
            sendFailed.value = try {
                // Offline the write never confirms: after a few seconds it is queued, not failed.
                withTimeoutOrNull(writeTimeoutMillis) { repo.setRsvp(eventId, memberId, status, currentMemberId) }
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                true
            }
        }
    }

    private fun state(event: Event?, family: Family, answers: List<Rsvp>, day: LocalDate, failed: Boolean): EventDetailUiState {
        if (event == null) return EventDetailUiState.Missing
        val me = family.members.firstOrNull { it.id == currentMemberId } ?: return EventDetailUiState.Missing
        val byMember = answers.associateBy { it.memberId }
        // Answers of removed members are not shown: they are cleaned up on the server (lot 8).
        fun members(status: RsvpStatus) = family.members
            .filter { byMember[it.id]?.status == status }
            .sortedBy { it.firstName.lowercase() }
        fun names(status: RsvpStatus) = members(status).map { it.firstName }
        val isMe = event.target is EventTarget.ForSelf && event.target.memberId == currentMemberId
        return EventDetailUiState.Success(
            event = event,
            today = day,
            target = TargetChoice(event.target, if (isMe) null else targetName(event.target, family)),
            authorName = authorDisplayName(event, family),
            canManage = canManage(event, me, family),
            answerers = answerableMembers(me, family).map { Answerer(it, byMember[it.id]?.status, it.id == me.id) },
            coming = names(RsvpStatus.Yes),
            maybe = names(RsvpStatus.Maybe),
            notComing = names(RsvpStatus.No),
            comingMembers = members(RsvpStatus.Yes),
            sendFailed = failed,
        )
    }
}
