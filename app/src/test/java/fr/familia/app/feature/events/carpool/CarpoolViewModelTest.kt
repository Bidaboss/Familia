package fr.familia.app.feature.events.carpool

import fr.familia.app.core.data.CarpoolRepository
import fr.familia.app.core.model.Ride
import fr.familia.app.core.model.RideNeed
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

private class FakeCarpool(rides: List<Ride> = emptyList(), needs: List<RideNeed> = emptyList()) : CarpoolRepository {
    val rides = MutableStateFlow(rides)
    val needs = MutableStateFlow(needs)
    override fun observeRides(eventId: String): Flow<List<Ride>> = rides
    override fun observeNeeds(eventId: String): Flow<List<RideNeed>> = needs
    override suspend fun offer(eventId: String, driverId: String, seats: Int, from: String?, leaveAt: LocalTime?, isNew: Boolean) {
        val old = rides.value.firstOrNull { it.driverId == driverId }
        rides.value = rides.value.filter { it.driverId != driverId } + Ride(driverId, seats, from, leaveAt, old?.passengers.orEmpty())
    }
    override suspend fun withdraw(eventId: String, driverId: String) {
        rides.value = rides.value.filter { it.driverId != driverId }
    }
    override suspend fun getIn(eventId: String, driverId: String, memberId: String, leaving: String?) {
        if (leaving != null) getOut(eventId, leaving, memberId)
        rides.value = rides.value.map { if (it.driverId == driverId) it.copy(passengers = it.passengers + memberId) else it }
    }
    override suspend fun getOut(eventId: String, driverId: String, memberId: String) {
        rides.value = rides.value.map { if (it.driverId == driverId) it.copy(passengers = it.passengers - memberId) else it }
    }
    override suspend fun askSeat(eventId: String, memberId: String, askedBy: String) {
        needs.value = needs.value + RideNeed(memberId, askedBy)
    }
    override suspend fun cancelAsk(eventId: String, memberId: String) {
        needs.value = needs.value.filter { it.memberId != memberId }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CarpoolViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(repo: FakeCarpool, me: String = "adult1", riders: List<String> = listOf("adult1", "kid1"), coming: Boolean = true) =
        CarpoolViewModel(repo, FakeMembers(), me, "e1", riders, coming)

    @Test
    fun `an adult who comes offers seats, then changes them, never below the passengers`() = runTest {
        val repo = FakeCarpool()
        val viewModel = vm(repo, me = "adult2", riders = listOf("adult2"))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertTrue(viewModel.uiState.value.canOffer)
        assertFalse(viewModel.offer(0, "", null))
        assertFalse(viewModel.offer(3, "x".repeat(41), null))
        assertTrue(viewModel.offer(3, "  Annecy ", LocalTime.of(10, 30)))
        assertEquals(Ride("adult2", 3, "Annecy", LocalTime.of(10, 30), emptyList()), repo.rides.value.single())
        assertFalse(viewModel.uiState.value.canOffer)
        assertTrue(viewModel.uiState.value.cars.single().isMine)
        repo.rides.value = listOf(repo.rides.value.single().copy(passengers = listOf("adult1", "kid1")))
        assertFalse(viewModel.offer(1, "", null))
        assertTrue(viewModel.offer(2, "", null))
    }

    @Test
    fun `no offer when not coming, nor for a child`() = runTest {
        val notComing = vm(FakeCarpool(), coming = false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { notComing.uiState.collect {} }
        assertFalse(notComing.uiState.value.canOffer)
        val child = vm(FakeCarpool(), me = "kid1", riders = listOf("kid1"))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { child.uiState.collect {} }
        assertFalse(child.uiState.value.canOffer)
    }

    @Test
    fun `I get my child and me in, never beyond the seats, and leave the other car`() = runTest {
        val repo = FakeCarpool(listOf(Ride("adult2", 2, null, null, emptyList()), Ride("admin1", 3, null, null, listOf("kid1"))))
        val viewModel = vm(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.choose("adult2", setOf("adult1", "kid1"))
        assertEquals(listOf("adult1", "kid1"), repo.rides.value.first { it.driverId == "adult2" }.passengers)
        assertEquals(emptyList<String>(), repo.rides.value.first { it.driverId == "admin1" }.passengers)
        repo.rides.value = listOf(Ride("adult2", 1, null, null, emptyList()))
        viewModel.choose("adult2", setOf("adult1", "kid1"))
        assertEquals(emptyList<String>(), repo.rides.value.single().passengers)
    }

    @Test
    fun `a seat asked for is no longer asked once in a car`() = runTest {
        val repo = FakeCarpool(listOf(Ride("adult2", 2, null, null, emptyList())))
        val viewModel = vm(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.needs(setOf("kid1"))
        assertEquals(listOf("Kid1"), viewModel.uiState.value.needs.map { it.firstName })
        viewModel.choose("adult2", setOf("kid1"))
        assertEquals(emptyList<String>(), viewModel.uiState.value.needs.map { it.firstName })
        viewModel.needs(emptySet())
    }

    @Test
    fun `the driver lets a passenger out, a car of someone gone is not shown`() = runTest {
        val repo = FakeCarpool(listOf(Ride("adult1", 3, null, null, listOf("kid2")), Ride("ghost", 2, null, null, emptyList())))
        val viewModel = vm(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertEquals(listOf("adult1"), viewModel.uiState.value.cars.map { it.driver.id })
        viewModel.letOut("kid2")
        assertEquals(emptyList<String>(), repo.rides.value.first { it.driverId == "adult1" }.passengers)
    }
}
