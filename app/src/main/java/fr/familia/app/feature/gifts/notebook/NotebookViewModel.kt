package fr.familia.app.feature.gifts.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.NotebookRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.NotebookBudget
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.notebookBudgetId
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
import java.time.LocalDate

/** The gifts for one person, at one occasion. */
data class NotebookPerson(
    val key: String,
    val name: String,
    val entries: List<NotebookEntry>,
    val personId: String? = null,
    /** What the member means to spend for them at this occasion, if set. */
    val budget: Double? = null,
) {
    val bought: Int get() = entries.count { it.status.isBought }

    /** What was bought, wrapped or given, at its price. */
    val spent: Double get() = entries.filter { it.status.isBought }.sumOf { it.price ?: 0.0 }
}

sealed interface NotebookUiState {
    data object Loading : NotebookUiState

    data object Error : NotebookUiState

    data class Success(
        /** The occasions shown, the next ones first, « no occasion yet », then the archive when open. */
        val occasions: List<NotebookOccasion>,
        val selected: NotebookOccasion?,
        val people: List<NotebookPerson>,
        val actionFailed: Boolean = false,
        /** How many occasions are past: they wait in the archive, out of the way. */
        val archivedCount: Int = 0,
        val showArchive: Boolean = false,
    ) : NotebookUiState
}

/**
 * « Mes cadeaux à offrir » (PRD, récit 9 bis): the member's private notebook, one card per person
 * for the chosen occasion. Reservations and parts arrive here from the server by themselves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotebookViewModel(
    private val notebook: NotebookRepository?,
    private val currentMemberId: String,
    today: Flow<LocalDate> = currentDayFlow(),
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    /** The occasion the member chose, and whether the archive is open. */
    private data class Pick(val occasion: NotebookOccasion? = null, val archive: Boolean = false)

    private val chosen = MutableStateFlow(Pick())
    private val failed = MutableStateFlow(false)
    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<NotebookUiState> = reload
        .flatMapLatest {
            val repo = notebook ?: return@flatMapLatest flowOf(NotebookUiState.Error)
            combine(repo.observeEntries(currentMemberId), repo.observeBudgets(currentMemberId), chosen, today, failed) { entries, budgets, pick, day, didFail ->
                state(entries, budgets, pick, day, didFail) as NotebookUiState
            }.catch { emit(NotebookUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookUiState.Loading)

    fun select(occasion: NotebookOccasion) {
        chosen.value = chosen.value.copy(occasion = occasion)
    }

    /** Opens or closes the archive: the occasions of the past years. */
    fun toggleArchive() {
        chosen.value = chosen.value.copy(archive = !chosen.value.archive)
    }

    fun retry() {
        reload.value++
    }

    fun setStatus(entry: NotebookEntry, status: GiftStatus) {
        if (entry.status != status) follow(entry, status, entry.occasion)
    }

    /** For a gift of « Mes envies », which has no occasion of its own. */
    fun setOccasion(entry: NotebookEntry, occasion: NotebookOccasion) {
        if (entry.occasion != occasion) follow(entry, entry.status, occasion)
    }

    private fun follow(entry: NotebookEntry, status: GiftStatus, occasion: NotebookOccasion) {
        val repo = notebook ?: return
        failed.value = false
        viewModelScope.launch {
            val ok = try {
                withTimeoutOrNull(writeTimeoutMillis) { repo.follow(currentMemberId, entry.id, status, occasion, entry.note) }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            failed.value = !ok
        }
    }

    /** Sets or removes ([amount] null) the budget of [person] at the occasion shown. */
    fun setBudget(person: NotebookPerson, occasion: NotebookOccasion, amount: Double?) {
        val repo = notebook ?: return
        failed.value = false
        viewModelScope.launch {
            val ok = try {
                withTimeoutOrNull(writeTimeoutMillis) {
                    repo.setBudget(currentMemberId, NotebookBudget(person.personId, person.name, occasion, amount ?: 0.0), amount)
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            failed.value = !ok
        }
    }

    private fun state(
        entries: List<NotebookEntry>,
        budgets: List<NotebookBudget>,
        pick: Pick,
        today: LocalDate,
        didFail: Boolean,
    ): NotebookUiState.Success {
        val all = sortedOccasions(entries.map { it.occasion }.toSet(), today)
        val selected = pick.occasion?.takeIf { it in all } ?: defaultOccasion(all, today)
        val past = all.filter { isPast(it, today) }
        // The occasion shown stays in the row, even from the archive closed again.
        val occasions = if (pick.archive) all else all.filter { it !in past || it == selected }
        val people = entries
            .filter { it.occasion == selected }
            .groupBy { personKey(it) }
            .map { (key, gifts) ->
                val first = gifts.first()
                val budgetId = notebookBudgetId(first.personId, first.personName, first.occasion)
                NotebookPerson(
                    key = key,
                    name = first.personName,
                    entries = gifts.sortedWith(compareBy({ it.status.ordinal }, { it.title.lowercase() })),
                    personId = first.personId,
                    budget = budgets.firstOrNull { notebookBudgetId(it.personId, it.personName, it.occasion) == budgetId }?.amount,
                )
            }
            .sortedBy { it.name.lowercase() }
        return NotebookUiState.Success(occasions, selected, people, didFail, archivedCount = past.size, showArchive = pick.archive)
    }
}

/** A member by id; someone outside the family by their first name, whatever its case. */
internal fun personKey(entry: NotebookEntry): String = entry.personId ?: "name:${entry.personName.trim().lowercase()}"

/** This year's and the next occasions first, in the order of the year, then the past ones, newest first. */
internal fun sortedOccasions(occasions: Set<NotebookOccasion>, today: LocalDate): List<NotebookOccasion> {
    val (dated, undated) = occasions.partition { it.year != null }
    val (coming, past) = dated.partition { it.year!! >= today.year }
    val order = compareBy<NotebookOccasion> { it.year }.thenBy { it.occasion?.ordinal ?: GiftOccasion.entries.size }
    return coming.sortedWith(order) + past.sortedWith(order.reversed()) + undated
}

/** An occasion of a past year goes to the archive; one with no year never does. */
internal fun isPast(occasion: NotebookOccasion, today: LocalDate): Boolean = (occasion.year ?: Int.MAX_VALUE) < today.year

/** Christmas of this year when there is one, else the first occasion still to come. */
internal fun defaultOccasion(occasions: List<NotebookOccasion>, today: LocalDate): NotebookOccasion? =
    occasions.firstOrNull { it == NotebookOccasion(GiftOccasion.Christmas, today.year) }
        ?: occasions.firstOrNull { (it.year ?: Int.MAX_VALUE) >= today.year }
        ?: occasions.firstOrNull()
