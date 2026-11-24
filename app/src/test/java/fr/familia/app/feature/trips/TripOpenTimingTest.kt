package fr.familia.app.feature.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** C1 of #605: each step of the opening is written once per touch. */
class TripOpenTimingTest {
    @Test
    fun `a step is written once per touch`() {
        val written = HashSet<String>()
        assertTrue(TripOpenTiming.firstTime(written, "carte"))
        assertFalse(TripOpenTiming.firstTime(written, "carte"))
        assertTrue(TripOpenTiming.firstTime(written, "écran"))
        written.clear()
        assertTrue(TripOpenTiming.firstTime(written, "carte"))
    }

    @Test
    fun `no touch or an old one gives no time`() {
        assertNull(TripOpenTiming.elapsed(0L, 5_000L))
        assertNull(TripOpenTiming.elapsed(1_000L, 70_000L))
        assertEquals(400L, TripOpenTiming.elapsed(1_000L, 1_400L))
    }
}
