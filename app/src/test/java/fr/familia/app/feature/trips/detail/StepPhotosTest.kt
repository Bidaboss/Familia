package fr.familia.app.feature.trips.detail

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class StepPhotosTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private fun day(d: Int) = LocalDate.of(2027, 7, d)
    private fun at(d: Int, hour: Int = 12) = day(d).atTime(hour, 0).toInstant(ZoneOffset.UTC)
    private val trip = Trip("t", "Été", day(10), day(17), emptyList(), emptyList(), "", "")
    private fun step(id: String, type: StepType, from: Int, to: Int? = null) =
        TripStep(id, type, id, day(from), null, to?.let(::day), null, null, lat = 45.0, lng = 0.0)

    private val line = tripTimeline(
        trip,
        listOf(step("car", StepType.Car, 10), step("bordeaux", StepType.Lodging, 10, 12), step("samoure", StepType.Lodging, 12)),
    )

    private fun photo(slot: Int, created: Int, taken: Int? = null) =
        AlbumPhoto("trips/t", "m", "M", slot, at(created), taken?.let { at(it) })

    @Test
    fun aPhotoGoesToTheDestinationOfTheDayItWasTaken() {
        val result = stepPhotos(line, listOf(photo(1, created = 16, taken = 11), photo(2, created = 14)), zone)
        assertEquals(listOf("m_1"), result.byStay.getValue("bordeaux").map { it.id })
        // Without the day it was taken, the day it was added.
        assertEquals(listOf("m_2"), result.byStay.getValue("samoure").map { it.id })
        assertEquals(emptyList<AlbumPhoto>(), result.withoutStep)
    }

    @Test
    fun onTheDayOfAMoveThePhotoGoesToTheDestinationThatBegins() {
        val result = stepPhotos(line, listOf(photo(1, created = 12, taken = 12)), zone)
        assertEquals(setOf("samoure"), result.byStay.keys)
    }

    @Test
    fun aPhotoOutOfEveryDestinationIsWithoutStep() {
        val result = stepPhotos(line, listOf(photo(1, created = 20, taken = 5), photo(2, created = 11)), zone)
        assertEquals(listOf("m_1"), result.withoutStep.map { it.id })
        assertEquals(listOf("m_2"), result.byStay.getValue("bordeaux").map { it.id })
    }

    @Test
    fun aPhotoAddedFromADestinationStaysUnderItWhateverItsDay() {
        val chosen = photo(1, created = 11, taken = 11).copy(stepId = "samoure")
        val gone = photo(2, created = 11, taken = 11).copy(stepId = "removed")
        val result = stepPhotos(line, listOf(chosen, gone), zone)
        assertEquals(listOf("m_1"), result.byStay.getValue("samoure").map { it.id })
        // Its step removed, the photo goes back to its day.
        assertEquals(listOf("m_2"), result.byStay.getValue("bordeaux").map { it.id })
    }

    @Test
    fun theQuickAddGoesUnderTheDestinationOfTheDay() {
        assertEquals("bordeaux", currentStay(line, day(11))?.step?.id)
        // The day of a move: the destination that begins.
        assertEquals("samoure", currentStay(line, day(12))?.step?.id)
        assertEquals(null, currentStay(line, day(9)))
        assertEquals(null, currentStay(line, day(18)))
    }
}
