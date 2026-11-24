package fr.familia.app.feature.trips.map

import com.mapbox.maps.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow

class TripMapLookTest {
    @Test
    fun theSatelliteLookShowsTheImageryInItsOwnColours() {
        // E-49: the classic Satellite Streets, not Standard Satellite and its neutral background.
        assertEquals(Style.SATELLITE_STREETS, TRIP_SATELLITE_STYLE)
    }

    @Test
    fun aRefusedTileIsWrittenWithItsSourceAndNoToken() {
        // E-49: to know whether the imagery is refused (token, quota, network).
        val line = tileErrorLine("SOURCE", "mapbox.satellite", "401 https://api.mapbox.com/v4?access_token=pk.secret")!!
        assertTrue(line.startsWith("SOURCE mapbox.satellite : 401"))
        assertFalse("pk.secret" in line)
        assertEquals("TILE ? : 403", tileErrorLine("TILE", null, "403"))
        // The style itself is a failure of the map, said elsewhere.
        assertNull(tileErrorLine("STYLE", null, "401"))
    }

    @Test
    fun zoomedOutTheWholeGlobeStaysAboveTheSheet() {
        // E-53: a phone 360 dp wide, 720 dp of map, 400 dp of sheet: 320 dp above it.
        val zoom = wholeGlobeZoom(360.0, 320.0)
        val across = 512.0 * 2.0.pow(zoom) / PI
        assertEquals(320.0 - 2 * GLOBE_MARGIN_DP, across, 0.5)
        // A frame too small still gives a globe, never a negative zoom.
        assertEquals(0.0, wholeGlobeZoom(40.0, 40.0), 0.0)
    }
}
