package fr.familia.app.feature.planning

import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.feature.events.FakeEvents
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.FakeRsvps
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class PlanningViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 12, 1)

    @Test
    fun `upcoming events are grouped by day, all-day ones first, past ones hidden`() = runTest {
        val day = LocalDate.of(2026, 12, 24)
        val events = FakeEvents(
            listOf(
                TestFamily.event("late", TestFamily.adult, date = day).copy(time = LocalTime.of(20, 0)),
                TestFamily.event("allDay", TestFamily.adult, date = day),
                TestFamily.event("past", TestFamily.adult, date = LocalDate.of(2026, 11, 1)),
                TestFamily.event("other", TestFamily.adult, date = LocalDate.of(2026, 12, 25)),
            ),
        )
        val vm = PlanningViewModel(events, FakeMembers(), "adult1", today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val days = (vm.uiState.value as PlanningUiState.Success).days
        assertEquals(listOf(day, LocalDate.of(2026, 12, 25)), days.map { it.date })
        assertEquals(listOf("allDay", "late"), days.first().items.map { it.event.id })
    }

    @Test
    fun `each row knows whether this member may open it, and names the target and author`() = runTest {
        val events = FakeEvents(
            listOf(
                TestFamily.event("mine", TestFamily.adult, EventTarget.ForSelf("adult1")),
                TestFamily.event("theirs", TestFamily.otherAdult, EventTarget.ForHousehold("h2")),
            ),
        )
        val vm = PlanningViewModel(events, FakeMembers(), "adult1", today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val items = (vm.uiState.value as PlanningUiState.Success).days.single().items.associateBy { it.event.id }
        assertTrue(items.getValue("mine").canManage)
        assertEquals(null, items.getValue("mine").target.name) // "Moi"
        assertFalse(items.getValue("theirs").canManage)
        assertEquals("Foyer Deux", items.getValue("theirs").target.name)
        assertEquals("Adult2", items.getValue("theirs").authorName)
    }

    @Test
    fun `a new event appears live`() = runTest {
        val events = FakeEvents()
        val vm = PlanningViewModel(events, FakeMembers(), "adult1", today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue((vm.uiState.value as PlanningUiState.Success).days.isEmpty())
        events.events.value = listOf(TestFamily.event("e1", TestFamily.otherAdult))
        assertEquals(1, (vm.uiState.value as PlanningUiState.Success).days.size)
    }

    @Test
    fun `birthdays appear on their day, before the events, with nothing stored`() = runTest {
        val family = TestFamily.family.copy(
            members = TestFamily.family.members.map { if (it.id == "kid2") it.copy(birthDate = LocalDate.of(2016, 12, 24)) else it },
        )
        val events = FakeEvents(listOf(TestFamily.event("e1", TestFamily.adult, date = LocalDate.of(2026, 12, 24))))
        val vm = PlanningViewModel(events, FakeMembers(family), "adult1", today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value as PlanningUiState.Success
        assertEquals(today, state.today)
        val day = state.days.single()
        assertEquals("kid2", day.birthdays.single().member.id)
        assertEquals(10, day.birthdays.single().age)
        assertEquals(listOf("e1"), day.items.map { it.event.id })
    }

    @Test
    fun `a day with only a birthday still gets its own header`() = runTest {
        val family = TestFamily.family.copy(
            members = TestFamily.family.members.map { if (it.id == "adult2") it.copy(birthDate = LocalDate.of(1980, 1, 5)) else it },
        )
        val vm = PlanningViewModel(FakeEvents(), FakeMembers(family), "adult1", today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val day = (vm.uiState.value as PlanningUiState.Success).days.single()
        assertEquals(LocalDate.of(2027, 1, 5), day.date)
        assertEquals(emptyList<Any>(), day.items)
    }

    @Test
    fun `at midnight the list moves on by itself`() = runTest {
        val day = MutableStateFlow(today)
        val events = FakeEvents(listOf(TestFamily.event("tonight", TestFamily.adult, date = today)))
        val vm = PlanningViewModel(events, FakeMembers(), "adult1", today = day)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(listOf("tonight"), (vm.uiState.value as PlanningUiState.Success).days.single().items.map { it.event.id })
        day.value = today.plusDays(1)
        val state = vm.uiState.value as PlanningUiState.Success
        assertEquals(today.plusDays(1), state.today)
        assertTrue(state.days.isEmpty())
    }

    @Test
    fun `each row counts the family members who come or may come`() = runTest {
        val events = FakeEvents(listOf(TestFamily.event("meal", TestFamily.adult)))
        val rsvps = FakeRsvps()
        rsvps.answers.value = mapOf(
            "meal" to listOf(
                Rsvp("adult1", RsvpStatus.Yes, "adult1"),
                Rsvp("kid1", RsvpStatus.Yes, "adult1"),
                Rsvp("adult2", RsvpStatus.Maybe, "adult2"),
                Rsvp("admin1", RsvpStatus.No, "admin1"),
                Rsvp("gone", RsvpStatus.Yes, "gone"), // a member who has left the family
            ),
        )
        val vm = PlanningViewModel(events, FakeMembers(), "adult1", today = flowOf(today), rsvps = rsvps)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val item = (vm.uiState.value as PlanningUiState.Success).days.single().items.single()
        assertEquals(2, item.coming)
        assertEquals(1, item.maybe)

        rsvps.setRsvp("meal", "adult2", RsvpStatus.Yes, "adult2")
        val updated = (vm.uiState.value as PlanningUiState.Success).days.single().items.single()
        assertEquals(3, updated.coming)
        assertEquals(0, updated.maybe)
    }

    @Test
    fun `the planning still shows when the counters cannot load`() = runTest {
        val events = FakeEvents(listOf(TestFamily.event("meal", TestFamily.adult)))
        val rsvps = FakeRsvps(allFailWith = IllegalStateException("offline"))
        val vm = PlanningViewModel(events, FakeMembers(), "adult1", today = flowOf(today), rsvps = rsvps)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val item = (vm.uiState.value as PlanningUiState.Success).days.single().items.single()
        assertEquals(0, item.coming)
    }

    @Test
    fun `mon foyer keeps what concerns me and every birthday, and the choice is saved`() = runTest {
        val family = TestFamily.family.copy(
            members = TestFamily.family.members.map { if (it.id == "kid2") it.copy(birthDate = LocalDate.of(2016, 12, 24)) else it },
        )
        val events = FakeEvents(
            listOf(
                TestFamily.event("family", TestFamily.otherAdult),
                TestFamily.event("mine", TestFamily.adult, EventTarget.ForHousehold("h1")),
                TestFamily.event("kid", TestFamily.adult, EventTarget.ForChild("kid1")),
                TestFamily.event("theirs", TestFamily.otherAdult, EventTarget.ForHousehold("h2")),
                TestFamily.event("theirKid", TestFamily.otherAdult, EventTarget.ForChild("kid2")),
            ),
        )
        val saved = mutableListOf<PlanningScope>()
        val vm = PlanningViewModel(events, FakeMembers(family), "adult1", today = flowOf(today), saveScope = { saved += it })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(5, (vm.uiState.value as PlanningUiState.Success).days.single().items.size)

        vm.setScope(PlanningScope.Household)
        val state = vm.uiState.value as PlanningUiState.Success
        assertEquals(PlanningScope.Household, state.scope)
        assertEquals(setOf("family", "mine", "kid"), state.days.single().items.map { it.event.id }.toSet())
        assertEquals("kid2", state.days.single().birthdays.single().member.id)
        assertEquals(listOf(PlanningScope.Household), saved)
    }

    @Test
    fun `the last choice comes back`() = runTest {
        val events = FakeEvents(listOf(TestFamily.event("theirs", TestFamily.otherAdult, EventTarget.ForHousehold("h2"))))
        val vm = PlanningViewModel(events, FakeMembers(), "adult1", today = flowOf(today), initialScope = PlanningScope.Household)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value as PlanningUiState.Success
        assertEquals(PlanningScope.Household, state.scope)
        assertTrue(state.days.isEmpty())
    }
}
