package fr.familia.app.feature.expenses

import fr.familia.app.core.model.ExpenseContainerType
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.feature.events.FakeEvents
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.FakeRsvps
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Fictional event e1 of the test family, a meal by adult2: adult1 and kid1 come, admin1 answered « Peut-être ». */
class ExpenseContextTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val meal = TestFamily.event("e1", TestFamily.otherAdult)
    private val answers = listOf(
        Rsvp("adult1", RsvpStatus.Yes, "adult1"),
        Rsvp("kid1", RsvpStatus.Yes, "adult1"),
        Rsvp("admin1", RsvpStatus.Maybe, "admin1"),
        Rsvp("kid2", RsvpStatus.No, "adult2"),
    )

    private fun source(): EventExpenseContext {
        val rsvps = FakeRsvps().apply { this.answers.value = mapOf("e1" to this@ExpenseContextTest.answers) }
        return EventExpenseContext(FakeEvents(listOf(meal)), rsvps, "e1")
    }

    @Test
    fun `those who come and the author share the costs of an event, never « Peut-être »`() {
        val context = eventContext(meal, answers)!!
        assertEquals("Repas e1", context.title)
        assertEquals("adult2", context.creatorId)
        assertEquals(setOf("adult1", "kid1", "adult2"), context.participantIds)
        assertNull(eventContext(null, answers))
    }

    @Test
    fun `the accounts of an event open with the author as creator and the shares of who comes`() = runTest {
        val repo = FakeExpenses()
        val vm = ExpensesViewModel(repo, source(), FakeMembers(), "adult1")
        val state = vm.uiState.first { it is ExpensesUiState.Success } as ExpensesUiState.Success
        assertEquals("Repas e1", state.title)
        assertEquals("adult2", repo.opened.single().first)
        assertEquals(mapOf("h1" to 1.5, "h2" to 1.0), repo.opened.single().second)
        assertEquals(ExpenseContainerType.Event, source().container.type)
        assertEquals("event_e1", source().container.bookId)
    }

    @Test
    fun `« Peut-être » does not open the accounts of an event`() = runTest {
        val vm = ExpensesViewModel(FakeExpenses(), source(), FakeMembers(), "admin1")
        assertEquals(ExpensesUiState.NotAllowed, vm.uiState.first { it !is ExpensesUiState.Loading })
    }
}
