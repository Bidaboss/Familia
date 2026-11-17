package fr.familia.app.feature.lending

import fr.familia.app.core.data.LendItemRepository
import fr.familia.app.core.model.LendCategory
import fr.familia.app.core.model.LendItem
import fr.familia.app.core.model.LendStatus
import fr.familia.app.core.model.withoutLoan
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.photo.SinglePhotoSaver
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.lending.detail.LendItemDetailUiState
import fr.familia.app.feature.lending.detail.LendItemDetailViewModel
import fr.familia.app.feature.lending.edit.LendFormError
import fr.familia.app.feature.lending.edit.LendItemEditUiState
import fr.familia.app.feature.lending.edit.LendItemEditViewModel
import fr.familia.app.feature.lending.list.LendItemsUiState
import fr.familia.app.feature.lending.list.LendItemsViewModel
import fr.familia.app.feature.lending.list.matchesLendItem
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Things to lend kept in memory, as Firestore would. */
class FakeLendItems(initial: List<LendItem> = emptyList()) : LendItemRepository {
    val items = MutableStateFlow(initial)
    /** As the rules let [readerId] read it: the loan only for the owner and the borrower. */
    private fun LendItem.seenBy(readerId: String) = if (readerId == ownerId || readerId == borrowerId) this else withoutLoan()
    override fun observeAll(readerId: String): Flow<List<LendItem>> = items.map { list -> list.map { it.seenBy(readerId) } }
    override fun observe(id: String, readerId: String): Flow<LendItem?> =
        items.map { list -> list.firstOrNull { it.id == id }?.seenBy(readerId) }
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
    override suspend fun ask(id: String, ownerId: String, me: Member, from: Instant?, until: Instant?) =
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
    fun `the rest of the family sees only Prete, with no name and no date`() = runTest {
        val viewModel = LendItemsViewModel(FakeLendItems(family), FakeMembers(), "admin1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val items = (viewModel.uiState.value as LendItemsUiState.Success).items.map { it.item }
        // Lent by adult2 to adult1, and asked by adult2 of adult1: both only « Prêté » here.
        for (item in items.filter { it.id in setOf("i2", "i3") }) {
            assertEquals(LendStatus.Lent, item.status)
            assertNull(item.borrowerId)
            assertNull(item.borrowerName)
            assertNull(item.lentAt)
        }
        assertFalse(canAskLendItem(items.first { it.id == "i3" }, TestFamily.admin))
        val owner = detail(FakeLendItems(family), "adult1", "i3")
        assertEquals("adult2", owner.ready().item.borrowerName)
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

    // --- The form --------------------------------------------------------------------------------

    private class FakeStorage : PhotoStorage {
        override suspend fun upload(path: String, jpeg: ByteArray) = Unit
        override suspend fun downloadUrl(path: String) = "https://example.com/$path"
        override suspend fun delete(path: String) = Unit
    }

    private val photos = SinglePhotoSaver({ _, _, _ -> }, FakeStorage())

    private fun edit(repo: FakeLendItems, me: String = "adult1", itemId: String? = null) =
        LendItemEditViewModel(repo, FakeMembers(), me, itemId, photos = photos)

    @Test
    fun `a new thing needs a name and a category, and is free in my name`() {
        val repo = FakeLendItems()
        val viewModel = edit(repo)
        viewModel.save()
        assertEquals(setOf(LendFormError.NameMissing, LendFormError.CategoryMissing), (viewModel.uiState.value as LendItemEditUiState.Editing).errors)
        viewModel.update { it.copy(name = " Remorque ", category = LendCategory.Diy, note = "Avec sa bâche") }
        viewModel.save()
        assertEquals(LendItemEditUiState.Done, viewModel.uiState.value)
        val saved = repo.items.value.single()
        assertEquals("i-new", saved.id)
        assertEquals("Remorque", saved.name)
        assertEquals("adult1", saved.ownerId)
        assertEquals(LendStatus.Available, saved.status)
        assertNull(saved.photo)
    }

    @Test
    fun `a change keeps the loan as it is`() {
        val repo = FakeLendItems(family)
        val viewModel = edit(repo, me = "adult2", itemId = "i2")
        viewModel.update { it.copy(name = "Lit parapluie bleu") }
        viewModel.save()
        val saved = repo.items.value.first { it.id == "i2" }
        assertEquals("Lit parapluie bleu", saved.name)
        assertEquals(LendStatus.Lent, saved.status)
        assertEquals("adult1", saved.borrowerId)
    }

    @Test
    fun `a child adds nothing, and nobody but the owner or the admin changes a thing`() {
        assertEquals(LendItemEditUiState.Unavailable, edit(FakeLendItems(), me = "kid1").uiState.value)
        assertEquals(LendItemEditUiState.Unavailable, edit(FakeLendItems(family), me = "adult2", itemId = "i1").uiState.value)
        assertTrue(edit(FakeLendItems(family), me = "admin1", itemId = "i1").uiState.value is LendItemEditUiState.Editing)
    }

    // --- One thing and its loan ------------------------------------------------------------------

    private fun TestScope.detail(repo: FakeLendItems, me: String, itemId: String): LendItemDetailViewModel {
        val viewModel = LendItemDetailViewModel(repo, FakeMembers(), me, itemId)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun LendItemDetailViewModel.ready() = uiState.value as LendItemDetailUiState.Ready

    @Test
    fun `an adult asks with dates, the owner says D'accord, then Rendu frees it`() = runTest {
        val repo = FakeLendItems(family)
        val asker = detail(repo, "adult2", "i1")
        assertTrue(asker.ready().canAsk)
        asker.ask(LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 15))
        var item = repo.items.value.first { it.id == "i1" }
        assertEquals(LendStatus.Asked, item.status)
        assertEquals("adult2", item.borrowerId)
        assertEquals(Instant.parse("2026-12-11T23:00:00Z"), item.from)
        assertEquals(Instant.parse("2026-12-14T23:00:00Z"), item.until)
        assertTrue(asker.ready().canDropAsk)
        val owner = detail(repo, "adult1", "i1")
        assertTrue(owner.ready().canAnswer)
        owner.accept()
        assertEquals(LendStatus.Lent, repo.items.value.first { it.id == "i1" }.status)
        assertTrue(asker.ready().canReturn)
        asker.release()
        item = repo.items.value.first { it.id == "i1" }
        assertEquals(LendStatus.Available, item.status)
        assertNull(item.borrowerId)
    }

    @Test
    fun `Pas cette fois frees it, and a return before the start is dropped`() = runTest {
        val repo = FakeLendItems(family)
        detail(repo, "adult2", "i1").ask(LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 10))
        assertNull(repo.items.value.first { it.id == "i1" }.until)
        val owner = detail(repo, "adult1", "i1")
        owner.release()
        assertEquals(LendStatus.Available, repo.items.value.first { it.id == "i1" }.status)
    }

    @Test
    fun `a child and the owner never ask, and nobody else answers or returns`() = runTest {
        val repo = FakeLendItems(family)
        assertFalse(detail(repo, "kid1", "i1").ready().canAsk)
        assertFalse(detail(repo, "adult1", "i1").ready().canAsk)
        val admin = detail(repo, "admin1", "i3")
        assertFalse(admin.ready().canAnswer)
        assertTrue(admin.ready().canManage)
        admin.release()
        assertEquals(LendStatus.Asked, repo.items.value.first { it.id == "i3" }.status)
        assertFalse(detail(repo, "admin1", "i2").ready().canReturn)
    }
}
