package fr.familia.app.feature.trips.detail

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.TripArrival
import fr.familia.app.core.model.TripStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StepArrivalDayTest {

    private val day = LocalDate.of(2027, 4, 11)

    private fun step(id: String, depart: LocalDate, arrive: LocalDate? = null, arrived: Boolean = false) =
        TripStep(
            id, StepType.Car, "Lieu $id", depart, null, arrive, null, null,
            arrival = if (arrived) TripArrival(null, "a", "l'admin") else null,
        )

    @Test
    fun `no « Bien arrivé » 138 days before the departure`() {
        assertFalse(StepArrivalDay.isOpen(step("s", day), day.minusDays(138)))
        assertFalse(StepArrivalDay.isOpen(step("s", day), day.minusDays(1)))
    }

    @Test
    fun `« Bien arrivé » on the day of the step and the day after`() {
        assertTrue(StepArrivalDay.isOpen(step("s", day), day))
        assertTrue(StepArrivalDay.isOpen(step("s", day), day.plusDays(1)))
        assertFalse(StepArrivalDay.isOpen(step("s", day), day.plusDays(2)))
    }

    @Test
    fun `a step over several days stays open until the day after its arrival`() {
        val long = step("s", day, arrive = day.plusDays(2))
        assertTrue(StepArrivalDay.isOpen(long, day.plusDays(1)))
        assertTrue(StepArrivalDay.isOpen(long, day.plusDays(3)))
        assertFalse(StepArrivalDay.isOpen(long, day.plusDays(4)))
    }

    @Test
    fun `the word of the step waits for the departure`() {
        assertFalse(StepArrivalDay.hasStarted(step("s", day), day.minusDays(1)))
        assertTrue(StepArrivalDay.hasStarted(step("s", day), day))
        assertTrue(StepArrivalDay.hasStarted(step("s", day), day.plusDays(10)))
    }

    @Test
    fun `« Prochaine étape » is the step of today, not one weeks away`() {
        val steps = listOf(step("b", day.plusDays(1)), step("a", day), step("c", day.minusDays(5)))
        assertNull(StepArrivalDay.stepOfToday(steps, day.minusDays(138)))
        assertEquals("a", StepArrivalDay.stepOfToday(steps, day)?.id)
        assertEquals("b", StepArrivalDay.stepOfToday(steps, day.plusDays(2))?.id)
    }

    @Test
    fun `a step already arrived is not offered again`() {
        val steps = listOf(step("a", day, arrived = true), step("b", day))
        assertEquals("b", StepArrivalDay.stepOfToday(steps, day)?.id)
        assertNull(StepArrivalDay.stepOfToday(listOf(step("a", day, arrived = true)), day))
    }
}
