package fr.familia.app.feature.trips.detail

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TripDaysTest {

    private val start = LocalDate.of(2027, 8, 2)
    private val trip = Trip("t", "Corse", start, start.plusDays(5), listOf("a"), listOf("a"), "a", "Camille")
    private val days = TripDays.of(trip)

    private fun step(id: String, depart: LocalDate, arrive: LocalDate? = null) =
        TripStep(id, StepType.Activity, "Lieu $id", depart, null, arrive, null, null)

    @Test
    fun `the strip holds every day of the trip, first to last`() {
        assertEquals(6, days.size)
        assertEquals(start, days.first())
        assertEquals(start.plusDays(5), days.last())
        assertEquals(listOf(start), TripDays.of(trip.copy(endDate = start)))
    }

    @Test
    fun `the day shown first is today during the trip, else the nearest end`() {
        assertEquals(start.plusDays(2), TripDays.firstShown(days, start.plusDays(2)))
        assertEquals(start, TripDays.firstShown(days, start.minusDays(30)))
        assertEquals(start.plusDays(5), TripDays.firstShown(days, start.plusDays(40)))
    }

    @Test
    fun `a step shows on every day it spans, and a step outside the trip on its nearest day`() {
        val lodging = step("gite", start, start.plusDays(3))
        val before = step("avant", start.minusDays(2))
        val after = step("apres", start.plusDays(9))
        val steps = listOf(lodging, before, after)
        assertEquals(listOf(lodging, before), TripDays.stepsOf(steps, start, days))
        assertEquals(listOf(lodging), TripDays.stepsOf(steps, start.plusDays(3), days))
        assertEquals(listOf(after), TripDays.stepsOf(steps, start.plusDays(5), days))
    }

    @Test
    fun `the path shows four days at most, the chosen one third when it can`() {
        assertEquals(0..3, TripDays.window(6, 0))
        assertEquals(0..3, TripDays.window(6, 2))
        assertEquals(1..4, TripDays.window(6, 3))
        assertEquals(2..5, TripDays.window(6, 5))
        assertEquals(0..1, TripDays.window(2, 1))
        assertEquals(0..0, TripDays.window(1, 0))
    }
}
