package fr.familia.app.feature.gooddeals.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GoodDealRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GoodDeal
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.feature.gooddeals.canManageGoodDeal
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
import kotlinx.coroutines.withTimeoutOrNull

sealed interface GoodDealDetailUiState {
    data object Loading : GoodDealDetailUiState

    /** Removed, or never readable: the screen goes back once it was shown. */
    data object Missing : GoodDealDetailUiState

    data class Ready(
        val deal: GoodDeal,
        val isMine: Boolean,
        val canManage: Boolean,
        val iAmInterested: Boolean,
        /** The first names of those interested, me first (« Moi » on screen), then by name. */
        val interestedNames: List<String>,
        val actionFailed: Boolean = false,
        /** A bonne adresse: its author first, then those who recommend it too, me as « Moi » on screen. */
        val recommendedNames: List<String> = emptyList(),
        val iRecommend: Boolean = false,
        /** An adult but the author may add their name (récit 45). */
        val canRecommend: Boolean = false,
    ) : GoodDealDetailUiState
}

/** One bon plan (PRD, récit 45): « Ça m'intéresse », and for its author, change or remove. */
@OptIn(ExperimentalCoroutinesApi::class)
class GoodDealDetailViewModel(
    private val deals: GoodDealRepository?,
    private val members: MemberRepository?,
    /** The member reading, the admin principal in « Voir comme »: rights are theirs. */
    private val currentMemberId: String,
    private val dealId: String,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val failed = MutableStateFlow(false)

    val uiState: StateFlow<GoodDealDetailUiState> = failed
        .flatMapLatest { didFail -> load(didFail) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoodDealDetailUiState.Loading)

    private fun load(didFail: Boolean): Flow<GoodDealDetailUiState> {
        val dealRepo = deals ?: return flowOf(GoodDealDetailUiState.Missing)
        val memberRepo = members ?: return flowOf(GoodDealDetailUiState.Missing)
        return combine(dealRepo.observe(dealId), memberRepo.observeFamily()) { deal, family -> build(deal, family, didFail) }
            .catch { emit(GoodDealDetailUiState.Missing) }
    }

    private fun build(deal: GoodDeal?, family: Family, didFail: Boolean): GoodDealDetailUiState {
        val me = family.members.firstOrNull { it.id == currentMemberId }
        if (deal == null || me == null) return GoodDealDetailUiState.Missing
        val others = deal.interestedIds.filter { it != me.id }
            .mapNotNull { id -> family.members.firstOrNull { it.id == id }?.firstName }
            .sortedBy { it.lowercase() }
        val recommenders = deal.recommendedIds.filter { it != me.id && it != deal.authorId }
            .mapNotNull { id -> family.members.firstOrNull { it.id == id }?.firstName }
            .sortedBy { it.lowercase() }
        return GoodDealDetailUiState.Ready(
            deal = deal,
            isMine = deal.authorId == me.id,
            canManage = canManageGoodDeal(deal, me, family),
            iAmInterested = me.id in deal.interestedIds,
            interestedNames = others,
            actionFailed = didFail,
            recommendedNames = listOfNotNull(deal.authorName.takeIf { deal.authorId != me.id }) + recommenders,
            iRecommend = me.id in deal.recommendedIds,
            canRecommend = me.profile == MemberProfile.Adult && deal.authorId != me.id,
        )
    }

    /** « Ça m'intéresse », or taken back. */
    fun toggleInterest() {
        val state = uiState.value as? GoodDealDetailUiState.Ready ?: return
        val repo = deals ?: return
        perform { repo.setInterested(dealId, currentMemberId, !state.iAmInterested) }
    }

    /** « Je recommande aussi », or taken back. */
    fun toggleRecommend() {
        val state = uiState.value as? GoodDealDetailUiState.Ready ?: return
        if (!state.canRecommend) return
        val repo = deals ?: return
        perform { repo.setRecommended(dealId, currentMemberId, !state.iRecommend) }
    }

    fun remove() {
        val state = uiState.value as? GoodDealDetailUiState.Ready ?: return
        if (!state.canManage) return
        val repo = deals ?: return
        perform { repo.delete(dealId) }
    }

    fun dismissError() {
        failed.value = false
    }

    // Offline, the write lands in the cache at once: only a refusal or an error counts as failed.
    private fun perform(write: suspend () -> Unit) {
        failed.value = false
        viewModelScope.launch {
            val ok = try {
                withTimeoutOrNull(writeTimeoutMillis) { write() }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (!ok) failed.value = true
        }
    }
}
