package fr.familia.app.feature.gifts.received

import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.ReceivedGift
import fr.familia.app.feature.gifts.FakeGifts
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** « On m'a offert » (récit 10 bis), in a fictional family. */
class ReceivedGiftsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun gift(id: String, title: String, year: Int, thanked: Boolean = false) =
        ReceivedGift(id, title, null, null, listOf("Camille"), joint = false, GiftOccasion.Birthday, year, thanked)

    private class Gifts(received: List<ReceivedGift>, var fail: Boolean = false) : GiftRepository by FakeGifts(null) {
        val received = MutableStateFlow(received)
        val asked = mutableListOf<String>()
        val thanks = mutableListOf<Pair<String, String>>()

        override fun observeReceived(listId: String): Flow<List<ReceivedGift>> {
            asked += listId
            return received
        }

        override suspend fun thank(listId: String, itemId: String) {
            if (fail) error("refused")
            thanks += listId to itemId
        }
    }

    private suspend fun ReceivedGiftsViewModel.settled() = uiState.first { it is ReceivedGiftsUiState.Success } as ReceivedGiftsUiState.Success

    @Test
    fun `the gifts of my own wishes, the newest occasion first`() = runTest {
        val gifts = Gifts(listOf(gift("a", "Un livre", 2026), gift("b", "Une lampe", 2027), gift("c", "Un bol", 2027)))
        val vm = ReceivedGiftsViewModel(gifts, "ines")
        assertEquals(listOf("c", "b", "a"), vm.settled().gifts.map { it.id })
        assertEquals(listOf("wishes-ines"), gifts.asked)
    }

    @Test
    fun `merci turns at once and is written`() = runTest {
        val gifts = Gifts(listOf(gift("a", "Un livre", 2026)))
        val vm = ReceivedGiftsViewModel(gifts, "ines")
        vm.settled()
        vm.thank("a")
        val state = vm.uiState.first { it is ReceivedGiftsUiState.Success && it.gifts.single().thanked } as ReceivedGiftsUiState.Success
        assertFalse(state.thankFailed)
        assertEquals(listOf("wishes-ines" to "a"), gifts.thanks)
    }

    @Test
    fun `a refused merci comes back, with a message`() = runTest {
        val gifts = Gifts(listOf(gift("a", "Un livre", 2026)), fail = true)
        val vm = ReceivedGiftsViewModel(gifts, "ines")
        vm.settled()
        vm.thank("a")
        val state = vm.uiState.first { it is ReceivedGiftsUiState.Success && it.thankFailed } as ReceivedGiftsUiState.Success
        assertFalse(state.gifts.single().thanked)
    }

    @Test
    fun `nothing given yet shows empty`() = runTest {
        assertTrue(ReceivedGiftsViewModel(Gifts(emptyList()), "ines").settled().gifts.isEmpty())
        assertTrue(ReceivedGiftsViewModel(null, "ines").settled().gifts.isEmpty())
    }
}
