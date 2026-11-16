package fr.familia.app.feature.polls.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.PollRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Poll
import fr.familia.app.core.model.PollVote
import fr.familia.app.core.model.leaders
import fr.familia.app.feature.polls.canAskPoll
import fr.familia.app.feature.polls.pollAnswerers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate

/** « Sondages »: the polls I was asked, or asked, or answer for my children (PRD, récits 2 bis et 2 ter). */
@OptIn(ExperimentalCoroutinesApi::class)
class PollsViewModel(
    private val polls: PollRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val now: () -> Instant = Instant::now,
    private val today: Flow<LocalDate> = currentDayFlow(),
) : ViewModel() {

    private val retries = MutableStateFlow(0)

    val uiState: StateFlow<PollsUiState> = retries
        .flatMapLatest { load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PollsUiState.Loading)

    private fun load(): Flow<PollsUiState> {
        val pollRepo = polls ?: return flowOf(PollsUiState.Error)
        val memberRepo = members ?: return flowOf(PollsUiState.Error)
        // A family has a handful of polls: one listener on the votes of each.
        val withVotes: Flow<List<Pair<Poll, List<PollVote>>>> = pollRepo.observeMine(currentMemberId).flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(emptyList<Pair<Poll, List<PollVote>>>())
            } else {
                combine(list.map { poll -> pollRepo.observeVotes(poll.id) }) { votes -> list.zip(votes.toList()) }
            }
        }
        return combine(withVotes, memberRepo.observeFamily(), today) { list, family, _ -> build(list, family) }
            .catch { emit(PollsUiState.Error) }
    }

    fun retry() {
        retries.value++
    }

    private fun build(list: List<Pair<Poll, List<PollVote>>>, family: Family): PollsUiState {
        val me = family.members.firstOrNull { it.id == currentMemberId } ?: return PollsUiState.Error
        val at = now()
        val memberIds = family.members.map { it.id }.toSet()
        val rows = list.map { (poll, votes) ->
            val counted = votes.filter { it.memberId in poll.askedIds && it.memberId in memberIds }
            val answered = counted.map { it.memberId }.toSet()
            val isOpen = poll.isOpen(at)
            val waiting = if (isOpen) pollAnswerers(poll, me, family).filter { it.id !in answered } else emptyList()
            PollRow(
                poll = poll,
                authorName = family.members.firstOrNull { it.id == poll.authorId }?.firstName ?: poll.authorName,
                isMine = poll.authorId == me.id,
                isOpen = isOpen,
                meToAnswer = waiting.any { it.id == me.id },
                childrenToAnswer = waiting.filter { it.id != me.id }.map { it.firstName },
                answeredCount = answered.size,
                askedCount = poll.askedIds.count { it in memberIds },
                leaders = if (isOpen) emptyList() else leaders(poll, counted).sorted(),
            )
        }
        val (open, closed) = rows.partition { it.isOpen }
        return PollsUiState.Success(
            open = open.sortedByDescending { it.meToAnswer || it.childrenToAnswer.isNotEmpty() },
            closed = closed,
            canAsk = canAskPoll(me),
        )
    }
}
