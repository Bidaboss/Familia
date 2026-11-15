package fr.familia.app.feature.trips

import fr.familia.app.feature.events.FakeEvents
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.planning.PlanningUiState
import fr.familia.app.feature.planning.PlanningViewModel
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/** Trips in the planning (PRD, récit 11): on their first day, or today while ongoing. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanningTripsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `an upcoming trip shows on its first day, and the month view marks all its days`() = runTest {
        val today = LocalDate.of(2027, 7, 1)
        val trips = FakeTrips(listOf(TestTrips.trip(start = LocalDate.of(2027, 7, 10), end = LocalDate.of(2027, 7, 12))))
        val vm = PlanningViewModel(FakeEvents(), FakeMembers(), "kid1", today = flowOf(today), trips = trips)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value as PlanningUiState.Success
        val day = state.days.single()
        assertEquals(LocalDate.of(2027, 7, 10), day.date)
        assertEquals(listOf("Adult1", "Adult2"), day.trips.single().participants)
        assertEquals(false, day.trips.single().ongoing)
        assertEquals((10..12).map { LocalDate.of(2027, 7, it) }.toSet(), state.tripDays)
    }

    @Test
    fun `an ongoing trip shows today, a past one or one not followed does not`() = runTest {
        val today = LocalDate.of(2027, 7, 15)
        val trips = FakeTrips(
            listOf(
                TestTrips.trip("now"),
                TestTrips.trip("past", start = LocalDate.of(2027, 7, 1), end = LocalDate.of(2027, 7, 2)),
                TestTrips.trip("hidden", readers = listOf("adult1", "adult2")),
            ),
        )
        val vm = PlanningViewModel(FakeEvents(), FakeMembers(), "kid1", today = flowOf(today), trips = trips)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value as PlanningUiState.Success
        val day = state.days.single()
        assertEquals(today, day.date)
        assertEquals(listOf("now"), day.trips.map { it.trip.id })
        assertTrue(day.trips.single().ongoing)
        assertEquals((15..20).map { LocalDate.of(2027, 7, it) }.toSet(), state.tripDays)
    }

    @Test
    fun `the planning still shows when the trips cannot load`() = runTest {
        val today = LocalDate.of(2027, 7, 1)
        val vm = PlanningViewModel(FakeEvents(), FakeMembers(), "kid1", today = flowOf(today), trips = FakeTrips(failWith = IOException("offline")))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value is PlanningUiState.Success)
    }
}
