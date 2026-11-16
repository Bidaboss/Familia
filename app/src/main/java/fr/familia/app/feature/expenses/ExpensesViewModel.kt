package fr.familia.app.feature.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.ExpenseRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.Expense
import fr.familia.app.core.model.ExpenseBook
import fr.familia.app.core.model.ExpenseContainer
import fr.familia.app.core.model.ExpenseContainerType
import fr.familia.app.core.model.ExpenseSettlement
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Transfer
import fr.familia.app.core.model.Trip
import fr.familia.app.core.ui.sendWrite
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One household in the balance: what it paid, what it should have paid, and the difference. */
data class HouseholdRow(val id: String, val name: String, val shares: Double, val paidCents: Long, val owedCents: Long, val netCents: Long)

/**
 * One transfer of « Qui rembourse qui », with the names of the households. [canConfirm]: this
 * member belongs to the household that receives it, so may tick « C'est remboursé ».
 */
data class TransferRow(
    val fromId: String,
    val toId: String,
    val fromName: String,
    val toName: String,
    val amountCents: Long,
    val canConfirm: Boolean = false,
)

/** One « C'est remboursé » already ticked; [canUndo] for who ticked it, until the accounts close. */
data class SettlementRow(
    val id: String,
    val fromName: String,
    val toName: String,
    val amountCents: Long,
    val byName: String,
    val canUndo: Boolean,
)

/** One expense of the list, with who paid it and whether this member may change it. */
data class ExpenseRow(val expense: Expense, val payerName: String, val canEdit: Boolean)

sealed interface ExpensesUiState {
    data object Loading : ExpensesUiState

    /** Not an adult participant, or the trip is gone: the accounts are not for this member. */
    data object NotAllowed : ExpensesUiState
    data object Error : ExpensesUiState

    data class Success(
        val title: String,
        val bookId: String,
        val closed: Boolean,
        /** The creator of the trip: changes the shares, corrects every expense. */
        val isCreator: Boolean,
        val totalCents: Long,
        val households: List<HouseholdRow>,
        val transfers: List<TransferRow>,
        val expenses: List<ExpenseRow>,
        val settlements: List<SettlementRow> = emptyList(),
        /** The last change could not be saved: shown once, then dismissed. */
        val writeFailed: Boolean = false,
    ) : ExpensesUiState
}

