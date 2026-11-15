package fr.familia.app.feature.trips

import fr.familia.app.core.model.TripArrival
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.trips.detail.TripDetailUiState
import fr.familia.app.feature.trips.detail.TripDetailViewModel
import fr.familia.app.feature.trips.edit.TripEditUiState
import fr.familia.app.feature.trips.edit.TripEditViewModel
import fr.familia.app.feature.trips.edit.TripFormError
import fr.familia.app.feature.trips.list.TripsUiState
import fr.familia.app.feature.trips.list.TripsViewModel
import fr.familia.app.feature.trips.step.StepEditUiState
import fr.familia.app.feature.trips.step.StepEditViewModel
import fr.familia.app.feature.trips.step.StepFormError
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val july = LocalDate.of(2027, 7, 15)

    // --- List -------------------------------------------------------------------------------------

    @Test
    fun `the list shows only followed trips, ongoing, upcoming then past`() = runTest {
        val trips = FakeTrips(
            listOf(
                TestTrips.trip("now"),
                TestTrips.trip("later", start = LocalDate.of(2027, 8, 1), end = LocalDate.of(2027, 8, 5)),
                TestTrips.trip("before", start = LocalDate.of(2027, 2, 1), end = LocalDate.of(2027, 2, 5)),
                TestTrips.trip("hidden", readers = listOf("adult1", "adult2")),
            ),
        )
        val vm = TripsViewModel(trips, FakeMembers(), "kid1", today = flowOf(july))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value as TripsUiState.Success
        assertEquals(listOf("now"), state.ongoing.map { it.trip.id })
        assertEquals(listOf("later"), state.upcoming.map { it.trip.id })
        assertEquals(listOf("before"), state.past.map { it.trip.id })
        assertEquals(listOf("Adult1", "Adult2"), state.ongoing.single().participants)
    }

    @Test
    fun `the list says when trips cannot load`() = runTest {
        val vm = TripsViewModel(FakeTrips(failWith = IOException("offline")), FakeMembers(), "adult1", today = flowOf(july))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(TripsUiState.Error, vm.uiState.value)
    }

    // --- Detail -----------------------------------------------------------------------------------

    @Test
    fun `a participant says bien arrive for everyone, a follower only reads`() = runTest {
        val trips = FakeTrips(steps = mapOf("t1" to listOf(TestTrips.step())))
        val participant = TripDetailViewModel(trips, FakeMembers(), "adult2", "t1", today = flowOf(july))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { participant.uiState.collect {} }
        val state = participant.uiState.value as TripDetailUiState.Success
        assertTrue(state.isParticipant)
        assertFalse(state.canManage)
        assertEquals(5, state.followers)
        participant.markArrived("s1")
        assertEquals(listOf("s1" to "adult2"), trips.arrivals)
        participant.clearArrival("s1")
        assertEquals(listOf("s1"), trips.cleared)

        val follower = TripDetailViewModel(trips, FakeMembers(), "kid1", "t1", today = flowOf(july))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { follower.uiState.collect {} }
        assertFalse((follower.uiState.value as TripDetailUiState.Success).isParticipant)
    }

    @Test
    fun `a refused bien arrive is said on screen`() = runTest {
        val trips = FakeTrips(steps = mapOf("t1" to listOf(TestTrips.step())), failWith = IOException("refused"))
        val vm = TripDetailViewModel(trips, FakeMembers(), "adult1", "t1", today = flowOf(july))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.markArrived("s1")
        assertTrue((vm.uiState.value as TripDetailUiState.Success).sendFailed)
    }

    @Test
    fun `a deleted trip shows as gone`() = runTest {
        val trips = FakeTrips()
        val vm = TripDetailViewModel(trips, FakeMembers(), "adult1", "t1", today = flowOf(july))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        trips.trips.value = emptyList()
        assertEquals(TripDetailUiState.Missing, vm.uiState.value)
    }

    // --- Trip form --------------------------------------------------------------------------------

    private fun TripEditViewModel.editing() = uiState.value as TripEditUiState.Editing

    @Test
    fun `a new trip has its creator as participant and the whole family as followers`() {
        val vm = TripEditViewModel(FakeTrips(), FakeMembers(), "adult1", null)
        val state = vm.editing()
        assertEquals(setOf("adult1"), state.form.participantIds)
        assertEquals(TestTrips.everyone.toSet(), state.form.readerIds)
        assertTrue(state.participants.first { it.id == "adult1" }.locked)
    }

    @Test
    fun `a name and dates are required, and the return comes after the departure`() {
        val vm = TripEditViewModel(FakeTrips(), FakeMembers(), "adult1", null)
        vm.save()
        assertEquals(setOf(TripFormError.TitleMissing, TripFormError.StartMissing, TripFormError.EndMissing), vm.editing().errors)
        vm.update { it.copy(title = "Corse", startDate = LocalDate.of(2027, 7, 10), endDate = LocalDate.of(2027, 7, 1)) }
        assertEquals(setOf(TripFormError.EndBeforeStart), vm.editing().errors)
    }

    @Test
    fun `saving keeps every participant among the followers, even one unticked`() {
        val trips = FakeTrips()
        val vm = TripEditViewModel(trips, FakeMembers(), "adult1", null)
        vm.update {
            it.copy(title = " Corse ", startDate = LocalDate.of(2027, 7, 10), endDate = LocalDate.of(2027, 7, 20))
                .withParticipant("adult2", true)
                .withReader("kid2", false)
                .withReader("adult2", false) // refused: adult2 takes part
        }
        vm.save()
        assertEquals(TripEditUiState.Done("t-new", deleted = false), vm.uiState.value)
        val (trip, isNew) = trips.savedTrips.single()
        assertTrue(isNew)
        assertEquals("Corse", trip.title)
        assertEquals(listOf("adult1", "adult2"), trip.participantIds)
        assertEquals(setOf("admin1", "adult1", "adult2", "kid1"), trip.readerIds.toSet())
        assertEquals("adult1", trip.creatorId)
        assertEquals("Adult1", trip.creatorName)
    }

    @Test
    fun `only the creator or the admin principal opens the trip form`() {
        assertEquals(TripEditUiState.Unavailable, TripEditViewModel(FakeTrips(), FakeMembers(), "adult2", "t1").uiState.value)
        val admin = TripEditViewModel(FakeTrips(), FakeMembers(), "admin1", "t1")
        assertFalse(admin.editing().isNew)
        admin.delete()
        assertEquals(TripEditUiState.Done(null, deleted = true), admin.uiState.value)
    }

    // --- Step form --------------------------------------------------------------------------------

    private fun StepEditViewModel.editing() = uiState.value as StepEditUiState.Editing

    private val paris = { ZoneId.of("Europe/Paris") }
    private val beforeTrip = { Instant.parse("2027-07-01T00:00:00Z") }

    @Test
    fun `a new step starts on the first day of the trip, and needs a place`() {
        val vm = StepEditViewModel(FakeTrips(), FakeMembers(), "adult2", "t1", null, zone = paris, now = beforeTrip)
        assertEquals(LocalDate.of(2027, 7, 10), vm.editing().form.departDate)
        assertFalse(vm.editing().canDelete)
        vm.save()
        assertEquals(setOf(StepFormError.PlaceMissing), vm.editing().errors)
    }

    @Test
    fun `saving a step sets the reminder 3 hours after the planned arrival`() {
        val trips = FakeTrips()
        val vm = StepEditViewModel(trips, FakeMembers(), "adult2", "t1", null, zone = paris, now = beforeTrip)
        vm.update { it.copy(place = " Ajaccio ", departTime = LocalTime.of(8, 15), arriveTime = LocalTime.of(9, 40)) }
        vm.save()
        assertEquals(StepEditUiState.Done, vm.uiState.value)
        val (step, isNew, remindAt) = trips.savedSteps.single()
        assertTrue(isNew)
        assertEquals("Ajaccio", step.place)
        assertEquals(LocalDate.of(2027, 7, 10), step.arriveDate)
        assertEquals(Instant.parse("2027-07-10T10:40:00Z"), remindAt)
    }

    @Test
    fun `an arrival before the departure is refused`() {
        val vm = StepEditViewModel(FakeTrips(), FakeMembers(), "adult2", "t1", null, zone = paris, now = beforeTrip)
        vm.update { it.copy(place = "Ajaccio", departTime = LocalTime.of(9, 0), arriveTime = LocalTime.of(8, 0)) }
        vm.save()
        assertEquals(setOf(StepFormError.ArriveBeforeDepart), vm.editing().errors)
    }

    @Test
    fun `editing a step keeps its arrival and sets no reminder`() {
        val arrived = TestTrips.step(arrival = TripArrival(Instant.EPOCH, "adult1", "Adult1"))
        val trips = FakeTrips(steps = mapOf("t1" to listOf(arrived)))
        val vm = StepEditViewModel(trips, FakeMembers(), "adult1", "t1", "s1", zone = paris, now = beforeTrip)
        assertTrue(vm.editing().canDelete)
        vm.update { it.copy(note = "Retard") }
        vm.save()
        val (step, isNew, remindAt) = trips.savedSteps.single()
        assertFalse(isNew)
        assertEquals(arrived.arrival, step.arrival)
        assertNull(remindAt)
    }

    @Test
    fun `a follower who does not take part cannot open the step form`() {
        val vm = StepEditViewModel(FakeTrips(), FakeMembers(), "kid1", "t1", null, zone = paris, now = beforeTrip)
        assertEquals(StepEditUiState.Unavailable, vm.uiState.value)
    }
}
