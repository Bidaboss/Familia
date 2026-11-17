package fr.familia.app.feature.events.detail

import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.feature.events.FakeEvents
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.FakeRsvps
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 12, 1)

    private fun TestScope.viewModel(
        memberId: String,
        events: FakeEvents = FakeEvents(listOf(TestFamily.event("e1", TestFamily.otherAdult))),
        rsvps: FakeRsvps = FakeRsvps(),
    ): EventDetailViewModel {
        val vm = EventDetailViewModel(events, FakeMembers(), rsvps, memberId, "e1", today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun EventDetailViewModel.success() = uiState.value as EventDetailUiState.Success

    @Test
    fun `everyone opens the event, only those who may manage it get Modifier`() = runTest {
        assertFalse(viewModel("adult1").success().canManage)
        assertTrue(viewModel("adult2").success().canManage)
        assertTrue(viewModel("admin1").success().canManage)
    }

    @Test
    fun `an adult answers for themselves and their child`() = runTest {
        val rsvps = FakeRsvps()
        val vm = viewModel("adult1", rsvps = rsvps)
        assertEquals(listOf("adult1", "kid1"), vm.success().answerers.map { it.member.id })
        assertTrue(vm.success().answerers.first().isMe)
        vm.answer("adult1", RsvpStatus.Yes)
        vm.answer("kid1", RsvpStatus.Maybe)
        assertEquals(listOf(RsvpStatus.Yes, RsvpStatus.Maybe), vm.success().answerers.map { it.status })
        assertEquals(listOf(Rsvp("adult1", RsvpStatus.Yes, "adult1"), Rsvp("kid1", RsvpStatus.Maybe, "adult1")), rsvps.answers.value["e1"])
    }

    @Test
    fun `an answer waiting for the network says so, and no longer once it left`() = runTest {
        val rsvps = FakeRsvps()
        rsvps.answers.value = mapOf("e1" to listOf(Rsvp("adult1", RsvpStatus.Yes, "adult1", pending = true)))
        val vm = viewModel("adult1", rsvps = rsvps)
        assertEquals(listOf(true, false), vm.success().answerers.map { it.pending })
        assertEquals(RsvpStatus.Yes, vm.success().answerers.first().status)
        rsvps.answers.value = mapOf("e1" to listOf(Rsvp("adult1", RsvpStatus.Yes, "adult1")))
        assertFalse(vm.success().answerers.first().pending)
    }

    @Test
    fun `changing one's mind replaces the answer`() = runTest {
        val vm = viewModel("adult1")
        vm.answer("adult1", RsvpStatus.Yes)
        vm.answer("adult1", RsvpStatus.No)
        assertEquals(RsvpStatus.No, vm.success().answerers.first().status)
        assertEquals(listOf("Adult1"), vm.success().notComing)
        assertTrue(vm.success().coming.isEmpty())
    }

    @Test
    fun `the summary lists the whole family by answer, and ignores removed members`() = runTest {
        val rsvps = FakeRsvps()
        rsvps.answers.value = mapOf(
            "e1" to listOf(
                Rsvp("kid2", RsvpStatus.Yes, "adult2"),
                Rsvp("adult2", RsvpStatus.Yes, "adult2"),
                Rsvp("admin1", RsvpStatus.Maybe, "admin1"),
                Rsvp("gone", RsvpStatus.Yes, "gone"),
            ),
        )
        val state = viewModel("adult1", rsvps = rsvps).success()
        assertEquals(listOf("Adult2", "Kid2"), state.coming)
        assertEquals(listOf("Admin1"), state.maybe)
        assertNull(state.answerers.first().status)
    }

    @Test
    fun `offline, the answer shows at once and is not reported as failed`() = runTest {
        val vm = viewModel("adult1", rsvps = FakeRsvps(hang = true))
        vm.answer("adult1", RsvpStatus.Yes)
        assertEquals(RsvpStatus.Yes, vm.success().answerers.first().status)
        advanceTimeBy(5_000)
        assertFalse(vm.success().sendFailed)
    }

    @Test
    fun `a refused answer is reported`() = runTest {
        val vm = viewModel("adult1", rsvps = FakeRsvps(failWith = IllegalStateException("denied")))
        vm.answer("adult1", RsvpStatus.Yes)
        assertTrue(vm.success().sendFailed)
    }

    @Test
    fun `a deleted event shows as missing`() = runTest {
        val events = FakeEvents(listOf(TestFamily.event("e1", TestFamily.otherAdult)))
        val vm = viewModel("adult1", events = events)
        events.events.value = emptyList()
        assertEquals(EventDetailUiState.Missing, vm.uiState.value)
    }
}
