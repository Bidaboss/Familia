package fr.familia.app.feature.expenses

import fr.familia.app.core.data.ExpenseRepository
import fr.familia.app.core.model.Expense
import fr.familia.app.core.model.ExpenseBook
import fr.familia.app.core.model.ExpenseCategory
import fr.familia.app.core.model.ExpenseContainer
import fr.familia.app.core.model.ExpenseHistoryEntry
import fr.familia.app.core.model.ExpenseSettlement
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.SplitMode
import fr.familia.app.core.model.Transfer
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.trips.FakeTrips
import fr.familia.app.feature.trips.TestTrips
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Fictional trip t1 of the test family: adult1 (h1), kid1 (h1) and adult2 (h2) go, admin1 only follows. */
class ExpensesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val trips = FakeTrips(listOf(TestTrips.trip(participants = listOf("adult1", "kid1", "adult2"))))

    private fun expense(id: String, cents: Long, payer: String, household: String, author: String = payer) = Expense(
        id, "Dépense $id", cents, LocalDate.of(2027, 7, 12), payer, household, ExpenseCategory.Groceries,
        SplitMode.Shares, listOf("h1", "h2"), authorId = author,
    )

    @Test
    fun `the first adult participant opens the book with one share per person, children too`() = runTest {
        val repo = FakeExpenses()
        val vm = ExpensesViewModel(repo, TripExpenseContext(trips, "t1"), FakeMembers(), "adult2")
        val state = vm.uiState.first { it is ExpensesUiState.Success } as ExpensesUiState.Success
        assertEquals(mapOf("h1" to 2.0, "h2" to 1.0), repo.opened.single().second)
        assertEquals(mapOf("adult1" to 1.0, "kid1" to 1.0, "adult2" to 1.0), repo.bookValue!!.memberShares)
        assertEquals("adult1", repo.opened.single().first)
        assertEquals(listOf("Foyer Deux", "Foyer Un"), state.shareGroups.map { it.name })
        assertEquals(listOf("Foyer Deux", "Foyer Un"), state.households.map { it.name })
        assertFalse(state.isCreator)
        assertTrue(state.transfers.isEmpty())
    }

    @Test
    fun `the balance, who pays back whom, and who may change an expense`() = runTest {
        val repo = FakeExpenses(
            book = ExpenseBook("trip_t1", ExpenseContainer(fr.familia.app.core.model.ExpenseContainerType.Trip, "t1"), "adult1", mapOf("h1" to 1.5, "h2" to 1.0)),
            expenses = listOf(expense("a", 10_000, "adult1", "h1"), expense("b", 2_500, "adult2", "h2")),
        )
        val vm = ExpensesViewModel(repo, TripExpenseContext(trips, "t1"), FakeMembers(), "adult2")
        val state = vm.uiState.first { it is ExpensesUiState.Success } as ExpensesUiState.Success
        assertEquals(12_500L, state.totalCents)
        // 125 € for 2,5 shares: h1 owes 75 €, h2 owes 50 €. h1 paid 100 €, h2 paid 25 €.
        val h2 = state.households.first { it.id == "h2" }
        assertEquals(-2_500L, h2.netCents)
        assertEquals(listOf(TransferRow("h2", "h1", "Foyer Deux", "Foyer Un", 2_500, canConfirm = false)), state.transfers)
        assertEquals(listOf(false, true), state.expenses.sortedBy { it.expense.id }.map { it.canEdit })
        assertTrue(repo.opened.isEmpty())
    }

    private fun book(closed: Boolean = false) = ExpenseBook(
        "trip_t1", ExpenseContainer(fr.familia.app.core.model.ExpenseContainerType.Trip, "t1"), "adult1",
        mapOf("h1" to 1.5, "h2" to 1.0), closed,
    )

    @Test
    fun `the household that receives ticks « C'est remboursé », and only who ticked it takes it back`() = runTest {
        val repo = FakeExpenses(book(), listOf(expense("a", 10_000, "adult1", "h1"), expense("b", 2_500, "adult2", "h2")))
        val payer = ExpensesViewModel(repo, TripExpenseContext(trips, "t1"), FakeMembers(), "adult2")
        backgroundScope.launch { payer.uiState.collect {} }
        val owed = payer.uiState.first { it is ExpensesUiState.Success } as ExpensesUiState.Success
        // adult2 pays back: only the household that receives the money may tick it.
        payer.confirm(owed.transfers.single())
        assertTrue(repo.settlementList.isEmpty())

        val receiver = ExpensesViewModel(repo, TripExpenseContext(trips, "t1"), FakeMembers(), "adult1")
        backgroundScope.launch { receiver.uiState.collect {} }
        val toTick = receiver.uiState.first { it is ExpensesUiState.Success } as ExpensesUiState.Success
        assertTrue(toTick.transfers.single().canConfirm)
        receiver.confirm(toTick.transfers.single())
        receiver.confirm(toTick.transfers.single())
        assertEquals(1, repo.settlementList.size)
        val settled = receiver.uiState.first { it is ExpensesUiState.Success && it.settlements.isNotEmpty() } as ExpensesUiState.Success
        assertTrue(settled.transfers.isEmpty())
        assertEquals(0L, settled.households.first { it.id == "h2" }.netCents)
        assertTrue(settled.settlements.single().canUndo)

        val other = payer.uiState.first { it is ExpensesUiState.Success && it.settlements.isNotEmpty() } as ExpensesUiState.Success
        assertFalse(other.settlements.single().canUndo)
        receiver.undo(settled.settlements.single())
        val back = receiver.uiState.first { it is ExpensesUiState.Success && it.settlements.isEmpty() } as ExpensesUiState.Success
        assertEquals(listOf(2_500L), back.transfers.map { it.amountCents })
    }

    @Test
    fun `only the creator changes the shares of each person, by halves from 0 to 10, and closes the accounts`() = runTest {
        val repo = FakeExpenses(book(), listOf(expense("a", 10_000, "adult1", "h1", author = "adult2")))
        val other = ExpensesViewModel(repo, TripExpenseContext(trips, "t1"), FakeMembers(), "adult2")
        backgroundScope.launch { other.uiState.collect {} }
        other.uiState.first { it is ExpensesUiState.Success }
        assertFalse(other.saveShares(mapOf("adult1" to "1", "kid1" to "1", "adult2" to "1")))
        other.setClosed(true)
        assertFalse(repo.bookValue!!.closed)

        val creator = ExpensesViewModel(repo, TripExpenseContext(trips, "t1"), FakeMembers(), "adult1")
        backgroundScope.launch { creator.uiState.collect {} }
        creator.uiState.first { it is ExpensesUiState.Success }
        assertFalse(creator.saveShares(mapOf("adult1" to "1,3", "kid1" to "1", "adult2" to "1")))
        assertFalse(creator.saveShares(mapOf("adult1" to "1", "kid1" to "1")))
        // A household that would weigh nothing is refused.
        assertFalse(creator.saveShares(mapOf("adult1" to "1", "kid1" to "1", "adult2" to "0")))
        assertTrue(creator.saveShares(mapOf("adult1" to "1", "kid1" to "0,5", "adult2" to "2")))
        assertEquals(mapOf("h1" to 1.5, "h2" to 2.0), repo.bookValue!!.shares)
        assertEquals(mapOf("adult1" to 1.0, "kid1" to 0.5, "adult2" to 2.0), repo.bookValue!!.memberShares)
        val saved = creator.uiState.first { it is ExpensesUiState.Success && it.households.any { h -> h.people.isNotEmpty() } } as ExpensesUiState.Success
        assertEquals(listOf("adult1" to 1.0, "kid1" to 0.5), saved.households.first { it.id == "h1" }.people.map { it.id to it.shares })

        creator.setClosed(true)
        val closed = creator.uiState.first { it is ExpensesUiState.Success && it.closed } as ExpensesUiState.Success
        assertTrue(closed.transfers.none { it.canConfirm })
        assertTrue(closed.expenses.none { it.canEdit })
        assertFalse(creator.saveShares(mapOf("adult1" to "1", "kid1" to "1", "adult2" to "1")))
        creator.setClosed(false)
        assertFalse(repo.bookValue!!.closed)
    }

    @Test
    fun `a refused change says so once`() = runTest {
        val repo = FakeExpenses(book(), listOf(expense("a", 10_000, "adult1", "h1"))).apply { refuse = true }
        val creator = ExpensesViewModel(repo, TripExpenseContext(trips, "t1"), FakeMembers(), "adult1")
        backgroundScope.launch { creator.uiState.collect {} }
        creator.uiState.first { it is ExpensesUiState.Success }
        creator.setClosed(true)
        assertTrue((creator.uiState.first { it is ExpensesUiState.Success && it.writeFailed } as ExpensesUiState.Success).writeFailed)
        creator.dismissError()
        assertFalse((creator.uiState.value as ExpensesUiState.Success).writeFailed)
    }

    @Test
    fun `a follower who does not go, or a child, never sees the accounts`() = runTest {
        for (who in listOf("admin1", "kid1")) {
            val vm = ExpensesViewModel(FakeExpenses(), TripExpenseContext(trips, "t1"), FakeMembers(), who)
            assertEquals(ExpensesUiState.NotAllowed, vm.uiState.first { it !is ExpensesUiState.Loading })
        }
    }
}

