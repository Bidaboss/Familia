package fr.familia.app.feature.turns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TurnRepository
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Turn
import fr.familia.app.core.model.TurnDone
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A household in the order, by name, with whether it is mine and whether its turn it is. */
data class TurnPlace(val householdId: String, val name: String, val isMine: Boolean, val isNow: Boolean)

/** A line of the history, newest first: « Noël 2026 chez … ». */
data class TurnHistoryLine(val done: TurnDone, val name: String)

sealed interface TurnDetailUiState {
    data object Loading : TurnDetailUiState
    data object Missing : TurnDetailUiState
    data object Error : TurnDetailUiState

    data class Success(
        val turn: Turn,
        val nowName: String,
        val nextName: String,
        val order: List<TurnPlace>,
        val history: List<TurnHistoryLine>,
        val rights: TurnRights,
        /** The households I can propose to change places with: the others of the rotation, by name. */
        val swapTargets: List<Household>,
        /** The name of the other household of the pending exchange, as I see it. */
        val swapOtherName: String = "",
        val isWorking: Boolean = false,
        val failed: Boolean = false,
    ) : TurnDetailUiState
}

internal fun turnDetail(turn: Turn, family: fr.familia.app.core.model.Family, me: Member): TurnDetailUiState.Success {
    val rights = turnRights(turn, me, family.ownerId)
    val names = family.households.associate { it.id to it.name }
    val swap = turn.swap
    return TurnDetailUiState.Success(
        turn = turn,
        nowName = names[turn.currentHouseholdId].orEmpty(),
        nextName = names[turn.nextHouseholdId].orEmpty(),
        order = turn.householdIds.mapIndexed { i, id -> TurnPlace(id, names[id].orEmpty(), id == me.householdId, i == turn.current) },
        history = turn.history.asReversed().map { TurnHistoryLine(it, names[it.householdId].orEmpty()) },
        rights = rights,
        swapTargets = if (!rights.askSwap) {
            emptyList()
        } else {
            turn.householdIds.filter { it != me.householdId }
                .mapNotNull { id -> family.households.firstOrNull { it.id == id } }
                .sortedBy { it.name.lowercase() }
        },
        swapOtherName = when {
            swap == null -> ""
            swap.toId == me.householdId -> names[swap.fromId].orEmpty()
            else -> names[swap.toId].orEmpty()
        },
    )
}

/** One rotation: whose turn it is, the order, the history, and what my household can do. */
@OptIn(ExperimentalCoroutinesApi::class)
class TurnDetailViewModel(
    private val turns: TurnRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val turnId: String,
) : ViewModel() {

    private val retries = MutableStateFlow(0)
    private val working = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)
    private var me: Member? = null

    val uiState: StateFlow<TurnDetailUiState> = retries
        .flatMapLatest { load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TurnDetailUiState.Loading)

    private fun load(): Flow<TurnDetailUiState> {
        val turnRepo = turns ?: return flowOf(TurnDetailUiState.Error)
        val memberRepo = members ?: return flowOf(TurnDetailUiState.Error)
        return combine(turnRepo.observe(turnId), memberRepo.observeFamily(), working, failed) { turn, family, isWorking, hasFailed ->
            val member = family.members.firstOrNull { it.id == currentMemberId }
            me = member
            when {
                member == null -> TurnDetailUiState.Error
                turn == null -> TurnDetailUiState.Missing
                else -> turnDetail(turn, family, member).copy(isWorking = isWorking, failed = hasFailed)
            }
        }.catch { emit(TurnDetailUiState.Error) }
    }

    fun retry() {
        retries.value++
    }

    fun dismissFailure() {
        failed.value = false
    }

    private fun act(block: suspend (TurnRepository, Turn) -> Unit) {
        val s = uiState.value as? TurnDetailUiState.Success ?: return
        val repo = turns ?: return
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            try {
                block(repo, s.turn)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed.value = true
            } finally {
                working.value = false
            }
        }
    }

    /** « C'est fait », with an optional label (« Noël 2026 »). */
    fun done(label: String) = act { repo, t -> repo.done(t, label) }

    fun skip() = act { repo, t -> repo.skip(t) }

    fun askSwap(toHouseholdId: String) = act { repo, t -> repo.askSwap(t, me ?: error("No member"), toHouseholdId) }

    fun acceptSwap() = act { repo, t -> repo.acceptSwap(t) }

    /** « Non merci » from the other household, or « Retirer » from mine. */
    fun dropSwap() = act { repo, t -> repo.dropSwap(t.id) }

    fun delete(onDeleted: () -> Unit) = act { repo, t ->
        repo.delete(t.id)
        onDeleted()
    }
}
