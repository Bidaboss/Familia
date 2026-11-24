package fr.familia.app.feature.gifts.notebook

import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.model.ClaimType
import fr.familia.app.core.model.GiftClaim
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.NotebookSource
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.gifts.FakeGifts
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** « Proposer à la famille », in the fictional family of TestFamily. */
class ProposeIdeaTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val christmas = NotebookOccasion(GiftOccasion.Christmas, 2026)

    private fun idea(personId: String? = "adult2", status: GiftStatus = GiftStatus.Idea) = NotebookEntry(
        "e1", NotebookSource.Idea, personId, personId ?: "Mamie Jo", christmas, "Une écharpe", "https://example.com/echarpe", 25.0,
        "Cachée dans le placard", status,
    )

    private fun list(id: String, kind: GiftListKind, beneficiary: String, title: String = id, revealed: Boolean = false) =
        GiftList(id, kind, title, GiftOccasion.Christmas, LocalDate.of(2026, 12, 25), beneficiary, listOf("adult1", "adult2"), "adult1", "Adult1", revealed)

    /** Several lists, and the writes made, in order. */
    private class Gifts(lists: List<GiftList>, private val failItem: Boolean = false, var failClaim: Boolean = false) :
        GiftRepository by FakeGifts(null) {
        val lists = MutableStateFlow(lists)
        val writes = mutableListOf<String>()
        val items = mutableListOf<Pair<String, GiftItem>>()
        val claims = mutableListOf<GiftClaim>()
        val created = mutableListOf<GiftList>()

        override suspend fun saveList(list: GiftList, isNew: Boolean) {
            writes += "list"
            created += list
        }
        override fun observeVisibleLists(memberId: String): Flow<List<GiftList>> = lists
        override suspend fun saveItem(listId: String, item: GiftItem, isNew: Boolean) {
            if (failItem) error("refused")
            writes += "item"
            items += listId to item
        }
        private var nextId = 0
        override fun newItemId(listId: String): String = "item-${nextId++}"
        override suspend fun claim(listId: String, claim: GiftClaim) {
            if (failClaim) error("refused")
            writes += "claim"
            claims += claim
        }
    }

    private suspend fun ProposeIdeaViewModel.settled() = uiState.first { it !is ProposeIdeaUiState.Loading }

    private val ideasOfAdult2 = GiftList("ideas-adult2", GiftListKind.Ideas, "Idées", null, null, "adult2", emptyList(), "adult3", "Adult3")

    @Test
    fun `the idea goes hidden to the ideas of the family, created if missing, and leaves the notebook`() = runTest {
        val gifts = Gifts(listOf(list("s1", GiftListKind.Surprise, "adult2"), list("wishes-adult2", GiftListKind.Wishes, "adult2")))
        val notebook = FakeNotebook(listOf(idea()))
        val vm = ProposeIdeaViewModel(notebook, gifts, FakeMembers(), "adult1", "e1")
        val state = vm.settled() as ProposeIdeaUiState.Choosing
        assertEquals(listOf("ideas-adult2"), state.lists.map { it.id })
        assertEquals("ideas-adult2", state.selectedListId)
        vm.setTakeIt(true)
        vm.propose()
        assertEquals(ProposeIdeaUiState.Done("ideas-adult2"), vm.uiState.first { it is ProposeIdeaUiState.Done })
        assertEquals(listOf("list", "item", "claim"), gifts.writes)
        assertEquals(GiftListKind.Ideas, gifts.created.single().kind)
        assertEquals("adult2", gifts.created.single().beneficiaryId)
        val (listId, item) = gifts.items.single()
        assertEquals("ideas-adult2", listId)
        assertEquals("Une écharpe", item.title)
        assertFalse(item.visibleToBeneficiary)
        assertNull("the private note stays private", item.note)
        assertEquals(ClaimType.Solo, gifts.claims.single().type)
        assertEquals("adult1", gifts.claims.single().holderId)
        assertTrue(notebook.entries.value.isEmpty())
    }

    @Test
    fun `an existing list of ideas is not created again`() = runTest {
        val gifts = Gifts(listOf(ideasOfAdult2))
        val vm = ProposeIdeaViewModel(FakeNotebook(listOf(idea())), gifts, FakeMembers(), "adult1", "e1")
        assertNull((vm.settled() as ProposeIdeaUiState.Choosing).newList)
        vm.propose()
        vm.uiState.first { it is ProposeIdeaUiState.Done }
        assertEquals(listOf("item"), gifts.writes)
    }

    @Test
    fun `a refused item keeps the idea in the notebook`() = runTest {
        val notebook = FakeNotebook(listOf(idea()))
        val vm = ProposeIdeaViewModel(notebook, Gifts(listOf(ideasOfAdult2), failItem = true), FakeMembers(), "adult1", "e1")
        assertEquals("ideas-adult2", (vm.settled() as ProposeIdeaUiState.Choosing).selectedListId)
        vm.propose()
        val state = vm.uiState.first { it is ProposeIdeaUiState.Choosing && it.saveFailed } as ProposeIdeaUiState.Choosing
        assertFalse(state.isSaving)
        assertEquals(1, notebook.entries.value.size)
    }

    @Test
    fun `a new attempt after a refused reservation writes no second item`() = runTest {
        val gifts = Gifts(listOf(ideasOfAdult2), failClaim = true)
        val notebook = FakeNotebook(listOf(idea()))
        val vm = ProposeIdeaViewModel(notebook, gifts, FakeMembers(), "adult1", "e1")
        vm.settled()
        vm.setTakeIt(true)
        vm.propose()
        vm.uiState.first { it is ProposeIdeaUiState.Choosing && it.saveFailed }
        gifts.failClaim = false
        vm.propose()
        vm.uiState.first { it is ProposeIdeaUiState.Done }
        assertEquals(listOf("item", "claim"), gifts.writes)
        assertEquals("item-0", gifts.claims.single().itemId)
        assertTrue(notebook.entries.value.isEmpty())
    }

    @Test
    fun `only an idea still to buy, for a member, is proposed`() = runTest {
        for (entry in listOf(idea(status = GiftStatus.Bought), idea(personId = null))) {
            val vm = ProposeIdeaViewModel(FakeNotebook(listOf(entry)), Gifts(emptyList()), FakeMembers(), "adult1", "e1")
            assertEquals(ProposeIdeaUiState.Unavailable, vm.settled())
        }
    }
}
