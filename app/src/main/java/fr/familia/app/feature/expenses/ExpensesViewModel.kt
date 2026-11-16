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
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Trip
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

/** One transfer of « Qui rembourse qui », with the names of the households. */
data class TransferRow(val fromName: String, val toName: String, val amountCents: Long)

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
    ) : ExpensesUiState
}

/**
 * The accounts of a trip (PRD rév. 193, récit 16 bis): the balance of each household, « Qui
 * rembourse qui » and the expenses. The first adult participant to open them opens the book,
 * with one share per adult and a half per child.
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

    val uiState: StateFlow<ExpensesUiState> = attempt
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState.Loading)

    fun retry() {
        attempt.update { it + 1 }
    }

    private fun withBook(repo: ExpenseRepository, trip: Trip?, family: Family): Flow<ExpensesUiState> {
        val me = family.members.firstOrNull { it.id == currentMemberId }
        if (trip == null || me == null || me.profile != MemberProfile.Adult || me.id !in trip.participantIds) {
            return flowOf(ExpensesUiState.NotAllowed)
        }
        return repo.observeBook(container).flatMapLatest { book ->
            if (book == null) {
                open(repo, trip, family)
                flowOf(ExpensesUiState.Loading)
            } else {
                combine(repo.observeExpenses(book.id), repo.observeSettlements(book.id)) { list, settled ->
                    state(trip, family, book, list, settled)
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
            transfers = transfersOf(balances).map { TransferRow(name(it.fromHouseholdId), name(it.toHouseholdId), it.amountCents) },
            expenses = list.sortedWith(compareByDescending<Expense> { it.date }.thenBy { it.title.lowercase() }).map { e ->
                val payer = family.members.firstOrNull { it.id == e.payerId }?.firstName.orEmpty()
                ExpenseRow(e, payer, !book.closed && (e.authorId == currentMemberId || isCreator))
            },
        )
    }
}
