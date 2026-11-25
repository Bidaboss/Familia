package fr.familia.app.feature.trips.list

import fr.familia.app.feature.trips.map.GlobeLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Retours du 25/11 matin: zoom to look around the world; only image tiles count as imagery. */
class GlobeReliefTest {
    @Test
    fun `zoomed in to look around, the globe holds still`() {
        assertTrue(globeTurnsAt(zoom = 1.1, globeZoom = 1.1))
        assertTrue(globeTurnsAt(zoom = 2.0, globeZoom = 1.1))
        assertFalse(globeTurnsAt(zoom = 2.2, globeZoom = 1.1))
        assertFalse(globeTurnsAt(zoom = 9.0, globeZoom = 1.1))
    }

    @Test
    fun `the fingers zoom from a small globe down to a valley`() {
        assertTrue(GLOBE_MIN_ZOOM < GlobeLook.Standard.listZoom)
        assertTrue(GLOBE_MAX_ZOOM >= 12.0)
    }

    @Test
    fun `only image tiles count as imagery, not the elevation nor the vector map`() {
        val images = imagerySources(
            listOf("mapbox://mapbox.satellite" to "raster", "composite" to "vector", "globe-dem" to "raster-dem", "trips" to "geojson"),
        )
        assertEquals(setOf("mapbox://mapbox.satellite"), images)
        assertFalse("globe-dem" in images)
    }
}
