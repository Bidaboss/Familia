package fr.familia.app.feature.trips.map

import fr.familia.app.feature.trips.TripOpenTiming
import fr.familia.app.feature.trips.list.FLIGHT_MILLIS
import fr.familia.app.feature.trips.list.OPEN_AFTER_FLIGHT_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripArrivalTest {
    @Test
    fun theGlobeTurnsAboutHalfARoundThenDivesInAbout6Seconds() {
        // E-61: a turn of about 180°, then the zoom, 7 s at most (DESIGN-101 B).
        assertTrue(EditArrival.ARRIVAL_SPIN_DEGREES in 150.0..179.0)
        assertTrue(EditArrival.ARRIVAL_SPIN_MS + EditArrival.ARRIVAL_ZOOM_MS <= 7_000L)
        assertEquals(-168.0, spinStartLongitude(2.0), 1e-9)
        assertEquals(10.0, spinStartLongitude(180.0), 1e-9)
        assertTrue(spinStartLongitude(-170.0) in -180.0..180.0)
    }

    @Test
    fun aTripOpensAtOnceWhileTheGlobeStillFlies() {
        // E-60: less than half a second before the screen of the trip; the flight is short.
        assertTrue(OPEN_AFTER_FLIGHT_MILLIS < 500L)
        assertTrue(FLIGHT_MILLIS <= 1_000L)
    }

    @Test
    fun theTimeOfTheOpeningIsCountedFromTheTouch() {
        assertEquals(420L, TripOpenTiming.elapsed(1_000L, 1_420L))
        assertNull(TripOpenTiming.elapsed(0L, 1_420L))
        assertNull(TripOpenTiming.elapsed(1_000L, 100_000L))
    }
}
