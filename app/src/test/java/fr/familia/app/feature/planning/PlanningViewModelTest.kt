package fr.familia.app.feature.planning

import fr.familia.app.core.model.EventTarget
import fr.familia.app.feature.events.FakeEvents
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val vm = PlanningViewModel(events, FakeMembers(), "adult1", today = { today })
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
        val vm = PlanningViewModel(events, FakeMembers(), "adult1", today = { today })
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
        val vm = PlanningViewModel(events, FakeMembers(), "adult1", today = { today })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue((vm.uiState.value as PlanningUiState.Success).days.isEmpty())
        events.events.value = listOf(TestFamily.event("e1", TestFamily.otherAdult))
        assertEquals(1, (vm.uiState.value as PlanningUiState.Success).days.size)
    }
}
