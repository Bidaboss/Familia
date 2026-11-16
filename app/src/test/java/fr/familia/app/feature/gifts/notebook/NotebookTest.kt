package fr.familia.app.feature.gifts.notebook

import fr.familia.app.core.data.NotebookRepository
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.NotebookBudget
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.NotebookSource
import fr.familia.app.core.model.notebookBudgetId
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Fictional notebook of adult1 (PRD, récit 9 bis). */
class NotebookTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 15)
    private val christmas = NotebookOccasion(GiftOccasion.Christmas, 2026)
    private val birthday = NotebookOccasion(GiftOccasion.Birthday, 2027)
    private val lastChristmas = NotebookOccasion(GiftOccasion.Christmas, 2025)
    private val none = NotebookOccasion(null, null)

    private fun entry(
        id: String,
        person: String,
        occasion: NotebookOccasion = christmas,
        price: Double? = 10.0,
        status: GiftStatus = GiftStatus.Idea,
        personId: String? = person,
    ) = NotebookEntry(id, NotebookSource.Idea, personId, person, occasion, "Cadeau $id", null, price, null, status)

    @Test
    fun `the coming occasions come first, then the past ones, then no occasion`() {
        val sorted = sortedOccasions(setOf(none, lastChristmas, birthday, christmas), today)
        assertEquals(listOf(christmas, birthday, lastChristmas, none), sorted)
        assertEquals(christmas, defaultOccasion(sorted, today))
        assertEquals(birthday, defaultOccasion(listOf(lastChristmas, birthday), today))
    }

    @Test
    fun `one card per person for the chosen occasion, with what is bought and spent`() = runTest {
        val notebook = FakeNotebook(
            listOf(
                entry("a", "adult2", price = 20.0, status = GiftStatus.Bought),
                entry("b", "adult2", price = 15.0),
                // Someone outside the family, written twice with another case: one person.
                entry("c", "Mamie Jo", price = 30.0, status = GiftStatus.Given, personId = null),
                entry("d", "mamie jo", price = null, personId = null),
                entry("e", "adult2", occasion = birthday),
            ),
        )
        val vm = NotebookViewModel(notebook, "adult1", today = flowOf(today))
        val state = vm.uiState.first { it is NotebookUiState.Success } as NotebookUiState.Success
        assertEquals(christmas, state.selected)
        assertEquals(listOf("adult2", "Mamie Jo"), state.people.map { it.name })
        val adult2 = state.people.first()
        assertEquals(1, adult2.bought)
        assertEquals(20.0, adult2.spent, 0.001)
        assertEquals(2, state.people[1].entries.size)

        vm.select(birthday)
        val other = vm.uiState.first { it is NotebookUiState.Success && it.selected == birthday } as NotebookUiState.Success
        assertEquals(listOf("e"), other.people.single().entries.map { it.id })
    }

    @Test
    fun `the occasions of past years wait in the archive`() = runTest {
        val notebook = FakeNotebook(listOf(entry("a", "adult2"), entry("b", "adult2", occasion = lastChristmas), entry("c", "adult3", occasion = none)))
        val vm = NotebookViewModel(notebook, "adult1", today = flowOf(today))
        val closed = vm.uiState.first { it is NotebookUiState.Success } as NotebookUiState.Success
        assertEquals(listOf(christmas, none), closed.occasions)
        assertEquals(1, closed.archivedCount)

        vm.toggleArchive()
        val open = vm.uiState.first { (it as? NotebookUiState.Success)?.showArchive == true } as NotebookUiState.Success
        assertEquals(listOf(christmas, lastChristmas, none), open.occasions)

        // An occasion chosen from the archive stays in the row once it is closed again.
        vm.select(lastChristmas)
        vm.toggleArchive()
        val back = vm.uiState.first { (it as? NotebookUiState.Success)?.let { s -> !s.showArchive && s.selected == lastChristmas } == true }
        assertEquals(listOf(christmas, lastChristmas, none), (back as NotebookUiState.Success).occasions)
        assertEquals(listOf("b"), back.people.single().entries.map { it.id })
    }

    @Test
    fun `only the member's own notebook is asked for, and a state is saved`() = runTest {
        val notebook = FakeNotebook(listOf(entry("a", "adult2")))
        val vm = NotebookViewModel(notebook, "adult1", today = flowOf(today))
        val state = vm.uiState.first { it is NotebookUiState.Success } as NotebookUiState.Success
        vm.setStatus(state.people.single().entries.single(), GiftStatus.Wrapped)
        val after = vm.uiState.first { (it as? NotebookUiState.Success)?.people?.single()?.entries?.single()?.status == GiftStatus.Wrapped }
        assertTrue(after is NotebookUiState.Success)
        assertEquals(setOf("adult1"), notebook.askedFor)
    }

    @Test
    fun `a gift of « Mes envies » gets the occasion the member chooses`() = runTest {
        val wish = NotebookEntry("gift_w", NotebookSource.Claim, "adult2", "Adult2", none, "Un livre", null, 15.0, null, GiftStatus.Idea, listId = "wishes-adult2", itemId = "i")
        val notebook = FakeNotebook(listOf(wish))
        val vm = NotebookViewModel(notebook, "adult1", today = flowOf(today))
        vm.uiState.first { it is NotebookUiState.Success }
        vm.setOccasion(wish, christmas)
        val state = vm.uiState.first { (it as? NotebookUiState.Success)?.selected == christmas } as NotebookUiState.Success
        assertEquals(listOf("gift_w"), state.people.single().entries.map { it.id })
    }

    @Test
    fun `a budget belongs to one person and one occasion, and can be removed`() = runTest {
        val notebook = FakeNotebook(listOf(entry("a", "adult2", price = 20.0, status = GiftStatus.Bought), entry("b", "adult2", occasion = birthday)))
        val vm = NotebookViewModel(notebook, "adult1", today = flowOf(today))
        val state = vm.uiState.first { it is NotebookUiState.Success } as NotebookUiState.Success
        vm.setBudget(state.people.single(), christmas, 50.0)
        val withBudget = vm.uiState.first { (it as? NotebookUiState.Success)?.people?.single()?.budget == 50.0 } as NotebookUiState.Success
        assertEquals(20.0, withBudget.people.single().spent, 0.001)
        vm.select(birthday)
        val other = vm.uiState.first { (it as? NotebookUiState.Success)?.selected == birthday } as NotebookUiState.Success
        assertEquals(null, other.people.single().budget)
        vm.setBudget(withBudget.people.single(), christmas, null)
        vm.select(christmas)
        vm.uiState.first { (it as? NotebookUiState.Success)?.let { s -> s.selected == christmas && s.people.single().budget == null } == true }
        assertTrue(notebook.budgets.value.isEmpty())
    }

    @Test
    fun `a budget id is safe for Firestore and ignores the case of a first name`() {
        assertEquals(
            notebookBudgetId(null, "Mamie Jo", christmas),
            notebookBudgetId(null, " mamie jo", christmas),
        )
        assertTrue(notebookBudgetId(null, "Jo/Lou", christmas).none { it == '/' })
    }
}

