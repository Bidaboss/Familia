package fr.familia.app.feature.expenses

import fr.familia.app.core.model.ExpenseBook
import fr.familia.app.core.model.ExpenseCategory
import fr.familia.app.core.model.ExpenseContainer
import fr.familia.app.core.model.ExpenseContainerType
import fr.familia.app.core.model.SplitMode
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.trips.FakeTrips
import fr.familia.app.feature.trips.TestTrips
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** The expense form (récit 16 bis), on the fictional trip t1: adult1 and kid1 (h1), adult2 (h2). */
class ExpenseFormTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val payers = mapOf("adult1" to "h1", "adult2" to "h2")
    private val households = setOf("h1", "h2")
    private val day = LocalDate.of(2027, 7, 12)
    private val valid = ExpenseForm("Courses", "60", date = day, payerId = "adult1", householdIds = households)

    @Test
    fun `a valid form gives an expense in whole euro cents`() {
        assertTrue(validate(valid, payers, households).isEmpty())
        val e = expenseOf("e1", valid.copy(amount = "12,5", category = ExpenseCategory.Groceries), payers, "adult1")
        assertEquals(1_250L, e.amountCents)
        assertEquals("h1", e.payerHouseholdId)
        assertEquals(listOf("h1", "h2"), e.householdIds)
        assertEquals(1.0, e.rate, 0.0)
    }

    @Test
    fun `each mistake is named`() {
        assertEquals(setOf(ExpenseFormError.Title), validate(valid.copy(title = " "), payers, households))
        assertEquals(setOf(ExpenseFormError.Amount), validate(valid.copy(amount = "0"), payers, households))
        assertEquals(setOf(ExpenseFormError.Amount), validate(valid.copy(amount = "deux"), payers, households))
        assertEquals(setOf(ExpenseFormError.Amount), validate(valid.copy(amount = "1e3"), payers, households))
        assertEquals(setOf(ExpenseFormError.Amount), validate(valid.copy(amount = "12,505"), payers, households))
        assertEquals(setOf(ExpenseFormError.Rate), validate(valid.copy(amount = "0,01", currency = "JPY", rate = "0,0001"), payers, households))
        assertEquals(setOf(ExpenseFormError.Currency), validate(valid.copy(currency = "E1"), payers, households))
        assertEquals(setOf(ExpenseFormError.Rate), validate(valid.copy(currency = "CHF", rate = ""), payers, households))
        assertEquals(setOf(ExpenseFormError.Payer), validate(valid.copy(payerId = "kid1"), payers, households))
        assertEquals(setOf(ExpenseFormError.Households), validate(valid.copy(householdIds = emptySet()), payers, households))
        assertEquals(setOf(ExpenseFormError.Households), validate(valid.copy(householdIds = setOf("h9")), payers, households))
    }

    @Test
    fun `another currency at its rate, and typed amounts that add up`() {
        val chf = expenseOf("e2", valid.copy(amount = "100", currency = "chf", rate = "0,92"), payers, "adult1")
        assertEquals(9_200L, chf.amountCents)
        assertEquals("CHF", chf.currency)
        assertEquals(10_000L, chf.originalCents)
        val typed = valid.copy(mode = SplitMode.Amounts, amounts = mapOf("h1" to "45", "h2" to "15"))
        assertTrue(validate(typed, payers, households).isEmpty())
        assertEquals(mapOf("h1" to 4_500L, "h2" to 1_500L), expenseOf("e3", typed, payers, "adult1").amounts)
        assertEquals(setOf(ExpenseFormError.Amounts), validate(typed.copy(amounts = mapOf("h1" to "45", "h2" to "10")), payers, households))
        assertEquals(setOf(ExpenseFormError.Amounts), validate(typed.copy(amounts = mapOf("h1" to "70", "h2" to "-10")), payers, households))
    }

    @Test
    fun `an expense opens again in the form as it was typed`() {
        val e = expenseOf("e4", valid.copy(amount = "100", currency = "CHF", rate = "0,92"), payers, "adult1")
        val form = formOf(e)
        assertEquals("100,00", form.amount)
        assertEquals("0,92", form.rate)
        assertEquals(e.copy(), expenseOf("e4", form, payers, "adult1"))
    }

    @Test
    fun `the form saves a new expense paid by the member, and a child never opens it`() = runTest {
        val book = ExpenseBook("trip_t1", ExpenseContainer(ExpenseContainerType.Trip, "t1"), "adult1", mapOf("h1" to 1.5, "h2" to 1.0))
        val repo = FakeExpenses(book = book)
        val trips = FakeTrips(listOf(TestTrips.trip(participants = listOf("adult1", "kid1", "adult2"))))
        val vm = ExpenseEditViewModel(repo, TripExpenseContext(trips, "t1"), FakeMembers(), "adult2", null, today = { day })
        val editing = vm.uiState.first { it is ExpenseEditUiState.Editing } as ExpenseEditUiState.Editing
        assertEquals("adult2", editing.form.payerId)
        assertEquals(listOf("adult1", "adult2"), editing.payers.map { it.id })
        vm.update { it.copy(title = "Péage", amount = "18,40") }
        vm.save()
        vm.uiState.first { it == ExpenseEditUiState.Done }
        val (saved, isNew) = repo.saved.single()
        assertTrue(isNew)
        assertEquals(1_840L, saved.amountCents)
        assertEquals("h2", saved.payerHouseholdId)

        val kid = ExpenseEditViewModel(repo, TripExpenseContext(trips, "t1"), FakeMembers(), "kid1", null, today = { day })
        assertEquals(ExpenseEditUiState.Unavailable, kid.uiState.first { it != ExpenseEditUiState.Loading })
    }
}
