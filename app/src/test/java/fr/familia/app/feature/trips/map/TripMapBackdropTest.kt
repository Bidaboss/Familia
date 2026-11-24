package fr.familia.app.feature.trips.map

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.TripStep
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripMapBackdropTest {
    private fun step(id: String) = TripStep(id, StepType.Lodging, "Rome", LocalDate.of(2027, 7, 10), null, null, null, null, lat = 41.9, lng = 12.5)

    @Test fun theSheetHasThreeHeights() {
        assertEquals(0.6f, backdropSheetShare(0))
        assertEquals(0.85f, backdropSheetShare(1))
        assertEquals(1f, backdropSheetShare(2))
    }

    @Test fun theEditedStepIsChosenOnTheMap() {
        val placed = listOf(step("a"), step("b"))
        assertEquals(1, focusIndex(placed, "b"))
        assertNull(focusIndex(placed, null))
        assertNull(focusIndex(placed, "absent"))
    }
}
