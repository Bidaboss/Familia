package fr.familia.app.feature.trips.place

import androidx.lifecycle.SavedStateHandle
import fr.familia.app.core.data.TripPlaceRepository
import fr.familia.app.core.model.PlaceKind
import fr.familia.app.core.model.TripPlace
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.trips.FakeTrips
import fr.familia.app.feature.trips.TestTrips
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalTime

/** Récit 103: the page of an accommodation, a restaurant or a spot of a destination. Fictional family of TestFamily. */
class PlaceEditTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakePlaces(initial: List<TripPlace> = emptyList(), private val failWith: Exception? = null) : TripPlaceRepository {
        val places = MutableStateFlow(initial)
        val saved = mutableListOf<Pair<TripPlace, Boolean>>()
        val deleted = mutableListOf<String>()
        override fun observePlaces(tripId: String): Flow<List<TripPlace>> = places
        override suspend fun getPlace(tripId: String, placeId: String): TripPlace? = places.value.firstOrNull { it.id == placeId }
        override fun newPlaceId(tripId: String): String = "p-new"
        override suspend fun savePlace(tripId: String, place: TripPlace, isNew: Boolean) {
            failWith?.let { throw it }
            saved += place to isNew
        }
        override suspend fun deletePlace(tripId: String, placeId: String) {
            failWith?.let { throw it }
            deleted += placeId
        }
    }

    private val bistrot = TripPlace(
        id = "p1", stepId = "s1", kind = PlaceKind.Restaurant, name = "Le Bistrot du Port", address = "Quai Napoléon, Ajaccio",
        lat = 41.9192, lng = 8.7386, arriveTime = LocalTime.of(20, 0), note = "Table au nom de Camille", cost = 80.0,
    )

    private fun trips() = FakeTrips(steps = mapOf("t1" to listOf(TestTrips.step("s1"))))

    private fun vm(
        places: FakePlaces = FakePlaces(listOf(bistrot)),
        me: String = "adult2",
        placeId: String? = "p1",
        kind: PlaceKind? = null,
        stepId: String? = null,
        saved: SavedStateHandle = SavedStateHandle(),
    ) = PlaceEditViewModel(trips(), places, FakeMembers(), me, "t1", placeId, stepId = stepId, kind = kind, savedState = saved)

    private fun PlaceEditViewModel.editing() = uiState.value as PlaceEditUiState.Editing

    @Test
    fun `a participant opens a place with its hour, its note and its cost, under its destination`() {
        val state = vm().editing()
        assertEquals(PlaceKind.Restaurant, state.kind)
        assertEquals("Le Bistrot du Port", state.form.name)
        assertEquals(LocalTime.of(20, 0), state.form.arriveTime)
        assertEquals("80", state.form.cost)
        assertEquals("Ajaccio", state.destination)
        assertTrue(state.canEdit)
        assertTrue(state.canDelete)
        assertFalse(state.isNew)
    }

    @Test
    fun `changing the hour and the note keeps the place under its destination and at its address`() {
        val places = FakePlaces(listOf(bistrot))
        val vm = vm(places)
        vm.update { it.copy(arriveTime = LocalTime.of(19, 30), note = "Terrasse\nAllergie aux noix", cost = "92,50") }
        assertTrue(vm.editing().hasChanges)
        vm.save()
        assertEquals(PlaceEditUiState.Done, vm.uiState.value)
        val (place, isNew) = places.saved.single()
        assertFalse(isNew)
        assertEquals("s1", place.stepId)
        assertEquals(LocalTime.of(19, 30), place.arriveTime)
        assertEquals("Terrasse\nAllergie aux noix", place.note)
        assertEquals(92.5, place.cost!!, 0.001)
        assertEquals(41.9192, place.lat)
        assertEquals("Quai Napoléon, Ajaccio", place.address)
    }

    @Test
    fun `another name is another place, its old address and point go`() {
        val places = FakePlaces(listOf(bistrot))
        val vm = vm(places)
        vm.update { it.copy(name = "Chez Paulo") }
        vm.save()
        val place = places.saved.single().first
        assertEquals("Chez Paulo", place.name)
        assertNull(place.address)
        assertNull(place.lat)
        assertFalse(place.hasPosition)
    }

    @Test
    fun `a new place needs a name, and is saved under the destination with its kind`() {
        val places = FakePlaces()
        val vm = vm(places, placeId = null, stepId = "s1", kind = PlaceKind.Lodging)
        val state = vm.editing()
        assertTrue(state.isNew)
        assertFalse(state.canDelete)
        vm.save()
        assertEquals(setOf(PlaceFormError.NameMissing), vm.editing().errors)
        vm.update { it.copy(name = "  Camping Les Pins  ") }
        assertTrue(vm.editing().errors.isEmpty())
        vm.save()
        val (place, isNew) = places.saved.single()
        assertTrue(isNew)
        assertEquals("p-new", place.id)
        assertEquals(PlaceKind.Lodging, place.kind)
        assertEquals("Camping Les Pins", place.name)
        assertNull(place.arriveTime)
        assertNull(place.note)
        assertNull(place.cost)
    }

    @Test
    fun `a follower who does not take part reads the place and changes nothing`() {
        val places = FakePlaces(listOf(bistrot))
        val vm = vm(places, me = "kid1")
        val state = vm.editing()
        assertFalse(state.canEdit)
        assertFalse(state.canDelete)
        vm.update { it.copy(note = "Rien") }
        vm.save()
        vm.delete()
        assertEquals("Table au nom de Camille", vm.editing().form.note)
        assertTrue(places.saved.isEmpty())
        assertTrue(places.deleted.isEmpty())
    }

    @Test
    fun `a follower adds no place, and a place or a destination that is gone says so`() {
        assertEquals(PlaceEditUiState.Unavailable, vm(placeId = null, stepId = "s1", kind = PlaceKind.Spot, me = "kid1").uiState.value)
        assertEquals(PlaceEditUiState.Unavailable, vm(placeId = "gone").uiState.value)
        assertEquals(PlaceEditUiState.Unavailable, vm(placeId = null, stepId = "nowhere", kind = PlaceKind.Spot).uiState.value)
    }

    @Test
    fun `deleting the place closes the page`() {
        val places = FakePlaces(listOf(bistrot))
        val vm = vm(places)
        vm.delete()
        assertEquals(listOf("p1"), places.deleted)
        assertEquals(PlaceEditUiState.Done, vm.uiState.value)
    }

    @Test
    fun `a refused write keeps the page open and says so`() {
        val vm = vm(FakePlaces(listOf(bistrot), failWith = IOException("hors ligne")))
        vm.update { it.copy(note = "Autre") }
        vm.save()
        assertTrue(vm.editing().saveFailed)
        assertFalse(vm.editing().isSaving)
    }

    @Test
    fun `the draft comes back when Android recreates the page, with the same id`() {
        val saved = SavedStateHandle()
        val first = vm(FakePlaces(), placeId = null, stepId = "s1", kind = PlaceKind.Spot, saved = saved)
        first.update { it.copy(name = "Citadelle", note = "Visite à 10 h") }
        val again = vm(FakePlaces(), placeId = null, stepId = "s1", kind = PlaceKind.Spot, saved = saved)
        assertEquals("Citadelle", again.editing().form.name)
        assertEquals("Visite à 10 h", again.editing().form.note)
        assertTrue(again.editing().hasChanges)
        assertEquals("p-new", saved.get<String>(PlaceEditViewModel.DRAFT_ID))
    }

    @Test
    fun `the limits are those of the rules`() {
        assertEquals(setOf(PlaceFormError.NameTooLong), validate(PlaceForm(name = "a".repeat(MAX_PLACE_NAME + 1))))
        assertEquals(setOf(PlaceFormError.NoteTooLong), validate(PlaceForm(name = "Port", note = "a".repeat(MAX_PLACE_NOTE + 1))))
        assertTrue(validate(PlaceForm(name = "Port", note = "a".repeat(MAX_PLACE_NOTE))).isEmpty())
        assertEquals(setOf(PlaceFormError.CostInvalid), validate(PlaceForm(name = "Port", cost = "beaucoup")))
        assertEquals(setOf(PlaceFormError.CostInvalid), validate(PlaceForm(name = "Port", cost = "-5")))
    }

    @Test
    fun `a cost is read as a French phone types it`() {
        assertEquals(12.5, parseCost("12,50")!!, 0.001)
        assertEquals(12.5, parseCost("12.5")!!, 0.001)
        assertEquals(1200.0, parseCost("1 200")!!, 0.001)
        assertEquals(80.0, parseCost("80 €")!!, 0.001)
        assertNull(parseCost(""))
        assertNull(parseCost("12,505"))
        assertNull(parseCost("2000000"))
        assertEquals("80", costText(80.0))
        assertEquals("12,50", costText(12.5))
    }

    @Test
    fun `hours and money read as in France`() {
        assertEquals("20 h", hourText(LocalTime.of(20, 0)))
        assertEquals("9 h 05", hourText(LocalTime.of(9, 5)))
        assertEquals("€", currencySymbol(null))
        assertEquals("€", currencySymbol("EUR"))
        assertEquals("XYZ", currencySymbol("XYZ"))
        assertEquals(LocalTime.of(16, 0), defaultHour(PlaceKind.Lodging))
    }
}
