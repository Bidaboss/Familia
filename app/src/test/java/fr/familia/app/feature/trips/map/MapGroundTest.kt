package fr.familia.app.feature.trips.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapGroundTest {
    @Test
    fun `the streets need the module, the token and the network`() {
        assertEquals(MapGround.Streets, mapGround(streetsOn = true, token = "pk.abc", hasNetwork = true))
    }

    @Test
    fun `without one of the three the drawn map stays`() {
        assertEquals(MapGround.Landscape, mapGround(false, "pk.abc", true))
        assertEquals(MapGround.Landscape, mapGround(true, "", true))
        assertEquals(MapGround.Landscape, mapGround(true, "  ", true))
        assertEquals(MapGround.Landscape, mapGround(true, "pk.abc", false))
    }
}