/**
 * The accounts of a trip (PRD rév. 193, récit 16 bis): the balance of each household, « Qui
 * rembourse qui » and the expenses. The first adult participant to open them opens the book,
 * with one share per adult and a half per child. Who received a transfer ticks « C'est
 * remboursé »; the creator of the trip changes the shares and closes the accounts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(
    private val expenses: ExpenseRepository?,
    private val trips: TripRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    tripId: String,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val container = ExpenseContainer(ExpenseContainerType.Trip, tripId)
    private val attempt = MutableStateFlow(0)
    private var opening = false
    private val writeFailed = MutableStateFlow(false)

    /** This member, once the family is known: the author of a « C'est remboursé ». */
    private var me: Member? = null

    private val base: Flow<ExpensesUiState> = attempt
        .flatMapLatest { n ->
            val repo = expenses
            if (repo == null || trips == null || members == null) {
                flowOf(ExpensesUiState.NotAllowed)
            } else {
                combine(trips.observeTrip(container.id), members.observeFamily()) { trip, family -> trip to family }
                    .flatMapLatest { (trip, family) -> withBook(repo, trip, family) }
                    .onStart { if (n > 0) emit(ExpensesUiState.Loading) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(ExpensesUiState.Error)
                    }
            }
        }

    val uiState: StateFlow<ExpensesUiState> = combine(base, writeFailed) { state, failed ->
        if (state is ExpensesUiState.Success) state.copy(writeFailed = failed) else state
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState.Loading)

    fun retry() {
        attempt.update { it + 1 }
    }

    /** « C'est remboursé », by an adult of the household that received [row]. */
    fun confirm(row: TransferRow) {
        val who = me ?: return
        if (!row.canConfirm) return
        write { repo, s -> if (!s.closed) repo.settle(s.bookId, Transfer(row.fromId, row.toId, row.amountCents), who) }
    }

    /** Takes back a « C'est remboursé » ticked by this member. */
    fun undo(row: SettlementRow) {
        if (!row.canUndo) return
        write { repo, s -> repo.unsettle(s.bookId, row.id) }
    }

    /** Closes the accounts, which freezes the balance, or opens them again: the creator only. */
    fun setClosed(closed: Boolean) {
        write { repo, s -> if (s.isCreator) repo.setClosed(s.bookId, closed) }
    }

    /**
     * The shares typed by the creator, one per household. False when one is wrong (from 0,5 to
     * 20, by halves): nothing is saved, and the form stays open.
     */
    fun saveShares(typed: Map<String, String>): Boolean {
        val s = uiState.value as? ExpensesUiState.Success ?: return false
        if (!s.isCreator || s.closed) return false
        val shares = parseShares(typed, s.households.map { it.id }) ?: return false
        write { repo, current -> repo.setShares(current.bookId, shares) }
        return true
    }

    fun dismissError() {
        writeFailed.value = false
    }

    /** Saves with the current accounts. Offline, the change waits in the queue and shows at once. */
    private fun write(block: suspend (ExpenseRepository, ExpensesUiState.Success) -> Unit) {
        val repo = expenses ?: return
        val s = uiState.value as? ExpensesUiState.Success ?: return
        viewModelScope.launch {
            if (sendWrite(writeTimeoutMillis) { block(repo, s) } == null) writeFailed.value = true
        }
    }

    private fun withBook(repo: ExpenseRepository, trip: Trip?, family: Family): Flow<ExpensesUiState> {
        val me = family.members.firstOrNull { it.id == currentMemberId }
        if (trip == null || me == null || me.profile != MemberProfile.Adult || me.id !in trip.participantIds) {
            return flowOf(ExpensesUiState.NotAllowed)
        }
        this.me = me
        return repo.observeBook(container).flatMapLatest { book ->
            if (book == null) {
                open(repo, trip, family)
                flowOf(ExpensesUiState.Loading)
            } else {
                combine(repo.observeExpenses(book.id), repo.observeSettlements(book.id)) { list, settled ->
                    state(trip, family, me, book, list, settled)
                }
            }
        }
    }

    /** Opens the book once, with the default shares of the participants. Offline, it waits in the queue. */
    private fun open(repo: ExpenseRepository, trip: Trip, family: Family) {
        if (opening) return
        opening = true
        val participants = family.members.filter { it.id in trip.participantIds }
        viewModelScope.launch {
            try {
                withTimeoutOrNull(writeTimeoutMillis) { repo.openBook(container, trip.creatorId, defaultShares(participants)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                opening = false
                attempt.update { it + 1 }
            }
        }
    }

    private fun state(
        trip: Trip,
        family: Family,
        me: Member,
        book: ExpenseBook,
        list: List<Expense>,
        settled: List<ExpenseSettlement>,
    ): ExpensesUiState.Success {
        val names = family.households.associate { it.id to it.name }
        fun name(id: String) = names[id] ?: id
        val balances = balancesOf(book.shares.keys, list, settled, book.shares)
        val isCreator = book.creatorId == currentMemberId
        return ExpensesUiState.Success(
            title = trip.title,
            bookId = book.id,
            closed = book.closed,
            isCreator = isCreator,
            totalCents = list.sumOf { it.amountCents },
            households = balances.map {
                HouseholdRow(it.householdId, name(it.householdId), book.shares[it.householdId] ?: 0.0, it.paidCents, it.owedCents, it.netCents)
            }.sortedBy { it.name.lowercase() },
            transfers = transfersOf(balances).map {
                TransferRow(
                    it.fromHouseholdId, it.toHouseholdId, name(it.fromHouseholdId), name(it.toHouseholdId), it.amountCents,
                    canConfirm = !book.closed && it.toHouseholdId == me.householdId,
                )
            },
            expenses = list.sortedWith(compareByDescending<Expense> { it.date }.thenBy { it.title.lowercase() }).map { e ->
                val payer = family.members.firstOrNull { it.id == e.payerId }?.firstName.orEmpty()
                ExpenseRow(e, payer, !book.closed && (e.authorId == currentMemberId || isCreator))
            },
            settlements = settled.sortedBy { it.id }.map {
                SettlementRow(
                    it.id, name(it.fromHouseholdId), name(it.toHouseholdId), it.amountCents, it.confirmedByName,
                    canUndo = !book.closed && it.confirmedBy == currentMemberId,
                )
            },
        )
    }
}
