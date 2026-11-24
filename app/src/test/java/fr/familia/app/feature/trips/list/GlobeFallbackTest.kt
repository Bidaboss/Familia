package fr.familia.app.feature.trips.list

import com.mapbox.maps.Style
import fr.familia.app.core.place.PlacePhoto
import fr.familia.app.core.place.photoRefusalLine
import fr.familia.app.core.place.refusal
import fr.familia.app.feature.trips.coverErrorLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Retour du 24/11 soir: a black globe and no photo, silently. */
class GlobeFallbackTest {
    @Test
    fun `a satellite globe whose imagery never came takes the drawn Earth, once`() {
        assertTrue(globeFallsBack(imageryCame = false, styleReady = true, fellBack = false))
        assertFalse(globeFallsBack(imageryCame = true, styleReady = true, fellBack = false))
        assertFalse(globeFallsBack(imageryCame = false, styleReady = false, fellBack = false))
        assertFalse(globeFallsBack(imageryCame = false, styleReady = true, fellBack = true))
    }

    @Test
    fun `only the styles of imagery wait for it`() {
        assertTrue(globeNeedsImagery(Style.SATELLITE))
        assertTrue(globeNeedsImagery(Style.SATELLITE_STREETS))
        assertFalse(globeNeedsImagery(Style.STANDARD))
    }

    @Test
    fun `a refused photo says the code and the site, never the place`() {
        assertEquals("refusé 403 fr.wikipedia.org", photoRefusalLine(403, "fr.wikipedia.org"))
        assertEquals("délai dépassé", refusal(null))
        assertEquals("IOException", refusal(Result.failure(IOException("réseau"))))
        assertNull(refusal(Result.success<PlacePhoto?>(null)))
    }

    @Test
    fun `the error of an image is short and without its address`() {
        val line = coverErrorLine(IllegalStateException("HTTP 403 https://upload.wikimedia.org/x.jpg"))
        assertEquals("IllegalStateException: HTTP 403", line)
    }
}
