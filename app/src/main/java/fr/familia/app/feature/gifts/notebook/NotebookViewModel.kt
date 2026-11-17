package fr.familia.app.feature.gifts.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.NotebookRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.NotebookBudget
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.NotebookSource
import fr.familia.app.core.model.committed
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

    /** « Mon budget » (récit 57): my reservations with a price, my parts, and what I bought elsewhere. */
    val committed: Double get() = entries.sumOf { it.committed() }

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
        /** « Mon budget » (récit 57), for an occasion with its year, when the switch is on. */
        val budget: OccasionBudget? = null,
    ) : NotebookUiState
}

/** The budget of the occasion shown: [amount] if set, and what is already [committed]. */
data class OccasionBudget(val amount: Double?, val committed: Double) {
    /** What is left, negative beyond: the gauge turns orange, with no alert. */
    val left: Double? get() = amount?.let { it - committed }
    val isOver: Boolean get() = (left ?: 0.0) < 0
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
    /** Writes the occasion of a reservation of « Mes envies », which decides when it is revealed. */
    private val gifts: GiftRepository? = null,
    /** Switch « Liste unique » (récit 9 ter): which of an entry and its copy shows, see [shownEntries]. */
    private val singleList: Boolean = false,
    /** Switch « Mon budget » (récit 57), for an adult: the budget of a whole occasion. */
    private val occasionBudget: Boolean = false,
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
                state(shownEntries(entries, singleList), budgets, pick, day, didFail) as NotebookUiState
            }.catch { emit(NotebookUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookUiState.Loading)

    fun select(occasion: NotebookOccasion) {
        chosen.value = chosen.value.copy(occasion = occasion)
    }

    /** Opens or closes the archive: the occasions already past. */
    fun toggleArchive() {
        chosen.value = chosen.value.copy(archive = !chosen.value.archive)
    }

    fun retry() {
        reload.value++
    }

    fun setStatus(entry: NotebookEntry, status: GiftStatus) {
        if (entry.status != status) follow(entry, status, entry.occasion)
    }

    /**
     * For a gift of « Mes envies », which has no occasion of its own. On one's own reservation, the
     * reservation follows (PRD rév. 186): the gift is revealed the day after that occasion. The
     * part of a joint gift follows the responsible, and is not chosen here.
     */
    fun setOccasion(entry: NotebookEntry, occasion: NotebookOccasion) {
        if (entry.source == NotebookSource.Share) return
        if (entry.occasion != occasion) follow(entry, entry.status, occasion)
    }

    private fun follow(entry: NotebookEntry, status: GiftStatus, occasion: NotebookOccasion) {
        val repo = notebook ?: return
        val claimOccasion = occasion.occasion
            ?.takeIf { entry.source == NotebookSource.Claim && entry.occasion.occasion != it && it in WISH_OCCASIONS }
        val listId = entry.listId?.takeIf { isPermanentListId(it) }
        val itemId = entry.itemId
        failed.value = false
        viewModelScope.launch {
            val ok = try {
                withTimeoutOrNull(writeTimeoutMillis) {
                    // The reservation first: it decides the reveal, and the notebook follows it.
                    // A refused choice then leaves both as they were.
                    if (claimOccasion != null && listId != null && itemId != null) gifts?.setClaimOccasion(listId, itemId, claimOccasion)
                    repo.follow(currentMemberId, entry.id, status, occasion, entry.note)
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

    /** Sets or removes ([amount] null) the budget of the whole [occasion] (récit 57). */
    fun setOccasionBudget(occasion: NotebookOccasion, amount: Double?) {
        if (!occasionBudget || occasion.occasion == null || occasion.year == null) return
        val repo = notebook ?: return
        failed.value = false
        viewModelScope.launch {
            val ok = try {
                withTimeoutOrNull(writeTimeoutMillis) {
                    repo.setBudget(currentMemberId, NotebookBudget(null, "", occasion, amount ?: 0.0, total = true), amount)
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
                    budget = budgets.firstOrNull { !it.total && notebookBudgetId(it.personId, it.personName, it.occasion) == budgetId }?.amount,
                )
            }
            .sortedBy { it.name.lowercase() }
        val budget = selected?.takeIf { occasionBudget && it.occasion != null && it.year != null }?.let { occasion ->
            OccasionBudget(
                amount = budgets.firstOrNull { it.total && it.occasion == occasion }?.amount,
                committed = people.sumOf { it.committed },
            )
        }
        return NotebookUiState.Success(
            occasions, selected, people, didFail, archivedCount = past.size, showArchive = pick.archive, budget = budget,
        )
    }
}

/** A member by id; someone outside the family by their first name, whatever its case. */
internal fun personKey(entry: NotebookEntry): String = entry.personId ?: "name:${entry.personName.trim().lowercase()}"

/** This year's and the next occasions first, in the order of the year, then the past ones, newest first. */
internal fun sortedOccasions(occasions: Set<NotebookOccasion>, today: LocalDate): List<NotebookOccasion> {
    val (dated, undated) = occasions.partition { it.year != null }
    val (past, coming) = dated.partition { isPast(it, today) }
    val order = compareBy<NotebookOccasion> { it.year }.thenBy { it.occasion?.ordinal ?: GiftOccasion.entries.size }
    return coming.sortedWith(order) + past.sortedWith(order.reversed()) + undated
}

/**
 * An occasion goes to the archive the day after it (PRD rév. 168): Noël from 26 December. Every
 * other occasion of a year, the birthdays first, gathers several people, each on their own day,
 * so it waits for the end of that year. One with no year never goes.
 */
internal fun isPast(occasion: NotebookOccasion, today: LocalDate): Boolean {
    val year = occasion.year ?: return false
    val last = if (occasion.occasion == GiftOccasion.Christmas) LocalDate.of(year, 12, 25) else LocalDate.of(year, 12, 31)
    return last < today
}

/** Christmas of this year while it is to come, else the first occasion still to come. */
internal fun defaultOccasion(occasions: List<NotebookOccasion>, today: LocalDate): NotebookOccasion? =
    occasions.firstOrNull { it == NotebookOccasion(GiftOccasion.Christmas, today.year) && !isPast(it, today) }
        ?: occasions.firstOrNull { it.year != null && !isPast(it, today) }
        ?: occasions.firstOrNull { it.year == null }
        ?: occasions.firstOrNull()

/** The occasions a reservation of « Mes envies » may be meant for: the server reveals it the day after. */
internal val WISH_OCCASIONS = setOf(GiftOccasion.Birthday, GiftOccasion.Christmas)

/**
 * The copy of « Liste unique » (récit 9 ter) keeps both entries of a moved gift: the old one, which
 * says where it went ([NotebookEntry.movedTo]), and the copy. Only one shows: the copy while the
 * switch is on, the old one once it is off again.
 */
internal fun shownEntries(entries: List<NotebookEntry>, singleList: Boolean): List<NotebookEntry> =
    if (singleList) {
        entries.filter { it.movedTo == null }
    } else {
        entries.filter { it.itemId?.startsWith(COPIED_ITEM_PREFIX) != true }
    }

/**
 * « Ce que je lui offre » on the page of [personId] (retour d'essai, 17/11): every gift of the
 * notebook for them, whatever the occasion, the ones still to give first.
 */
internal fun giftsFor(entries: List<NotebookEntry>, personId: String, singleList: Boolean): List<NotebookEntry> =
    shownEntries(entries, singleList)
        .filter { it.personId == personId }
        .sortedWith(compareBy<NotebookEntry> { it.status == GiftStatus.Given }.thenBy { it.title.lowercase() })

/** The items copied by « Liste unique » (firebase/functions/src/singleList.ts). */
private const val COPIED_ITEM_PREFIX = "from-"
