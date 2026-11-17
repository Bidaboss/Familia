package fr.familia.app.feature.gifts

import androidx.lifecycle.SavedStateHandle
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.LinkPreviewRepository
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.gifts.item.GiftItemEditUiState
import fr.familia.app.feature.gifts.item.GiftItemEditViewModel
import fr.familia.app.feature.gifts.item.GiftItemForm
import fr.familia.app.feature.gifts.share.ShareEntry
import fr.familia.app.feature.gifts.share.shareEntryFor
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** « Ajouter à mes envies » from another app (PRD, récit 50). Fictional family of TestFamily. */
class WishFromShareTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val shared = GiftItemForm(title = "Un puzzle", url = "https://boutique.example/puzzle")

    /** No list exists yet; records the lists and items written, and where. */
    private class RecordingGifts(private val fake: FakeGifts = FakeGifts(null)) : GiftRepository by fake {
        val lists = mutableListOf<GiftList>()
        val items = mutableListOf<Pair<String, GiftItem>>()
        override suspend fun saveList(list: GiftList, isNew: Boolean) {
            lists += list
        }
        override suspend fun saveItem(listId: String, item: GiftItem, isNew: Boolean) {
            items += listId to item
        }
    }

    private fun viewModel(me: String, gifts: GiftRepository = RecordingGifts(), saved: SavedStateHandle = SavedStateHandle()) =
        GiftItemEditViewModel(gifts, FakeMembers(), me, "wishes-$me", null, saved = saved, shared = shared, fromShare = true)

    private suspend fun GiftItemEditViewModel.editing() =
        uiState.first { it is GiftItemEditUiState.Editing } as GiftItemEditUiState.Editing

    @Test
    fun `the share menu shows Familia only with its modules, récit 50 first`() {
        assertNull(shareEntryFor(gifts = false, shareToWishes = true, wishFromShare = true))
        assertNull(shareEntryFor(gifts = true, shareToWishes = false, wishFromShare = false))
        assertEquals(ShareEntry.Wishes, shareEntryFor(gifts = true, shareToWishes = true, wishFromShare = false))
        assertEquals(ShareEntry.AddToWishes, shareEntryFor(gifts = true, shareToWishes = true, wishFromShare = true))
        assertEquals(ShareEntry.AddToWishes, shareEntryFor(gifts = true, shareToWishes = false, wishFromShare = true))
    }

    @Test
    fun `an adult chooses « Pour moi » or an idea for anyone else`() = runTest {
        val state = viewModel("adult1").editing()
        assertEquals(shared, state.form)
        assertNull("« Pour moi » by default", state.targetId)
        // By household, as in Famille: « Foyer Deux » (adult2, kid2), then « Foyer Un » (admin1, kid1).
        assertEquals(listOf(null, "adult2", "kid2", "admin1", "kid1"), state.targets.map { it.memberId })
        assertFalse(state.hiddenFromBeneficiary)
    }

    @Test
    fun `a child adds only to their own wishes`() = runTest {
        val gifts = RecordingGifts()
        val vm = viewModel("kid1", gifts)
        assertTrue(vm.editing().targets.isEmpty())
        vm.chooseTarget("adult1")
        vm.save()
        vm.uiState.first { it == GiftItemEditUiState.Done }
        assertEquals("wishes-kid1", gifts.items.single().first)
    }

    @Test
    fun `« Une idée pour… » goes to the ideas of the family, hidden from the person`() = runTest {
        val gifts = RecordingGifts()
        val vm = viewModel("adult1", gifts)
        vm.editing()
        vm.chooseTarget("adult2")
        val state = vm.uiState.first { it is GiftItemEditUiState.Editing && it.targetId == "adult2" } as GiftItemEditUiState.Editing
        assertTrue(state.hiddenFromBeneficiary)
        assertEquals("Adult2", state.beneficiaryName)
        assertEquals(shared, state.form)
        vm.save()
        vm.uiState.first { it == GiftItemEditUiState.Done }
        assertEquals("ideas-adult2", gifts.lists.single().id)
        val (listId, item) = gifts.items.single()
        assertEquals("ideas-adult2", listId)
        assertFalse(item.visibleToBeneficiary)
        assertEquals("https://boutique.example/puzzle", item.url)
        assertNull("an idea keeps no occasion", item.occasion)
    }

    @Test
    fun `back to « Pour moi », the wish is mine and visible again`() = runTest {
        val gifts = RecordingGifts()
        val vm = viewModel("adult1", gifts)
        vm.editing()
        vm.chooseTarget("adult2")
        vm.uiState.first { it is GiftItemEditUiState.Editing && it.targetId == "adult2" }
        vm.chooseTarget(null)
        val state = vm.uiState.first { it is GiftItemEditUiState.Editing && it.targetId == null } as GiftItemEditUiState.Editing
        assertFalse(state.hiddenFromBeneficiary)
        vm.save()
        vm.uiState.first { it == GiftItemEditUiState.Done }
        val (listId, item) = gifts.items.single()
        assertEquals("wishes-adult1", listId)
        assertTrue(item.visibleToBeneficiary)
    }

    @Test
    fun `the choice survives Android closing the app`() = runTest {
        val saved = SavedStateHandle()
        val first = viewModel("adult1", saved = saved)
        first.editing()
        first.chooseTarget("kid2")
        first.uiState.first { it is GiftItemEditUiState.Editing && it.targetId == "kid2" }
        val again = viewModel("adult1", saved = saved).editing()
        assertEquals("kid2", again.targetId)
        assertTrue(again.hiddenFromBeneficiary)
    }

    @Test
    fun `nothing is read from the shop, even with a reader at hand`() = runTest {
        var asked = 0
        val previews = LinkPreviewRepository { asked++; null }
        val vm = GiftItemEditViewModel(RecordingGifts(), FakeMembers(), "adult1", "wishes-adult1", null, shared = shared, previews = previews, fromShare = true)
        assertFalse(vm.editing().previewLoading)
        assertEquals(0, asked)
    }
}