class FakeNotebook(entries: List<NotebookEntry> = emptyList()) : NotebookRepository {
    val entries = MutableStateFlow(entries)
    val askedFor = mutableSetOf<String>()

    override fun observeEntries(memberId: String): Flow<List<NotebookEntry>> {
        askedFor += memberId
        return entries
    }

    override fun newEntryId(memberId: String): String = "new-entry"

    override suspend fun saveIdea(memberId: String, entry: NotebookEntry, isNew: Boolean) {
        askedFor += memberId
        entries.update { all -> all.filterNot { it.id == entry.id } + entry }
    }

    override suspend fun follow(memberId: String, entryId: String, status: GiftStatus, occasion: NotebookOccasion, note: String?) {
        askedFor += memberId
        entries.update { all -> all.map { if (it.id == entryId) it.copy(status = status, occasion = occasion, note = note) else it } }
    }

    override suspend fun deleteIdea(memberId: String, entryId: String) {
        askedFor += memberId
        entries.update { all -> all.filterNot { it.id == entryId } }
    }

    val budgets = MutableStateFlow<List<NotebookBudget>>(emptyList())

    override fun observeBudgets(memberId: String): Flow<List<NotebookBudget>> {
        askedFor += memberId
        return budgets
    }

    override suspend fun setBudget(memberId: String, budget: NotebookBudget, amount: Double?) {
        askedFor += memberId
        val id = notebookBudgetId(budget.personId, budget.personName, budget.occasion)
        budgets.update { all ->
            all.filterNot { notebookBudgetId(it.personId, it.personName, it.occasion) == id } +
                listOfNotNull(amount?.let { budget.copy(amount = it) })
        }
    }
}
