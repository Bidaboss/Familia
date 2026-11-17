package fr.familia.app.feature.lending

import fr.familia.app.core.data.LendItemRepository
import fr.familia.app.core.model.LendCategory
import fr.familia.app.core.model.LendItem
import fr.familia.app.core.model.LendStatus
import fr.familia.app.core.model.Member
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.lending.list.LendItemsUiState
import fr.familia.app.feature.lending.list.LendItemsViewModel
import fr.familia.app.feature.lending.list.matchesLendItem
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Things to lend kept in memory, as Firestore would. */
class FakeLendItems(initial: List<LendItem> = emptyList()) : LendItemRepository {
    val items = MutableStateFlow(initial)
    override fun observeAll(): Flow<List<LendItem>> = items
    override fun observe(id: String): Flow<LendItem?> = items.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun get(id: String): LendItem? = items.value.firstOrNull { it.id == id }
    override fun newId(): String = "i-new"
    override suspend fun save(item: LendItem, isNew: Boolean) {
        items.value = items.value.filter { it.id != item.id } + item
    }
    override suspend fun delete(id: String) {
        items.value = items.value.filter { it.id != id }
    }
    private fun change(id: String, block: (LendItem) -> LendItem) {
        items.value = items.value.map { if (it.id == id) block(it) else it }
    }
    override suspend fun ask(id: String, me: Member, from: Instant?, until: Instant?) =
        change(id) { it.copy(status = LendStatus.Asked, borrowerId = me.id, borrowerName = me.firstName, from = from, until = until) }
    override suspend fun accept(id: String) = change(id) { it.copy(status = LendStatus.Lent, lentAt = Instant.EPOCH) }
    override suspend fun release(id: String) =
        change(id) { it.copy(status = LendStatus.Available, borrowerId = null, borrowerName = null, from = null, until = null, lentAt = null) }
}

/** « On se prête » in Entraide (PRD, récit 51). Fictional family of TestFamily. */
@OptIn(ExperimentalCoroutinesApi::class)
class LendingTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun item(id: String, name: String, category: LendCategory = LendCategory.Kitchen, owner: String = "adult1", status: LendStatus = LendStatus.Available, borrower: String? = null) =
        LendItem(id, name, category = category, ownerId = owner, ownerName = owner, status = status, borrowerId = borrower, borrowerName = borrower)

    private val family = listOf(
        item("i1", "Appareil à raclette"),
        item("i2", "Lit parapluie", LendCategory.Baby, owner = "adult2", status = LendStatus.Lent, borrower = "adult1"),
        item("i3", "Remorque", LendCategory.Diy, status = LendStatus.Asked, borrower = "adult2"),
    )

    @Test
    fun `the list shows every thing by name, and who has it`() = runTest {
        val viewModel = LendItemsViewModel(FakeLendItems(family), FakeMembers(), "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value as LendItemsUiState.Success
        assertEquals(listOf("i1", "i2", "i3"), state.items.map { it.item.id })
        assertTrue(state.items[0].isMine)
        assertTrue(state.items[1].iHaveIt)
        assertTrue(state.canAdd)
    }

    @Test
    fun `search by name without accents, a category and the free things only`() = runTest {
        val viewModel = LendItemsViewModel(FakeLendItems(family), FakeMembers(), "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.search("APPAREIL a")
        assertEquals(listOf("i1"), (viewModel.uiState.value as LendItemsUiState.Success).items.map { it.item.id })
        viewModel.search("")
        viewModel.pickCategory(LendCategory.Baby)
        assertEquals(listOf("i2"), (viewModel.uiState.value as LendItemsUiState.Success).items.map { it.item.id })
        viewModel.pickCategory(LendCategory.Baby)
        viewModel.toggleAvailableOnly()
        val state = viewModel.uiState.value as LendItemsUiState.Success
        assertEquals(listOf("i1"), state.items.map { it.item.id })
        assertTrue(state.hasItems)
    }

    @Test
    fun `a child sees the list but adds nothing and never asks`() = runTest {
        val viewModel = LendItemsViewModel(FakeLendItems(family), FakeMembers(), "kid1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value as LendItemsUiState.Success
        assertEquals(3, state.items.size)
        assertFalse(state.canAdd)
        assertFalse(canAskLendItem(family[0], TestFamily.kid))
    }

    @Test
    fun `only an adult who is not the owner asks, and only a free thing`() {
        assertTrue(canAskLendItem(family[0], TestFamily.otherAdult))
        assertFalse(canAskLendItem(family[0], TestFamily.adult))
        assertFalse(canAskLendItem(family[2], TestFamily.admin))
        assertTrue(canManageLendItem(family[0], TestFamily.admin, TestFamily.family))
        assertFalse(canManageLendItem(family[0], TestFamily.otherAdult, TestFamily.family))
    }

    @Test
    fun `every word of the search must be in the name`() {
        assertTrue(matchesLendItem(family[0], "raclette appareil"))
        assertFalse(matchesLendItem(family[0], "raclette four"))
    }
}
