package fr.familia.app.feature.trips.place

import androidx.lifecycle.SavedStateHandle
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.place.FoundPlace
import fr.familia.app.core.place.PlaceSearch
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Récit 103, règle 20: the search dedicated to each kind of place, around the destination. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceSearchViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private class FakeSearch(var answer: List<FoundPlace>? = emptyList()) : PlaceSearch {
        val asked = mutableListOf<Pair<TripPlaceKind, String>>()
        override suspend fun search(lat: Double, lng: Double, kind: TripPlaceKind, text: String): List<FoundPlace>? {
            asked += kind to text
            return answer
        }
    }

    private val port = FoundPlace("Le Bistrot du Port", "Restaurant", null, 41.9192, 8.7386, 350)

    private fun vm(search: FakeSearch?, lat: Double? = 41.92, lng: Double? = 8.74, saved: SavedStateHandle = SavedStateHandle()) =
        PlaceSearchViewModel(search, TripPlaceKind.Restaurant, lat, lng, waitMillis = 600, savedState = saved)

    private fun wait() {
        dispatcher.scheduler.advanceTimeBy(601)
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun `it opens on the places of its kind around the destination`() {
        val search = FakeSearch(listOf(port))
        val vm = vm(search)
        assertEquals(listOf(TripPlaceKind.Restaurant to ""), search.asked)
        assertEquals(PlaceSearchResults.Found(listOf(port)), vm.uiState.value.results)
    }

    @Test
    fun `typing asks once, a short while after the last key`() {
        val search = FakeSearch(listOf(port))
        val vm = vm(search)
        vm.type("bis")
        vm.type("bistrot")
        assertEquals(PlaceSearchResults.Loading, vm.uiState.value.results)
        wait()
        assertEquals(listOf("", "bistrot"), search.asked.map { it.second })
        assertEquals(PlaceSearchResults.Found(listOf(port)), vm.uiState.value.results)
    }

    @Test
    fun `a space at the end asks nothing new`() {
        val search = FakeSearch(listOf(port))
        val vm = vm(search)
        vm.type("port")
        wait()
        vm.type("port ")
        wait()
        assertEquals(listOf("", "port"), search.asked.map { it.second })
        assertEquals("port ", vm.uiState.value.text)
    }

    @Test
    fun `no answer says so, and retrying asks again`() {
        val search = FakeSearch(answer = null)
        val vm = vm(search)
        assertEquals(PlaceSearchResults.Failed, vm.uiState.value.results)
        search.answer = listOf(port)
        vm.retry()
        assertEquals(PlaceSearchResults.Found(listOf(port)), vm.uiState.value.results)
    }

    @Test
    fun `a destination without point only lets a name be typed`() {
        val search = FakeSearch(listOf(port))
        assertEquals(PlaceSearchResults.NoPoint, vm(search, lat = null).uiState.value.results)
        assertEquals(PlaceSearchResults.NoPoint, vm(null).uiState.value.results)
        assertTrue(search.asked.isEmpty())
    }

    @Test
    fun `the typed words come back when Android recreates the page`() {
        val saved = SavedStateHandle()
        vm(FakeSearch(), saved = saved).type("pizza")
        val search = FakeSearch()
        val again = vm(search, saved = saved)
        assertEquals("pizza", again.uiState.value.text)
        assertEquals(listOf("pizza"), search.asked.map { it.second })
    }
}
