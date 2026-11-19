package fr.familia.app.feature.events.edit

import androidx.lifecycle.SavedStateHandle
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.PlaceKind
import fr.familia.app.feature.events.FakeEvents
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.events.eventPlaceView
import fr.familia.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** « Où » of an event (récit 74): chez un foyer, une adresse, ou autre. Fictional family and places. */
class EventPlaceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val withAddress = TestFamily.family.copy(
        households = listOf(Household("h1", "Foyer Un", "3 rue des Lilas, Nantes"), Household("h2", "Foyer Deux")),
    )

    private fun vm(events: FakeEvents, family: Family = withAddress, eventId: String? = null, handle: SavedStateHandle? = null) =
        EventEditViewModel(events, FakeMembers(family), "adult1", eventId, savedState = handle)

    private fun EventEditViewModel.editing() = uiState.value as EventEditUiState.Editing

    @Test
    fun `a household chosen saves its name and its id`() {
        val events = FakeEvents()
        val viewModel = vm(events)
        assertEquals(listOf("Foyer Deux", "Foyer Un"), viewModel.editing().households.map { it.name })
        viewModel.update { it.copy(title = "Galette", date = LocalDate.of(2027, 1, 6)).withPlaceKind(PlaceKind.Household) }
        viewModel.update { it.atHousehold(withAddress.households[1]) }
        viewModel.save()
        val event = events.saved.single().first
        assertEquals("Foyer Deux", event.place)
        assertEquals(PlaceKind.Household, event.placeKind)
        assertEquals("h2", event.placeHouseholdId)
    }

    @Test
    fun `« Chez un foyer » with no household picked saves no place`() {
        val events = FakeEvents()
        val viewModel = vm(events)
        viewModel.update { it.copy(title = "Galette", date = LocalDate.of(2027, 1, 6), place = "Au parc").withPlaceKind(PlaceKind.Household) }
        viewModel.save()
        val event = events.saved.single().first
        assertNull(event.place)
        assertEquals(PlaceKind.Other, event.placeKind)
        assertNull(event.placeHouseholdId)
    }

    @Test
    fun `an address keeps its text, autre is the free text of before`() {
        val form = EventForm(place = "Au parc").withPlaceKind(PlaceKind.Address)
        assertEquals("Au parc", form.place)
        val event = form.copy(title = "Pique-nique", date = LocalDate.of(2027, 6, 1), target = TestFamily.event("x", TestFamily.adult).target)
            .toEvent("e1", "adult1", "Adult1")
        assertEquals(PlaceKind.Address, event.placeKind)
        assertNull(event.placeHouseholdId)
        // Leaving a household never leaves its name as a text to edit.
        assertEquals("", EventForm().atHousehold(withAddress.households[0]).withPlaceKind(PlaceKind.Other).place)
    }

    @Test
    fun `an event of before opens as autre, one at a household shows its name of now`() {
        val old = TestFamily.event("e1", TestFamily.adult).copy(place = "Au gymnase")
        assertEquals(PlaceKind.Other, vm(FakeEvents(listOf(old)), eventId = "e1").editing().form.placeKind)

        val renamed = TestFamily.event("e2", TestFamily.adult).copy(place = "Ancien nom", placeKind = PlaceKind.Household, placeHouseholdId = "h1")
        val form = vm(FakeEvents(listOf(renamed)), eventId = "e2").editing().form
        assertEquals("Foyer Un", form.place)
        assertEquals("h1", form.placeHouseholdId)

        val gone = renamed.copy(id = "e3", placeHouseholdId = "h9")
        val kept = vm(FakeEvents(listOf(gone)), eventId = "e3").editing().form
        assertEquals(PlaceKind.Other, kept.placeKind)
        assertEquals("Ancien nom", kept.place)
    }

    @Test
    fun `the place survives a draft`() {
        val handle = SavedStateHandle()
        vm(FakeEvents(), handle = handle).update { it.copy(title = "Galette").atHousehold(withAddress.households[0]) }
        val form = vm(FakeEvents(), handle = handle).editing().form
        assertEquals(PlaceKind.Household, form.placeKind)
        assertEquals("h1", form.placeHouseholdId)
        assertEquals("Foyer Un", form.place)
    }

    @Test
    fun `the event's screen gives « Itinéraire » as soon as there is an address`() {
        val base = TestFamily.event("e1", TestFamily.adult)
        val atOne = base.copy(place = "Ancien nom", placeKind = PlaceKind.Household, placeHouseholdId = "h1")
        val one = eventPlaceView(atOne, withAddress.households, placesOn = false)!!
        assertEquals("Foyer Un", one.text)
        assertEquals("geo:0,0?q=3%20rue%20des%20Lilas%2C%20Nantes", one.directions)

        val two = eventPlaceView(atOne.copy(placeHouseholdId = "h2"), withAddress.households, placesOn = true)!!
        assertEquals("Foyer Deux", two.text)
        assertNull(two.directions)

        val address = eventPlaceView(base.copy(place = "12 rue des Roses", placeKind = PlaceKind.Address), emptyList(), placesOn = false)!!
        assertEquals("geo:0,0?q=12%20rue%20des%20Roses", address.directions)

        val other = base.copy(place = "Au parc")
        assertNull(eventPlaceView(other, emptyList(), placesOn = false)!!.directions)
        assertEquals("geo:0,0?q=Au%20parc", eventPlaceView(other, emptyList(), placesOn = true)!!.directions)
        assertNull(eventPlaceView(base, emptyList(), placesOn = true))
    }
}
