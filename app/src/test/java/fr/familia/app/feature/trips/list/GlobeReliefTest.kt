package fr.familia.app.feature.trips.list

import fr.familia.app.feature.trips.map.GlobeLook
import fr.familia.app.feature.trips.map.imageryPop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Retour du 25/11 matin: A and B looked the same, the continents too pale, no relief. */
class GlobeReliefTest {
    @Test
    fun `B has the shaded mountains, A has none`() {
        assertEquals(0.0, GlobeLook.Vivid.relief, 0.0)
        assertTrue(GlobeLook.Soft.relief > 0.5)
        assertEquals(0.0, GlobeLook.Standard.relief, 0.0)
    }

    @Test
    fun `A and B have deeper colours, C stays as it comes`() {
        assertTrue(GlobeLook.Vivid.pop > 0.0)
        assertEquals(GlobeLook.Vivid.pop, GlobeLook.Soft.pop, 0.0)
        assertTrue(imageryPop(GlobeLook.Standard.pop).isEmpty())
    }

    @Test
    fun `the colours stay within reason`() {
        val paint = imageryPop(0.4)
        assertEquals(0.4, paint.getValue("raster-saturation"), 1e-9)
        assertEquals(0.24, paint.getValue("raster-contrast"), 1e-9)
        assertEquals(0.88, paint.getValue("raster-brightness-max"), 1e-9)
        val strong = imageryPop(5.0)
        assertEquals(0.6, strong.getValue("raster-saturation"), 1e-9)
        assertEquals(0.4, strong.getValue("raster-contrast"), 1e-9)
        assertEquals(0.8, strong.getValue("raster-brightness-max"), 1e-9)
    }
}
