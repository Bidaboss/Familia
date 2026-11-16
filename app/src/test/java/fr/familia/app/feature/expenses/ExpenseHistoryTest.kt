package fr.familia.app.feature.expenses

import fr.familia.app.core.model.ExpenseHistoryAction
import fr.familia.app.core.model.ExpenseHistoryEntry
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ExpenseHistoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun `the fields that changed are told the way a member thinks of them`() {
        assertEquals(
            listOf(ExpenseField.Amount, ExpenseField.Split, ExpenseField.Currency),
            fieldsOf(listOf("householdIds", "rate", "amountCents", "amounts", "originalCents", "unknown")),
        )
    }

    @Test
    fun `the history shows the newest change first, in the time of the phone`() = runTest {
        val repo = FakeExpenses()
        repo.history.value = mapOf(
            "e1" to listOf(
                ExpenseHistoryEntry("h1", ExpenseHistoryAction.Created, "Camille", "Courses", 8_000, at = Instant.parse("2027-07-12T09:30:00Z")),
                ExpenseHistoryEntry("h2", ExpenseHistoryAction.Edited, "Sacha", "Courses du marché", 8_450, listOf("amountCents", "title"), Instant.parse("2027-07-12T17:05:00Z")),
            ),
        )
        val vm = ExpenseHistoryViewModel(repo, "t1", "e1", paris)
        val state = vm.uiState.first { it is ExpenseHistoryUiState.Success } as ExpenseHistoryUiState.Success
        assertEquals("Courses du marché", state.title)
        assertEquals(listOf("h2", "h1"), state.rows.map { it.id })
        assertEquals(listOf(ExpenseField.Title, ExpenseField.Amount), state.rows.first().fields)
        assertEquals(LocalDateTime.of(2027, 7, 12, 19, 5), state.rows.first().at)
    }
}
