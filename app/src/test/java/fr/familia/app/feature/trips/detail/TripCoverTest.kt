package fr.familia.app.feature.trips.detail

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun withoutADestinationTheTitleNeverGoesToWikipedia() {
        val places = coverPlaces(trip, listOf(step(StepType.Car, "Samoure-Gare → Les Pins", 11)))
        assertEquals(listOf("Printemps"), places.map { it.name })
        assertFalse(places.single().fromStep)
    }
}
