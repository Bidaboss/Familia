package fr.familia.app.feature.gooddeals.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GoodDealRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GoodDeal
import fr.familia.app.core.model.GoodDealCategory
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
import java.text.Normalizer
import java.time.Instant

/** One post in the list, with what the reader needs to know at a glance. */
data class GoodDealRow(val deal: GoodDeal, val isMine: Boolean, val iAmInterested: Boolean, val iRecommend: Boolean = false)

/** What the list of the bonnes adresses is narrowed to: a category, and words of a name or town. */
data class AddressFilter(val category: GoodDealCategory? = null, val query: String = "")

sealed interface GoodDealsUiState {
    data object Loading : GoodDealsUiState
    data object Error : GoodDealsUiState

    data class Success(
        /** The bons plans still running, the one ending first on top (récit 45). */
        val deals: List<GoodDealRow>,
        val canPublish: Boolean,
        /** The bonnes adresses matching [filter], by name. */
        val addresses: List<GoodDealRow> = emptyList(),
        val filter: AddressFilter = AddressFilter(),
        /** Whether the family has any address at all, whatever the filter. */
        val hasAddresses: Boolean = addresses.isNotEmpty(),
    ) : GoodDealsUiState
}

/** "Médecin" and "medecin" match: no case, no accents. */
private fun String.folded(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** True when every word of [query] is in the name or the town of [deal]. */
fun matchesAddress(deal: GoodDeal, query: String): Boolean {
    val words = query.folded().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val haystack = "${deal.title} ${deal.place.orEmpty()}".folded()
    return words.all { it in haystack }
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
    private val filter = MutableStateFlow(AddressFilter())

    val uiState: StateFlow<GoodDealsUiState> = retries
        .flatMapLatest { load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoodDealsUiState.Loading)

    private fun load(): Flow<GoodDealsUiState> {
        val dealRepo = deals ?: return flowOf(GoodDealsUiState.Error)
        val memberRepo = members ?: return flowOf(GoodDealsUiState.Error)
        return combine(dealRepo.observeAll(), memberRepo.observeFamily(), filter) { list, family, f -> build(list, family, f) }
            .catch { emit(GoodDealsUiState.Error) }
    }

    fun retry() {
        retries.value++
    }

    /** A category chip: picked, or picked again to show them all. */
    fun pickCategory(category: GoodDealCategory?) {
        filter.value = filter.value.copy(category = category.takeIf { it != filter.value.category })
    }

    fun search(query: String) {
        filter.value = filter.value.copy(query = query.take(60))
    }

    private fun build(list: List<GoodDeal>, family: Family, f: AddressFilter): GoodDealsUiState {
        val me = family.members.firstOrNull { it.id == currentMemberId } ?: return GoodDealsUiState.Error
        val at = now()
        fun row(d: GoodDeal) = GoodDealRow(d, isMine = d.authorId == me.id, iAmInterested = me.id in d.interestedIds, iRecommend = me.id in d.recommendedIds)
        val running = list
            .filter { it.kind == GoodDealKind.Deal && it.isActive(at) }
            .sortedWith(compareBy<GoodDeal>({ it.endsAt }, { it.title.lowercase() }))
            .map(::row)
        val allAddresses = list.filter { it.kind == GoodDealKind.Address }
        val addresses = allAddresses
            .filter { (f.category == null || it.category == f.category) && matchesAddress(it, f.query) }
            .sortedBy { it.title.folded() }
            .map(::row)
        return GoodDealsUiState.Success(running, canPublishGoodDeal(me), addresses, f, hasAddresses = allAddresses.isNotEmpty())
    }
}
