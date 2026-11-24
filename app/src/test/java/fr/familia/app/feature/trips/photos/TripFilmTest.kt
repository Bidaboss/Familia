package fr.familia.app.feature.trips.photos

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripFilmTest {
    private fun day(d: Int) = LocalDate.of(2027, 7, d)
    private val trip = Trip("t", "Été", day(10), day(20), emptyList(), emptyList(), "", "")
    private fun step(id: String, place: String, d: Int) = TripStep(id, StepType.Lodging, place, day(d), null, null, null, null)
    private var second = 0L
    private fun photo(stepId: String?, slot: Int = 1) = AlbumPhoto("trips/t", "adult1", "adult1", slot, Instant.ofEpochSecond(++second), stepId = stepId)

    @Test fun eachStepIsAnnouncedThenShowsItsPhotosInTheOrderOfTheTrip() {
        val steps = listOf(step("b", "Rome, Italie", 14), step("a", "Nice", 10))
        val photos = listOf(photo("b", 1), photo("a", 2), photo(null, 3), photo("a", 4))
        val plan = tripFilmPlan(trip, steps, photos)!!
        val shape = plan.shots.map { if (it is TripFilmShot.Title) "T:${it.number}:${it.name}" else "P:${(it as TripFilmShot.Photo).photo.slot}" }
        // Nice first (the 10th), then Rome (the 14th), then the photos of no step, without a title.
        assertEquals(listOf("T:1:Nice", "P:2", "P:4", "T:2:Rome", "P:1", "P:3"), shape)
        assertEquals(2.5, plan.secondsPerShot, 0.0)
    }

    @Test fun aStepWithoutPhotoHasNoTitle() {
        val steps = listOf(step("a", "Nice", 10), step("b", "Rome", 14))
        val plan = tripFilmPlan(trip, steps, listOf(photo("a"), photo("a", 2)))!!
        assertEquals(1, plan.shots.count { it is TripFilmShot.Title })
    }

    @Test fun oneOrNoPhotoMakesNoFilm() {
        assertNull(tripFilmPlan(trip, emptyList(), listOf(photo(null))))
    }

    @Test fun aLongTripStaysUnderThreeMinutes() {
        val steps = (1..5).map { step("s$it", "Ville $it", 10 + it) }
        val photos = steps.flatMap { s -> (1..30).map { photo(s.id, it) } } + (1..60).map { photo(null, it) }
        val plan = tripFilmPlan(trip, steps, photos)!!
        assertTrue(plan.seconds <= TRIP_FILM_MAX_SECONDS + 0.001)
        assertTrue(plan.secondsPerShot >= TRIP_FILM_MIN_SECONDS)
        // Every step keeps its title and at least one photo.
        assertEquals(5, plan.shots.count { it is TripFilmShot.Title })
        steps.forEach { s -> assertTrue(plan.shots.any { it is TripFilmShot.Photo && it.photo.stepId == s.id }) }
    }

    @Test fun thinningKeepsTheFirstAndTheLast() {
        assertEquals(listOf(1), thin((1..10).toList(), 1))
        assertEquals((1..4).toList(), thin((1..4).toList(), 9))
        val five = thin((1..10).toList(), 5)
        assertEquals(5, five.size)
        assertEquals(1, five.first())
        assertEquals(10, five.last())
    }
}
