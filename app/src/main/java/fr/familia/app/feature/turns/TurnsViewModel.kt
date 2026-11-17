package fr.familia.app.feature.turns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TurnRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Turn
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

/** One rotation in the list: whose turn it is, by name, and whether it is my household's. */
data class TurnRow(val turn: Turn, val nowName: String, val isMine: Boolean, val swapForMe: Boolean)

sealed interface TurnsUiState {
    data object Loading : TurnsUiState
    data object Error : TurnsUiState

    /** [rows]: mine first (my household's turn, or an exchange waiting for us), then by title. */
    data class Success(val rows: List<TurnRow>, val canCreate: Boolean) : TurnsUiState
}

/** The name of household [id]; blank for one removed meanwhile (the screen says « Foyer retiré »). */
internal fun householdName(family: Family, id: String?): String = family.households.firstOrNull { it.id == id }?.name.orEmpty()

internal fun turnRows(turns: List<Turn>, family: Family, myHouseholdId: String): List<TurnRow> =
    turns.map { t ->
        TurnRow(t, householdName(family, t.currentHouseholdId), t.currentHouseholdId == myHouseholdId, t.swap?.toId == myHouseholdId)
    }.sortedWith(compareBy<TurnRow>({ !(it.isMine || it.swapForMe) }, { it.turn.title.lowercase() }))

/** « À qui le tour ? » in Entraide (PRD, récit 58). */
@OptIn(ExperimentalCoroutinesApi::class)
class TurnsViewModel(
    private val turns: TurnRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
) : ViewModel() {

    private val retries = MutableStateFlow(0)

    val uiState: StateFlow<TurnsUiState> = retries
        .flatMapLatest { load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TurnsUiState.Loading)

    private fun load(): Flow<TurnsUiState> {
        val turnRepo = turns ?: return flowOf(TurnsUiState.Error)
        val memberRepo = members ?: return flowOf(TurnsUiState.Error)
        return combine(turnRepo.observeAll(), memberRepo.observeFamily()) { list, family ->
            val me = family.members.firstOrNull { it.id == currentMemberId }
            if (me == null) TurnsUiState.Error else TurnsUiState.Success(turnRows(list, family, me.householdId), canCreateTurn(me))
        }.catch { emit(TurnsUiState.Error) }
    }

    fun retry() {
        retries.value++
    }
}
