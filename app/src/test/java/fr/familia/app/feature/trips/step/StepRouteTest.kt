package fr.familia.app.feature.trips.step

import fr.familia.app.core.model.StepType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** E-64: « Départ de » and « Arrivée à » for a transport, kept as « Paris → Bordeaux ». */
class StepRouteTest {
    private val day = LocalDate.of(2027, 8, 2)

    @Test
    fun `the two towns are cut as typed, spaces kept`() {
        assertEquals("Paris" to "Bordeaux", routeParts("Paris → Bordeaux"))
        assertEquals("Le " to "", routeParts(routeOf("Le ", "")))
        assertEquals("" to "Bordeaux", routeParts(routeOf("", "Bordeaux")))
        assertEquals("", routeOf("", ""))
    }

    @Test
    fun `an old place is the departure, other arrows are cut too`() {
        assertEquals("Samoure" to "", routeParts("Samoure"))
        assertEquals("Lyon" to "Camilles", routeParts("Lyon->Camilles"))
        assertEquals("Lyon" to "Camilles", routeParts("Lyon→Camilles"))
    }

    @Test
    fun `a transport is saved as one line with the arrow`() {
        val form = StepForm(type = StepType.Train, place = routeOf(" lyon ", "camilles"), departDate = day)
        assertEquals("Lyon → Camilles", form.toStep("s1", null).place)
        assertEquals("Lyon", StepForm(type = StepType.Car, place = routeOf("lyon", ""), departDate = day).toStep("s2", null).place)
    }

    @Test
    fun `a transport needs one of its towns, a stay its place`() {
        assertEquals(setOf(StepFormError.PlaceMissing), validate(StepForm(type = StepType.Bus, place = routeOf(" ", ""), departDate = day)))
        assertTrue(validate(StepForm(type = StepType.Bus, place = routeOf("", "Noé"), departDate = day)).isEmpty())
        assertEquals(setOf(StepFormError.PlaceMissing), validate(StepForm(type = StepType.Lodging, place = "", departDate = day)))
    }
}
