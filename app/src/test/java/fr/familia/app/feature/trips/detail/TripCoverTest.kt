package fr.familia.app.feature.trips.detail

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCoverTest {
    private fun day(d: Int) = LocalDate.of(2027, 4, d)
    private val trip = Trip("t", "Printemps", day(11), day(16), emptyList(), emptyList(), "", "")
    private fun step(type: StepType, place: String, from: Int) =
        TripStep(place, type, place, day(from), null, null, null, null, lat = 45.0, lng = -1.0)

    @Test
    fun theCoverShowsOnlyTheDestinationsNeverATransport() {
        val steps = listOf(
            step(StepType.Car, "Samoure-Gare → Les Pins", 11),
            step(StepType.Lodging, "Les Pins", 11),
            step(StepType.Activity, "Le phare", 12),
            step(StepType.Train, "Les Pins → Port-Clair", 13),
            step(StepType.Lodging, "Port-Clair, Charente", 13),
        )
        val places = coverPlaces(trip, steps)
        assertEquals(listOf("Les Pins", "Port-Clair"), places.map { it.name })
        assertTrue(places.all { it.fromStep })
    }

    @Test
    fun aTripWithoutAStayShowsWhereItsTransportsArriveAndItsActivitiesNeverADeparture() {
        val steps = listOf(
            step(StepType.Flight, "Samoure-Gare → Port-Clair", 11),
            step(StepType.Activity, "Le phare", 12),
            step(StepType.Car, "Port-Clair → Les Pins", 13),
        )
        val places = coverPlaces(trip, steps)
        assertEquals(listOf("Port-Clair", "Le phare", "Les Pins"), places.map { it.name })
        assertTrue(places.all { it.fromStep })
        // The point of a route is not the one of its arrival; an activity keeps its own.
        assertNull(places[0].point)
        assertNotNull(places[1].point)
    }

    @Test
    fun withoutAnyStepTheTitleNeverGoesToWikipedia() {
        val places = coverPlaces(trip, emptyList())
        assertEquals(listOf("Printemps"), places.map { it.name })
        assertFalse(places.single().fromStep)
    }

    @Test
    fun thePlaceNamedByTheTripComesFirstThenTheDestinationsEachOnce() {
        val steps = listOf(step(StepType.Lodging, "Naxos", 11), step(StepType.Lodging, "Cyclades", 13))
        val named = fr.familia.app.core.place.NamedPlace("Cyclades", 37.0, 25.0)
        val places = coverPlacesWithName(trip, steps, named, chosenForName = false)
        assertEquals(listOf("Cyclades", "Naxos"), places.map { it.name })
        assertEquals(TRIP_COVER_KEY, places[0].stepId)
        assertEquals(37.0, places[0].point!!.lat, 0.0)
    }

    @Test
    fun withoutASurePlaceTheCoverStaysOnTheDestinations() {
        val steps = listOf(step(StepType.Lodging, "Naxos", 11))
        assertEquals(coverPlaces(trip, steps), coverPlacesWithName(trip, steps, null, chosenForName = false))
        assertEquals(coverPlaces(trip, emptyList()), coverPlacesWithName(trip, emptyList(), null, chosenForName = false))
    }

    @Test
    fun aPhotoChosenForTheNameStaysWhenTheNameChanges() {
        val steps = listOf(step(StepType.Lodging, "Naxos", 11))
        val places = coverPlacesWithName(trip, steps, null, chosenForName = true)
        assertEquals(listOf("Printemps", "Naxos"), places.map { it.name })
        assertEquals(TRIP_COVER_KEY, places[0].stepId)
        assertFalse(places[0].fromStep)
        assertNull(places[0].point)
    }

    @Test
    fun aTripWithoutStepsShowsThePlaceOfItsNameInsteadOfItsTitle() {
        val named = fr.familia.app.core.place.NamedPlace("Cyclades", 37.0, 25.0)
        assertEquals(listOf("Cyclades"), coverPlacesWithName(trip, emptyList(), named, chosenForName = false).map { it.name })
    }
}
