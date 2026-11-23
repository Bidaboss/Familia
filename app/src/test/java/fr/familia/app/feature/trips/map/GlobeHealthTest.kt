package fr.familia.app.feature.trips.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobeHealthTest {
    @Test fun neverFailedIsUsable() = assertTrue(GlobeHealth.usable(0L, 1_000L))

    @Test fun recentFailureHidesTheGlobe() = assertFalse(GlobeHealth.usable(1_000L, 1_000L + 60_000L))

    @Test fun oldFailureIsForgotten() = assertTrue(GlobeHealth.usable(1_000L, 1_000L + GlobeHealth.PAUSE_MILLIS))
}
