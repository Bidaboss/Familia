package fr.familia.app.feature.trips.map

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.TripStep
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripMapBackdropTest {
    private fun step(id: String) = TripStep(id, StepType.Lodging, "Rome", LocalDate.of(2027, 7, 10), null, null, null, null, lat = 41.9, lng = 12.5)

    @Test fun theSheetHasThreeHeights() {
        assertEquals(0.22f, backdropSheetShare(0))
        assertEquals(0.48f, backdropSheetShare(1))
        assertEquals(1f, backdropSheetShare(2))
    }

    @Test fun theEditedStepIsChosenOnTheMap() {
        val placed = listOf(step("a"), step("b"))
        assertEquals(1, focusIndex(placed, "b"))
        assertNull(focusIndex(placed, null))
        assertNull(focusIndex(placed, "absent"))
    }

    @Test fun modifierPlaysTheArrivalOnlyWithTheMapAndAnimations() {
        assertTrue(playsEditArrival(asked = true, withMap = true, animationsOff = false))
        assertFalse(playsEditArrival(asked = false, withMap = true, animationsOff = false))
        assertFalse(playsEditArrival(asked = true, withMap = false, animationsOff = false))
        assertFalse(playsEditArrival(asked = true, withMap = true, animationsOff = true))
    }

    @Test fun theArrivalLastsAboutSixSeconds() {
        // DESIGN-101 B: about 6 s of camera after the sheet, the globe seen whole.
        val camera = EditArrival.CAMERA_WAIT_MS + EditArrival.OUT_MS + EditArrival.IN_MS
        assertTrue(camera in 5_500L..6_500L)
        assertTrue(EditArrival.GLOBE_ZOOM <= 1.5)
        // The page of a trip too: out to the continent, then in on the steps.
        assertTrue(EditArrival.HOME_OUT_MS + EditArrival.HOME_IN_MS in 5_500L..6_500L)
    }
}
