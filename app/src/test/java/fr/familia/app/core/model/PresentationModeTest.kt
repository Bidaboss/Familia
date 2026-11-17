package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PresentationModeTest {

    private val now = Instant.parse("2026-11-21T11:00:00Z")

    @Test
    fun `2 h and 4 h end after that time, until off has no end`() {
        assertEquals(Instant.parse("2026-11-21T13:00:00Z"), PresentationLength.TwoHours.until(now))
        assertEquals(Instant.parse("2026-11-21T15:00:00Z"), PresentationLength.FourHours.until(now))
        assertNull(PresentationLength.UntilOff.until(now))
    }

    @Test
    fun `on until its end, or until turned off without one`() {
        assertTrue(PresentationMode(on = true, until = null).isOn(now))
        assertTrue(PresentationMode(on = true, until = now.plusSeconds(60)).isOn(now))
        assertFalse(PresentationMode(on = true, until = now).isOn(now))
        assertFalse(PresentationMode(on = false, until = null).isOn(now))
    }
}
