package fr.familia.app.feature.gifts.surprise

import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.Surprise
import fr.familia.app.core.model.SurpriseOccasion
import fr.familia.app.core.model.SurpriseTake
import fr.familia.app.core.model.TakeKind
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.gifts.FakeGifts
import fr.familia.app.feature.gifts.detail.GiftListUiState
import fr.familia.app.feature.gifts.detail.GiftListViewModel
import fr.familia.app.feature.gifts.detail.WishSurpriseState
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * La bascule (plan Cadeaux et Surprise, PR 3; brief §5, fiche C8): the wish of someone else is read
 * only, its state comes from the surprises I am in, « Offrir cette envie » opens mine or starts one.
 */
class WishSwitchTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val list = GiftList("wishes-adult2", GiftListKind.Wishes, "Mes envies", null, null, "adult2", emptyList(), "adult2", "adult2")
    private val gifts = FakeGifts(list).apply {
        items.value = listOf(
            GiftItem("bike", "Un vélo", null, 120.0, null, "adult2", "Adult2", true),
            GiftItem("book", "Un livre", null, 15.0, null, "adult2", "Adult2", true),
        )
    }
    private val surprise = Surprise("s1", "adult2", "Noël", SurpriseOccasion.Christmas, LocalDate.of(2026, 12, 25), listOf("adult1", "admin1"), "adult1", "Adult1")

    private fun viewModel(surprises: FakeSurprises, me: String = "adult1") = GiftListViewModel(
        gifts, FakeMembers(), me, list.id, today = flowOf(LocalDate.of(2026, 11, 25)), surprises = surprises, surprisesOnly = true,
    )

    private suspend fun GiftListViewModel.success() = uiState.first { it is GiftListUiState.Success } as GiftListUiState.Success

    @Test
    fun `no reservation from the card any more, the state comes from my surprise`() = runTest {
        val surprises = FakeSurprises(listOf(surprise))
        surprises.takes.value = mapOf("s1" to listOf(SurpriseTake("wish-bike", TakeKind.Shared, "admin1", goal = 120, sharesTotal = 40)))
        val state = viewModel(surprises).success()
        assertFalse(state.canReserve)
        assertFalse(state.canShare)
        assertFalse(state.canContribute)
        assertTrue(state.canOffer)
        assertEquals("s1", state.surpriseId)
        assertEquals(WishSurpriseState.Joint("Admin1", 40, 120), state.wishStates["bike"])
        assertEquals(WishSurpriseState.Free, state.wishStates["book"])
    }

    @Test
    fun `without a surprise, « Offrir cette envie » starts one, and the states say nothing`() = runTest {
        val state = viewModel(FakeSurprises()).success()
        assertTrue(state.canOffer)
        assertNull(state.surpriseId)
        assertEquals(WishSurpriseState.Free, state.wishStates["bike"])
    }

    @Test
    fun `my own wishes never show where they stand`() = runTest {
        val state = viewModel(FakeSurprises(listOf(surprise)), me = "adult2").success()
        assertFalse(state.canOffer)
        assertTrue(state.wishStates.isEmpty())
    }
}
