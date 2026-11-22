package fr.familia.app.feature.trips.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripMapStreetsTest {
    private val paris = GeoPoint(48.8566, 2.3522)
    private val lyon = GeoPoint(45.7640, 4.8357)
    private val marseille = GeoPoint(43.2965, 5.3698)

    @Test
    fun `no point, no frame`() {
        assertNull(streetsFrame(emptyList()))
    }

    @Test
    fun `one step sits in the middle at street zoom`() {
        val frame = streetsFrame(listOf(lyon))!!
        assertEquals(lyon.lat, frame.lat, 1e-6)
        assertEquals(lyon.lng, frame.lng, 1e-6)
        assertTrue(frame.zoom <= 15.0)
        val unit = projectStreets(listOf(lyon), frame).single()
        assertEquals(0.5f, unit.x, 0.001f)
        assertEquals(0.5f, unit.y, 0.001f)
    }

    @Test
    fun `every point is inside the margin and the north stays up`() {
        val points = listOf(paris, lyon, marseille)
        val frame = streetsFrame(points)!!
        val units = projectStreets(points, frame)
        units.forEach {
            assertTrue(it.x >= MAP_MARGIN - 0.01f && it.x <= 1 - MAP_MARGIN + 0.01f)
            assertTrue(it.y >= MAP_MARGIN - 0.01f && it.y <= 1 - MAP_MARGIN + 0.01f)
        }
        assertTrue(units[0].y < units[1].y && units[1].y < units[2].y)
        assertTrue(units[0].x < units[1].x)
    }

    @Test
    fun `the zoom is cut to two decimals and kept in range`() {
        val frame = streetsFrame(listOf(paris, marseille))!!
        assertEquals(Math.floor(frame.zoom * 100) / 100, frame.zoom, 1e-9)
        assertTrue(frame.zoom in 1.0..15.0)
    }

    @Test
    fun `the request has the centre, the zoom, the size and the token`() {
        val url = streetsUrl(StreetsFrame(45.764, 4.8357, 5.5), "pk.test")
        assertEquals(
            "https://api.mapbox.com/styles/v1/mapbox/streets-v12/static/4.83570,45.76400,5.50/576x640@2x?access_token=pk.test",
            url,
        )
        assertNotNull(url)
    }

    @Test
    fun `the streets are used only with the module, a token and a network`() {
        assertEquals(MapGround.Streets, mapGround(true, "pk.x", true))
    }
}
