package fr.familia.app.feature.gifts

import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.gifts.detail.GiftListUiState
import fr.familia.app.feature.gifts.detail.GiftListViewModel
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** « Je l'ai reçue » (récit 10 bis, rév. 232). Fictional family of TestFamily. */
class WishReceivedTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 17)

    private fun wishes(beneficiary: String) =
        GiftList("wishes-$beneficiary", GiftListKind.Wishes, "Mes envies", null, null, beneficiary, emptyList(), beneficiary, beneficiary)

    private suspend fun state(meId: String, list: GiftList): GiftListUiState.Success =
        GiftListViewModel(FakeGifts(list), FakeMembers(), meId, list.id, today = flowOf(today))
            .uiState.first { it is GiftListUiState.Success } as GiftListUiState.Success

    @Test
    fun `whoever keeps the lists of the person marks a wish received, nobody else`() = runTest {
        assertTrue(state("adult1", wishes("adult1")).canMarkReceived)
        assertFalse(state("adult2", wishes("adult1")).canMarkReceived)
        // The adults of a child without the app do it for them.
        assertTrue(state("adult2", wishes("kid2")).canMarkReceived)
        assertFalse(state("adult1", wishes("kid2")).canMarkReceived)
    }

    @Test
    fun `never on a list of an occasion nor on the ideas of the family`() = runTest {
        val own = GiftList("noel", GiftListKind.Own, "Noël", null, LocalDate.of(2026, 12, 25), "adult1", listOf("adult1"), "adult1", "adult1")
        assertFalse(state("adult1", own).canMarkReceived)
        val ideas = GiftList("ideas-adult1", GiftListKind.Ideas, "Idées de la famille", null, null, "adult1", emptyList(), "adult2", "adult2")
        assertFalse(state("adult2", ideas).canMarkReceived)
    }
}
