package fr.familia.app.feature.gooddeals.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GoodDealRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GoodDeal
import fr.familia.app.core.model.GoodDealKind
import fr.familia.app.feature.gooddeals.canPublishGoodDeal
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

/** One bon plan in the list, with what the reader needs to know at a glance. */
data class GoodDealRow(val deal: GoodDeal, val isMine: Boolean, val iAmInterested: Boolean)

sealed interface GoodDealsUiState {
    data object Loading : GoodDealsUiState
    data object Error : GoodDealsUiState

    /** The bons plans still running, the one ending first on top (récit 45). */
    data class Success(val deals: List<GoodDealRow>, val canPublish: Boolean) : GoodDealsUiState
}

/** The « Bons plans » of Entraide (PRD, récit 45). An ended bon plan leaves the list. */
@OptIn(ExperimentalCoroutinesApi::class)
class GoodDealsViewModel(
    private val deals: GoodDealRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val retries = MutableStateFlow(0)

    val uiState: StateFlow<GoodDealsUiState> = retries
        .flatMapLatest { load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoodDealsUiState.Loading)

    private fun load(): Flow<GoodDealsUiState> {
        val dealRepo = deals ?: return flowOf(GoodDealsUiState.Error)
        val memberRepo = members ?: return flowOf(GoodDealsUiState.Error)
        return combine(dealRepo.observeAll(), memberRepo.observeFamily()) { list, family -> build(list, family) }
            .catch { emit(GoodDealsUiState.Error) }
    }

    fun retry() {
        retries.value++
    }

    private fun build(list: List<GoodDeal>, family: Family): GoodDealsUiState {
        val me = family.members.firstOrNull { it.id == currentMemberId } ?: return GoodDealsUiState.Error
        val at = now()
        val rows = list
            .filter { it.kind == GoodDealKind.Deal && it.isActive(at) }
            .sortedWith(compareBy<GoodDeal>({ it.endsAt }, { it.title.lowercase() }))
            .map { GoodDealRow(it, isMine = it.authorId == me.id, iAmInterested = me.id in it.interestedIds) }
        return GoodDealsUiState.Success(rows, canPublish = canPublishGoodDeal(me))
    }
}
