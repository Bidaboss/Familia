package fr.familia.app.feature.gifts.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.NotebookRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
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
) {
    val bought: Int get() = entries.count { it.status.isBought }

    /** What was bought, wrapped or given, at its price. */
    val spent: Double get() = entries.filter { it.status.isBought }.sumOf { it.price ?: 0.0 }
}

sealed interface NotebookUiState {
    data object Loading : NotebookUiState

    data object Error : NotebookUiState

    data class Success(
        /** Every occasion of the notebook, the next ones first; « no occasion yet » last. */
        val occasions: List<NotebookOccasion>,
        val selected: NotebookOccasion?,
        val people: List<NotebookPerson>,
        val actionFailed: Boolean = false,
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

    private val chosen = MutableStateFlow<NotebookOccasion?>(null)
    private val failed = MutableStateFlow(false)
    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<NotebookUiState> = reload
        .flatMapLatest {
            val repo = notebook ?: return@flatMapLatest flowOf(NotebookUiState.Error)
            combine(repo.observeEntries(currentMemberId), chosen, today, failed) { entries, pick, day, didFail ->
                state(entries, pick, day, didFail) as NotebookUiState
            }.catch { emit(NotebookUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookUiState.Loading)

    fun select(occasion: NotebookOccasion) {
        chosen.value = occasion
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

    private fun state(entries: List<NotebookEntry>, pick: NotebookOccasion?, today: LocalDate, didFail: Boolean): NotebookUiState.Success {
        val occasions = sortedOccasions(entries.map { it.occasion }.toSet(), today)
        val selected = pick?.takeIf { it in occasions } ?: defaultOccasion(occasions, today)
        val people = entries
            .filter { it.occasion == selected }
            .groupBy { personKey(it) }
            .map { (key, gifts) ->
                NotebookPerson(key, gifts.first().personName, gifts.sortedWith(compareBy({ it.status.ordinal }, { it.title.lowercase() })))
            }
            .sortedBy { it.name.lowercase() }
        return NotebookUiState.Success(occasions, selected, people, didFail)
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

/** Christmas of this year when there is one, else the first occasion still to come. */
internal fun defaultOccasion(occasions: List<NotebookOccasion>, today: LocalDate): NotebookOccasion? =
    occasions.firstOrNull { it == NotebookOccasion(GiftOccasion.Christmas, today.year) }
        ?: occasions.firstOrNull { (it.year ?: Int.MAX_VALUE) >= today.year }
        ?: occasions.firstOrNull()
