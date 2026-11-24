package fr.familia.app.feature.trips.detail

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsertSpotsTest {
    private fun day(d: Int) = LocalDate.of(2027, 7, d)
    private val trip = Trip("t", "Été", day(10), day(17), emptyList(), emptyList(), "", "")
    private fun step(id: String, type: StepType, from: Int, to: Int? = null) = TripStep(id, type, id, day(from), null, to?.let(::day), null, null)

    @Test
    fun aPlusAfterTheStartAndAfterEachElement() {
        val line = tripTimeline(trip, listOf(step("car", StepType.Car, 10), step("bordeaux", StepType.Lodging, 10, 12), step("samoure", StepType.Lodging, 12)))
        val spots = insertSpots(trip, line.rows)
        assertEquals(4, spots.size)
        assertNull(spots[0].after)
        assertEquals(day(10), spots[0].day)
        // Before any destination, the places go under the next one.
        assertEquals("bordeaux", spots[0].destinationId)
        assertEquals("bordeaux", spots[1].destinationId)
        // After a destination: the day it ends, and its places under it.
        assertEquals(day(12), spots[2].day)
        assertEquals("bordeaux", spots[2].destinationId)
        assertEquals("samoure", spots[3].destinationId)
        assertEquals(day(17), spots[3].day)
    }

    @Test
    fun anEmptyTripHasOnePlusWithoutDestination() {
        val spots = insertSpots(trip, tripTimeline(trip, emptyList()).rows)
        assertEquals(1, spots.size)
        assertNull(spots[0].destinationId)
    }

    @Test
    fun aTransportSitsOnTheRowOfItsPlusWithNoOtherPlusAroundIt() {
        // E-57: départ → [+ | transport] → destination → + → destination → [+ | transport] → arrivée.
        val a = TimelineRow.Destination(step("a", StepType.Lodging, 10, 12), 1, day(10), day(12))
        val b = TimelineRow.Destination(step("b", StepType.Lodging, 12, 14), 2, day(12), day(14))
        val car = TimelineRow.Move(step("car", StepType.Car, 10), null)
        val train = TimelineRow.Move(step("train", StepType.Train, 14), null)
        val rows = listOf(car, a, b, train)
        assertEquals(listOf(false, true, false, false), rows.indices.map { insertAfter(rows, it) })
    }
}
