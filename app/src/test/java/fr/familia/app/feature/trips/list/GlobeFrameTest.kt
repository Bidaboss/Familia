package fr.familia.app.feature.trips.list

import fr.familia.app.feature.trips.map.isWritingOrLine
import fr.familia.app.feature.trips.map.wholeGlobeZoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow

class GlobeFrameTest {
    private fun across(zoom: Double) = 512.0 * 2.0.pow(zoom) / PI

    @Test
    fun `E-65 the globe keeps the zoom of its variant when it fits above the sheet`() {
        // A phone of 400 × 860 dp, the sheet low (22 %): room for the globe of 1.1.
        assertEquals(1.1, globeFrame(400.0, 860.0, 860.0 * 0.22, 1.1), 1e-9)
    }

    @Test
    fun `E-65 the globe gets smaller to stay whole above a high sheet, 16 dp from the edges`() {
        val zoom = globeFrame(400.0, 700.0, 700.0 * 0.48, 1.1)
        val visible = 700.0 - 700.0 * 0.48
        assertTrue(zoom < 1.1)
        assertEquals(visible - 32.0, across(zoom), 0.5)
        assertEquals(wholeGlobeZoom(400.0, visible), zoom, 1e-9)
    }

    @Test
    fun `E-66 only the names and the lines leave the globe, never the imagery`() {
        assertTrue(isWritingOrLine("symbol"))
        assertTrue(isWritingOrLine("line"))
        assertFalse(isWritingOrLine("raster"))
        assertFalse(isWritingOrLine("background"))
        assertFalse(isWritingOrLine("fill"))
    }
}
