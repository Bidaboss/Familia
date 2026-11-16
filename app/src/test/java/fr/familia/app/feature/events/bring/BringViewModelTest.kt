package fr.familia.app.feature.events.bring

import fr.familia.app.core.data.BringRepository
import fr.familia.app.core.model.BringItem
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeBring(initial: List<BringItem> = emptyList(), private val failWith: Exception? = null) : BringRepository {
    val items = MutableStateFlow(initial)
    private var next = 0
    override fun observe(eventId: String): Flow<List<BringItem>> = items
    override suspend fun add(eventId: String, label: String, authorId: String, authorName: String, take: Boolean, recipeId: String?) {
        failWith?.let { throw it }
        items.value = items.value + BringItem("n${next++}", label, authorId, if (take) authorId else null, if (take) authorName else null, recipeId)
    }
    override suspend fun take(eventId: String, itemId: String, memberId: String, firstName: String) =
        change(itemId) { it.copy(takerId = memberId, takerName = firstName) }
    override suspend fun release(eventId: String, itemId: String) = change(itemId) { it.copy(takerId = null, takerName = null) }
    override suspend fun delete(eventId: String, itemId: String) {
        failWith?.let { throw it }
        items.value = items.value.filter { it.id != itemId }
    }
    private fun change(id: String, block: (BringItem) -> BringItem) {
        failWith?.let { throw it }
        items.value = items.value.map { if (it.id == id) block(it) else it }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BringViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `a member adds a line taken at once, or a free one, trimmed`() = runTest {
        val repo = FakeBring()
        val viewModel = BringViewModel(repo, FakeMembers(), "adult1", "e1", canManageEvent = false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertTrue(viewModel.add("  Le pain ", take = true))
        assertTrue(viewModel.add("Le dessert", take = false))
        assertFalse(viewModel.add("   ", take = true))
        assertFalse(viewModel.add("x".repeat(61), take = true))
        val rows = viewModel.uiState.value.rows
        assertEquals(listOf("Le pain", "Le dessert"), rows.map { it.item.label })
        assertEquals("Adult1", rows[0].item.takerName)
        assertTrue(rows[0].isMine)
        assertNull(rows[1].item.takerId)
    }

    @Test
    fun `a line keeps the recipe joined to it, and one without stays without`() = runTest {
        val repo = FakeBring()
        val viewModel = BringViewModel(repo, FakeMembers(), "adult1", "e1", canManageEvent = false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertTrue(viewModel.add("La tarte aux pommes", take = true, recipeId = "r1"))
        assertTrue(viewModel.add("Le pain", take = false))
        assertEquals(listOf("r1", null), viewModel.uiState.value.rows.map { it.item.recipeId })
    }

    @Test
    fun `a free line is taken, then let go`() = runTest {
        val repo = FakeBring(listOf(BringItem("b1", "Le fromage", "adult2", null, null)))
        val viewModel = BringViewModel(repo, FakeMembers(), "kid1", "e1", canManageEvent = false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.take("b1")
        assertEquals("kid1", repo.items.value.single().takerId)
        assertTrue(viewModel.uiState.value.rows.single().canRelease)
        assertFalse(viewModel.uiState.value.rows.single().canDelete)
        viewModel.release("b1")
        assertNull(repo.items.value.single().takerId)
    }

    @Test
    fun `nobody takes a line already taken, nor frees someone else's without the right`() = runTest {
        val repo = FakeBring(listOf(BringItem("b1", "Le vin", "adult2", "adult2", "Adult2")))
        val viewModel = BringViewModel(repo, FakeMembers(), "adult1", "e1", canManageEvent = false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.take("b1")
        viewModel.release("b1")
        viewModel.delete("b1")
        assertEquals("adult2", repo.items.value.single().takerId)
    }

    @Test
    fun `whoever manages the event frees and deletes any line`() = runTest {
        val repo = FakeBring(listOf(BringItem("b1", "Le vin", "adult2", "adult2", "Adult2")))
        val viewModel = BringViewModel(repo, FakeMembers(), "admin1", "e1", canManageEvent = true)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.release("b1")
        assertNull(repo.items.value.single().takerId)
        viewModel.delete("b1")
        assertTrue(repo.items.value.isEmpty())
    }

    @Test
    fun `a refused write says so`() = runTest {
        val repo = FakeBring(listOf(BringItem("b1", "Le pain", "adult1", null, null)), failWith = IllegalStateException("refusé"))
        val viewModel = BringViewModel(repo, FakeMembers(), "adult1", "e1", canManageEvent = false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.take("b1")
        assertTrue(viewModel.uiState.value.failed)
        viewModel.dismissError()
        assertFalse(viewModel.uiState.value.failed)
    }
}
