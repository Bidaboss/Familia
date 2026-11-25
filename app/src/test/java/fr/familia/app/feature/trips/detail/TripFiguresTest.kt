package fr.familia.app.feature.trips.detail

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** « Le voyage en chiffres » (récit 89). */
class TripFiguresTest {
    private val trip = Trip("t1", "La Palmyre", LocalDate.of(2026, 4, 11), LocalDate.of(2026, 4, 16), listOf("ines"), listOf("ines"), "ines", "ines")

    private fun step(id: String, place: String, lat: Double? = null, lng: Double? = null) =
        TripStep(id, StepType.Lodging, place, LocalDate.of(2026, 4, 11), null, null, null, null, lat = lat, lng = lng)

    @Test
    fun `no step, no tile`() = assertNull(tripFigures(trip, emptyList(), 3))

    @Test
    fun `a trip just begun counts one step and its days, with no distance`() {
        val figures = tripFigures(trip, listOf(step("a", "Lyon")), 0)!!
        assertEquals(1, figures.steps)
        assertEquals(6, figures.days)
        assertNull(figures.distanceKm)
        assertFalse(figures.showPlaces)
        assertFalse(figures.showPhotos)
    }

    @Test
    fun `places are counted once, whatever the case`() {
        val figures = tripFigures(trip, listOf(step("a", "Lyon"), step("b", " lyon "), step("c", "Bordeaux")), 38)!!
        assertEquals(2, figures.places)
        assertTrue(figures.showPlaces)
        assertEquals(38, figures.photos)
        assertTrue(figures.showPhotos)
    }

    @Test
    fun `the distance adds the legs between the placed steps in order`() {
        // Lyon -> Bordeaux is about 435 km, Bordeaux -> La Palmyre about 120 km.
        val figures = tripFigures(
            trip,
            listOf(step("a", "Lyon", 45.76, 4.84), step("b", "Bordeaux", 44.84, -0.58), step("c", "La Palmyre", 45.68, -1.18)),
            0,
        )!!
        val km = figures.distanceKm!!
        assertTrue("distance $km", km in 520..600)
        assertFalse(figures.showsUnplacedLine)
    }

    @Test
    fun `a step with no position is not counted and the tile says so`() {
        val figures = tripFigures(trip, listOf(step("a", "Lyon", 45.76, 4.84), step("b", "Chez Mamie"), step("c", "Bordeaux", 44.84, -0.58)), 0)!!
        assertEquals(2, figures.placed)
        assertEquals(3, figures.steps)
        assertTrue(figures.showsUnplacedLine)
    }

    @Test
    fun `one placed step is not enough for a distance`() {
        val figures = tripFigures(trip, listOf(step("a", "Lyon", 45.76, 4.84), step("b", "Bordeaux")), 0)!!
        assertNull(figures.distanceKm)
    }

    @Test
    fun `the flight distance of two known cities`() {
        // Paris -> Lyon, about 392 km.
        val km = flightKm(48.8566, 2.3522, 45.7640, 4.8357)
        assertTrue("km $km", km in 385.0..400.0)
    }
}
