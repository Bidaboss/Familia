package fr.familia.app.feature.gifts

import fr.familia.app.core.model.ClaimType
import fr.familia.app.core.model.GiftClaim
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.gifts.detail.ClaimLost
import fr.familia.app.feature.gifts.detail.GiftListUiState
import fr.familia.app.feature.gifts.detail.GiftListViewModel
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Passe « en même temps »: a reservation the server refuses says why, gently, even late. */
class GiftsAtTheSameTimeTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 15)
    private val list = GiftList(
        "l", GiftListKind.Own, "Liste l", GiftOccasion.Christmas, LocalDate.of(2026, 12, 25),
        "adult1", listOf("adult1", "adult2"), "adult1", "adult1",
    )
    private val otherClaim = GiftClaim("i1", ClaimType.Solo, "admin1", "Admin1")
    private val refused = IllegalStateException("PERMISSION_DENIED")

    private suspend fun TestScope.opened(gifts: FakeGifts, timeout: Long = 4_000): GiftListViewModel {
        val vm = GiftListViewModel(gifts, FakeMembers(), "adult2", "l", writeTimeoutMillis = timeout, today = flowOf(today))
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { it is GiftListUiState.Success && it.items.isNotEmpty() }
        return vm
    }

    private fun GiftListViewModel.shown() = uiState.value as GiftListUiState.Success

    @Test
    fun `someone reserved it a second before, the message says so, gently`() = runTest {
        val gifts = FakeGifts(list)
        gifts.onClaim = {
            gifts.claims.value = listOf(otherClaim)
            throw refused
        }
        val vm = opened(gifts)
        vm.take("i1")
        runCurrent()
        assertEquals(ClaimLost("i1", ClaimLost.Reason.Taken), vm.shown().claimLost)
        assertFalse(vm.shown().actionFailed)
        assertEquals("Admin1", vm.shown().items.single().claim!!.holderName)
    }

    @Test
    fun `the idea is already bought, the refusal says so instead of failing`() = runTest {
        val gifts = FakeGifts(list)
        gifts.items.value = gifts.items.value.map { it.copy(alreadyBought = true) }
        gifts.onClaim = { throw refused }
        val vm = opened(gifts)
        vm.take("i1")
        runCurrent()
        assertEquals(ClaimLost("i1", ClaimLost.Reason.AlreadyBought), vm.shown().claimLost)
        assertFalse(vm.shown().actionFailed)
    }

    @Test
    fun `the idea was removed while I reserved it`() = runTest {
        val gifts = FakeGifts(list)
        gifts.onClaim = {
            gifts.items.value = emptyList()
            throw refused
        }
        val vm = opened(gifts)
        vm.openJointGift("i1")
        runCurrent()
        assertEquals(ClaimLost("i1", ClaimLost.Reason.Removed), vm.shown().claimLost)
        assertFalse(vm.shown().actionFailed)
    }

    @Test
    fun `reserved offline, then refused when the network comes back, said late, nothing stays waiting`() = runTest {
        val gifts = FakeGifts(list)
        gifts.onClaim = {
            delay(60_000)
            gifts.claims.value = listOf(otherClaim)
            throw refused
        }
        val vm = opened(gifts, timeout = 1_000)
        vm.take("i1")
        advanceTimeBy(2_000)
        runCurrent()
        // Queued offline: the screen goes on without a message.
        assertNull(vm.shown().claimLost)
        assertFalse(vm.shown().actionFailed)
        advanceUntilIdle()
        assertEquals(ClaimLost("i1", ClaimLost.Reason.Taken), vm.shown().claimLost)
        assertFalse(vm.shown().items.single().claim!!.isMine)
    }

    @Test
    fun `a refusal with the idea still free stays the usual message`() = runTest {
        val gifts = FakeGifts(list)
        gifts.onClaim = { throw refused }
        val vm = opened(gifts)
        vm.take("i1")
        runCurrent()
        assertNull(vm.shown().claimLost)
        assertTrue(vm.shown().actionFailed)
    }

    @Test
    fun `the next reservation that holds clears the message`() = runTest {
        val gifts = FakeGifts(list)
        gifts.onClaim = { throw refused }
        val vm = opened(gifts)
        vm.take("i1")
        runCurrent()
        assertTrue(vm.shown().actionFailed)
        gifts.onClaim = {}
        vm.take("i1")
        runCurrent()
        assertFalse(vm.shown().actionFailed)
        assertNull(vm.shown().claimLost)
    }

    @Test
    fun `a late refusal of an older reservation never covers the one that held after it`() = runTest {
        val gifts = FakeGifts(list)
        gifts.onClaim = {
            delay(60_000)
            throw refused
        }
        val vm = opened(gifts, timeout = 1_000)
        vm.take("i1")
        advanceTimeBy(2_000)
        gifts.onClaim = {}
        vm.openJointGift("i1")
        runCurrent()
        advanceUntilIdle()
        assertFalse(vm.shown().actionFailed)
        assertNull(vm.shown().claimLost)
    }
}
