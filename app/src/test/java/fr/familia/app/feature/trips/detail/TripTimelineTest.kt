package fr.familia.app.feature.trips.detail

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripPlace
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.model.TripStep
import java.time.LocalTime
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTimelineTest {
    private fun day(d: Int) = LocalDate.of(2027, 7, d)
    private val trip = Trip("t", "Été", day(10), day(17), emptyList(), emptyList(), "", "")
    private fun step(id: String, type: StepType, from: Int, to: Int? = null, lat: Double? = null, lng: Double? = null) =
        TripStep(id, type, id, day(from), null, to?.let(::day), null, null, lat = lat, lng = lng)

    private val car = step("car", StepType.Car, 10, lat = 48.85, lng = 2.35)
    private val bordeaux = step("bordeaux", StepType.Lodging, 10, 12, lat = 44.84, lng = -0.58)
    private val palmyre = step("palmyre", StepType.Lodging, 12, lat = 45.69, lng = -1.18)
    private val steps = listOf(palmyre, bordeaux, car)

    @Test
    fun theLineHasTheDestinationsWithTheirNightsAndTheMovesBetween() {
        val line = tripTimeline(trip, steps)
        assertEquals(listOf("car", "bordeaux", "palmyre"), line.rows.map { it.step.id })
        val (first, second) = line.destinations
        assertEquals(1, first.number)
        assertEquals(2, first.nights)
        // The last stay goes on until the return.
        assertEquals(5, second.nights)
        assertEquals(7, line.planned)
        assertEquals(7, line.total)
        assertEquals(0, line.missing)
        assertNull((line.rows.first() as TimelineRow.Move).km)
    }

    @Test
    fun aMoveAfterAPlacedStepHasItsDistance() {
        val back = step("back", StepType.Car, 16, lat = 48.85, lng = 2.35)
        val move = tripTimeline(trip, steps + back).rows.last() as TimelineRow.Move
        assertTrue(move.km!! in 350..500)
    }

    @Test
    fun oneNightMoreMovesTheEndAndEveryStepAfter() {
        val change = changeNights(trip, listOf(bordeaux, palmyre.copy(arriveDate = day(15))), bordeaux, 1) as NightsChange.Steps
        val byId = change.changed.associateBy { it.id }
        assertEquals(day(13), byId.getValue("bordeaux").arriveDate)
        assertEquals(day(13), byId.getValue("palmyre").departDate)
        assertEquals(day(16), byId.getValue("palmyre").arriveDate)
    }

    @Test
    fun neverPastTheReturnNorUnderOneNight() {
        assertEquals(NightsChange.Blocked(NightsBlock.AtReturn), changeNights(trip, steps, palmyre, 1))
        val short = step("short", StepType.Lodging, 10, 11)
        assertEquals(NightsChange.Blocked(NightsBlock.OneNight), changeNights(trip, listOf(short), short, -1))
        val less = changeNights(trip, steps, bordeaux, -1) as NightsChange.Steps
        assertEquals(day(11), less.changed.first { it.id == "bordeaux" }.arriveDate)
        assertEquals(day(11), less.changed.first { it.id == "palmyre" }.departDate)
    }

    @Test fun theProgrammeOfADestinationPutsTheAccommodationFirstThenByTime() {
        fun place(id: String, step: String, kind: TripPlaceKind, name: String, at: LocalTime? = null) =
            TripPlace(id, step, kind, name, null, null, null, at, null)
        val byStep = placesByStep(
            listOf(
                place("r2", "s1", TripPlaceKind.Restaurant, "Le Port", LocalTime.of(20, 0)),
                place("sp", "s1", TripPlaceKind.Spot, "Phare"),
                place("r1", "s1", TripPlaceKind.Restaurant, "Chez Lou", LocalTime.of(12, 30)),
                place("h", "s1", TripPlaceKind.Lodging, "Camping"),
                place("o", "s2", TripPlaceKind.Spot, "Zoo"),
            ),
        )
        assertEquals(listOf("h", "r1", "r2", "sp"), byStep["s1"]!!.map { it.id })
        assertEquals(listOf("o"), byStep["s2"]!!.map { it.id })
    }
}
