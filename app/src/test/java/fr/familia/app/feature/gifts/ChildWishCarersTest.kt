package fr.familia.app.feature.gifts

import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.gifts.detail.GiftListUiState
import fr.familia.app.feature.gifts.detail.GiftListViewModel
import fr.familia.app.feature.gifts.item.GiftItemEditUiState
import fr.familia.app.feature.gifts.item.GiftItemEditViewModel
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * The adults who look after a child edit and remove its wishes (récit 114): its responsible adults and
 * the chef of its household, never another adult, never a surprise idea kept from the child. Fictional
 * family of TestFamily: admin1 (chef) and adult1 in h1 with kid1 (parent adult1), adult2 in h2.
 */
class ChildWishCarersTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 25)
    private val list = GiftList("wishes-kid1", GiftListKind.Wishes, "Mes envies", null, null, "kid1", emptyList(), "kid1", "kid1")
    private val wish = GiftItem("ballon", "Un ballon", null, 20.0, null, "kid1", "Kid1", visibleToBeneficiary = true)
    private val idea = GiftItem("velo", "Un vélo", null, 150.0, null, "adult2", "Adult2", visibleToBeneficiary = false)

    private fun gifts() = FakeGifts(list).also { it.items.value = listOf(wish, idea) }

    private suspend fun rows(meId: String) =
        (GiftListViewModel(gifts(), FakeMembers(), meId, list.id, today = flowOf(today)).uiState.first { it is GiftListUiState.Success }
            as GiftListUiState.Success).items.associate { it.item.id to it.canEdit }

    @Test
    fun `its parent and the chef of its household edit a wish of the child, not another adult`() = runTest {
        assertEquals(true, rows("adult1")["ballon"])
        assertEquals(true, rows("admin1")["ballon"])
        assertEquals(false, rows("adult2")["ballon"])
    }

    @Test
    fun `a surprise idea stays with its author`() = runTest {
        assertEquals(false, rows("adult1")["velo"])
        assertEquals(true, rows("adult2")["velo"])
    }

    private suspend fun open(meId: String, itemId: String) =
        GiftItemEditViewModel(gifts(), FakeMembers(), meId, list.id, itemId).uiState.first { it !is GiftItemEditUiState.Loading }

    @Test
    fun `the form opens the wish of the child for its parent, without changing who shares it`() = runTest {
        val state = open("adult1", "ballon")
        assertTrue(state is GiftItemEditUiState.Editing)
        state as GiftItemEditUiState.Editing
        assertEquals("Un ballon", state.form.title)
        assertTrue(state.coChoices.isEmpty())
        assertFalse(state.isNew)
    }

    @Test
    fun `another adult never opens it`() = runTest {
        assertEquals(GiftItemEditUiState.Unavailable, open("adult2", "ballon"))
        assertEquals(GiftItemEditUiState.Unavailable, open("adult1", "velo"))
    }

    @Test
    fun `saving keeps the child as its author`() = runTest {
        val repo = gifts()
        val vm = GiftItemEditViewModel(repo, FakeMembers(), "adult1", list.id, "ballon")
        vm.uiState.first { it is GiftItemEditUiState.Editing }
        vm.update { it.copy(title = "Un ballon de foot") }
        vm.save()
        vm.uiState.first { it is GiftItemEditUiState.Done }
        val saved = repo.savedItems.last()
        assertEquals("kid1", saved.authorId)
        assertEquals("Kid1", saved.authorName)
        assertTrue(saved.visibleToBeneficiary)
    }
}
