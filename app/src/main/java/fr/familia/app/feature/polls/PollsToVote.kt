package fr.familia.app.feature.polls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.PollRepository
import fr.familia.app.core.model.Poll
import fr.familia.app.core.model.PollVote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

/**
 * How many open polls ask [memberId] and still wait for their own vote: the same rule as the
 * Accueil (pollsToVote), for the badge of « Sondages » in the Planning bar (BUG-1).
 */
fun pollsToVoteCount(memberId: String, polls: List<Poll>, votes: Map<String, List<PollVote>>, now: Instant): Int =
    polls.count { poll ->
        poll.isOpen(now) && memberId in poll.askedIds && votes[poll.id].orEmpty().none { it.memberId == memberId }
    }

@OptIn(ExperimentalCoroutinesApi::class)
class PollsToVoteViewModel(
    repository: PollRepository?,
    private val memberId: String,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {
    private fun <T> Flow<T>.orElse(value: T): Flow<T> = catch { e -> if (e is CancellationException) throw e else emit(value) }

    /** One listener on my polls, shared by [open] and [count] (note C du récit 75). */
    private val mine: Flow<List<Poll>>? = repository?.observeMine(memberId)
        ?.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** The open polls I see (récit 75): the card « Sondages » of the Planning shows only with one. */
    val open: StateFlow<Int> = (
        mine
            ?.map { list -> list.count { it.isOpen(clock()) } }
            ?.distinctUntilChanged()
            ?.orElse(0)
            ?: flowOf(0)
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val count: StateFlow<Int> = (
        mine?.let { shared ->
            val repo = repository ?: return@let null
            shared
                .map { list -> list.filter { it.isOpen(clock()) && memberId in it.askedIds } }
                .distinctUntilChanged()
                .flatMapLatest { asked ->
                    if (asked.isEmpty()) {
                        flowOf(0)
                    } else {
                        combine(asked.map { poll -> repo.observeVotes(poll.id).map { poll.id to it }.orElse(poll.id to emptyList()) }) {
                            pollsToVoteCount(memberId, asked, it.toMap(), clock())
                        }
                    }
                }
                .orElse(0)
        } ?: flowOf(0)
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

/** The polls of the card « Sondages » of the Planning (récit 75): those open, and those that wait for my vote. */
data class PollsSummary(val open: Int = 0, val toVote: Int = 0)

/** Both numbers, 0 while the module is off or nothing is open. */
@Composable
fun rememberPollsSummary(container: AppContainer?, memberId: String): PollsSummary {
    val viewModel: PollsToVoteViewModel = viewModel(
        key = "pollsToVote-$memberId",
        factory = viewModelFactory { initializer { PollsToVoteViewModel(container?.pollRepository, memberId) } },
    )
    val open by viewModel.open.collectAsStateWithLifecycle()
    val count by viewModel.count.collectAsStateWithLifecycle()
    return PollsSummary(open, count)
}

