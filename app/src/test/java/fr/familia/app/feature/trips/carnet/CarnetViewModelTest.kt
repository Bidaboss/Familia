package fr.familia.app.feature.trips.carnet

import fr.familia.app.core.data.TravelPlaceRepository
import fr.familia.app.core.model.TravelPlace
import fr.familia.app.core.place.Place
import fr.familia.app.core.place.PlaceLocator
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.trips.FakeTrips
import fr.familia.app.feature.trips.TestTrips
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** « Carnet de baroudeur » (récit 107): fed by the finished trips the member took part in, and the hand-added ones. */
class CarnetViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 24)

    /** Every point in Portugal west of -8.8, Morocco south of 35, France elsewhere. */
    private class FakeLocator : PlaceLocator {
        override suspend fun currentPosition(): Pair<Double, Double>? = null
        override suspend fun addressOf(lat: Double, lng: Double): String? = null
        override suspend fun countryCodeOf(lat: Double, lng: Double): String? = when {
            lat < 35 -> "ma"
            lng < -8.8 -> "pt"
            else -> "fr"
        }
        override suspend fun find(query: String): Place? = null
        override suspend fun expand(shortLink: String): String? = null
    }

    private class FakePlaces : TravelPlaceRepository {
        val places = MutableStateFlow<Map<String, List<TravelPlace>>>(emptyMap())
        override fun observePlaces(memberId: String): Flow<List<TravelPlace>> = places.map { it[memberId].orEmpty() }
        override suspend fun addPlace(memberId: String, place: TravelPlace) {
            places.value = places.value + (memberId to (places.value[memberId].orEmpty() + place.copy(id = "p${places.value.size}")))
        }
        override suspend fun deletePlace(memberId: String, placeId: String) {
            places.value = places.value.mapValues { (_, list) -> list.filterNot { it.id == placeId } }
        }
    }

    private fun past(id: String, participants: List<String>) =
        TestTrips.trip(id = id, participants = participants, start = LocalDate.of(2025, 5, 1), end = LocalDate.of(2025, 5, 5))

    @Test
    fun `only the finished trips the member took part in count`() = runTest {
        val trips = FakeTrips(
            initial = listOf(
                past("t1", listOf("adult1")),
                past("t2", listOf("adult2")),
                TestTrips.trip(id = "t3", participants = listOf("adult1")),
            ),
            steps = mapOf(
                "t1" to listOf(TestTrips.step("s1", LocalDate.of(2025, 5, 1), lat = 38.7, lng = -9.1)),
                "t2" to listOf(TestTrips.step("s2", LocalDate.of(2025, 5, 1), lat = 31.6, lng = -8.0)),
            ),
        )
        val vm = CarnetViewModel(trips, null, FakePlaces(), FakeMembers(), FakeLocator(), "adult1", flowOf(today))
        val job = launch { vm.uiState.collect {} }
        val ready = vm.uiState.first { it is CarnetUiState.Ready } as CarnetUiState.Ready
        assertEquals(1, ready.carnet.trips)
        assertEquals(listOf("FR", "PT"), ready.carnet.stamps.map { it.code })
        assertEquals(5, ready.carnet.days)
        job.cancel()
    }

    @Test
    fun `a hand-added destination comes in the carnet with its country`() = runTest {
        val places = FakePlaces()
        val vm = CarnetViewModel(FakeTrips(initial = emptyList()), null, places, FakeMembers(), FakeLocator(), "adult1", flowOf(today))
        val job = launch { vm.uiState.collect {} }
        vm.add(Place(31.63, -8.0, address = "Marrakech, Maroc"), LocalDate.of(2019, 4, 2), 5)
        assertEquals(AddState.Saved, vm.adding.value)
        val saved = places.places.value["adult1"].orEmpty().single()
        assertEquals("Marrakech", saved.name)
        assertEquals("MA", saved.countryCode)
        val ready = vm.uiState.first { (it as? CarnetUiState.Ready)?.manual?.isNotEmpty() == true } as CarnetUiState.Ready
        assertEquals(listOf("FR", "MA"), ready.carnet.stamps.map { it.code })
        assertEquals(0, ready.carnet.km)
        assertEquals(1, ready.carnet.manualPoints.size)
        assertTrue(Continent.Africa in ready.carnet.continents)
        vm.remove(saved.id)
        assertTrue(places.places.value["adult1"].orEmpty().isEmpty())
        job.cancel()
    }
}
