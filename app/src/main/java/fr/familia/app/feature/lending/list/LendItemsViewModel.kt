package fr.familia.app.feature.lending.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.LendItemRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.LendCategory
import fr.familia.app.core.model.LendItem
import fr.familia.app.core.model.LendStatus
import fr.familia.app.feature.lending.canAddLendItem
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

/** One thing in the list: whose it is, and where it is now. */
data class LendRow(val item: LendItem, val isMine: Boolean, val iHaveIt: Boolean)

/** What the list is narrowed to: words of a name, a category, and the free things only. */
data class LendFilter(val query: String = "", val category: LendCategory? = null, val availableOnly: Boolean = false)

sealed interface LendItemsUiState {
    data object Loading : LendItemsUiState
    data object Error : LendItemsUiState

    data class Success(
        /** The things matching [filter], by name. */
        val items: List<LendRow>,
        val canAdd: Boolean,
        val filter: LendFilter = LendFilter(),
        /** Whether the family has any thing at all, whatever the filter. */
        val hasItems: Boolean = items.isNotEmpty(),
    ) : LendItemsUiState
}

/** "Raclette" and "râclette" match: no case, no accents. */
private fun String.folded(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** True when every word of [query] is in the name of [item]. */
fun matchesLendItem(item: LendItem, query: String): Boolean {
    val words = query.folded().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val name = item.name.folded()
    return words.all { it in name }
}

/** « On se prête » in Entraide (PRD, récit 51): search by name, a category, « Disponible ». */
@OptIn(ExperimentalCoroutinesApi::class)
class LendItemsViewModel(
    private val items: LendItemRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
) : ViewModel() {

    private val retries = MutableStateFlow(0)
    private val filter = MutableStateFlow(LendFilter())

    val uiState: StateFlow<LendItemsUiState> = retries
        .flatMapLatest { load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LendItemsUiState.Loading)

    private fun load(): Flow<LendItemsUiState> {
        val itemRepo = items ?: return flowOf(LendItemsUiState.Error)
        val memberRepo = members ?: return flowOf(LendItemsUiState.Error)
        return combine(itemRepo.observeAll(currentMemberId), memberRepo.observeFamily(), filter) { list, family, f -> build(list, family, f) }
            .catch { emit(LendItemsUiState.Error) }
    }

    fun retry() {
        retries.value++
    }

    fun search(query: String) {
        filter.value = filter.value.copy(query = query.take(60))
    }

    /** A category chip: picked, or picked again to show them all. */
    fun pickCategory(category: LendCategory?) {
        filter.value = filter.value.copy(category = category.takeIf { it != filter.value.category })
    }

    fun toggleAvailableOnly() {
        filter.value = filter.value.copy(availableOnly = !filter.value.availableOnly)
    }

    private fun build(list: List<LendItem>, family: Family, f: LendFilter): LendItemsUiState {
        val me = family.members.firstOrNull { it.id == currentMemberId } ?: return LendItemsUiState.Error
        val rows = list
            .filter {
                (f.category == null || it.category == f.category) &&
                    (!f.availableOnly || it.status == LendStatus.Available) &&
                    matchesLendItem(it, f.query)
            }
            .sortedBy { it.name.folded() }
            .map { LendRow(it, isMine = it.ownerId == me.id, iHaveIt = it.status == LendStatus.Lent && it.borrowerId == me.id) }
        return LendItemsUiState.Success(rows, canAddLendItem(me), f, hasItems = list.isNotEmpty())
    }
}