class FakeExpenses(
    book: ExpenseBook? = null,
    expenses: List<Expense> = emptyList(),
    settlements: List<ExpenseSettlement> = emptyList(),
) : ExpenseRepository {
    private val book = MutableStateFlow(book)
    val expenses = MutableStateFlow(expenses)
    private val settlements = MutableStateFlow(settlements)
    val settlementList: List<ExpenseSettlement> get() = settlements.value
    val bookValue: ExpenseBook? get() = book.value
    var refuse = false
    val opened = mutableListOf<Pair<String, Map<String, Double>>>()
    val saved = mutableListOf<Pair<Expense, Boolean>>()

    override fun observeBook(container: ExpenseContainer): Flow<ExpenseBook?> = book
    override suspend fun openBook(container: ExpenseContainer, creatorId: String, shares: Map<String, Double>, memberShares: Map<String, Double>) {
        opened += creatorId to shares
        book.value = ExpenseBook(container.bookId, container, creatorId, shares, memberShares = memberShares)
    }
    override suspend fun setShares(bookId: String, shares: Map<String, Double>, memberShares: Map<String, Double>) {
        book.value = book.value?.copy(shares = shares, memberShares = memberShares)
    }
    override fun observeExpenses(bookId: String): Flow<List<Expense>> = expenses
    override fun observeSettlements(bookId: String): Flow<List<ExpenseSettlement>> = settlements.map { it }
    override fun newExpenseId(bookId: String): String = "new-${expenses.value.size + 1}"
    val history = MutableStateFlow<Map<String, List<ExpenseHistoryEntry>>>(emptyMap())
    override fun observeHistory(bookId: String, expenseId: String): Flow<List<ExpenseHistoryEntry>> = history.map { it[expenseId].orEmpty() }
    override suspend fun saveExpense(bookId: String, expense: Expense, me: Member, isNew: Boolean) {
        saved += expense to isNew
        expenses.value = expenses.value.filterNot { it.id == expense.id } + expense
    }
    override suspend fun deleteExpense(bookId: String, expenseId: String) {
        expenses.value = expenses.value.filterNot { it.id == expenseId }
    }
    override suspend fun setClosed(bookId: String, closed: Boolean) {
        if (refuse) error("refused")
        book.value = book.value?.copy(closed = closed)
    }
    override suspend fun settle(bookId: String, transfer: Transfer, me: Member) {
        settlements.value = settlements.value + ExpenseSettlement(
            "s${settlements.value.size + 1}", transfer.fromHouseholdId, transfer.toHouseholdId, transfer.amountCents, me.id, me.firstName,
        )
    }
    override suspend fun unsettle(bookId: String, settlementId: String) {
        settlements.value = settlements.value.filterNot { it.id == settlementId }
    }
}
