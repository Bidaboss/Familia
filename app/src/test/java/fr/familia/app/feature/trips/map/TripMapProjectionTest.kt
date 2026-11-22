package fr.familia.app.feature.trips.map

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.TripStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.hypot

class TripMapProjectionTest {
    private val ajaccio = GeoPoint(41.92, 8.74)
    private val bastia = GeoPoint(42.70, 9.45)
    private val porto = GeoPoint(42.27, 8.70)

    @Test
    fun `no point gives no point`() {
        assertTrue(projectToUnit(emptyList()).isEmpty())
    }

    @Test
    fun `a single point sits in the middle`() {
        val p = projectToUnit(listOf(ajaccio)).single()
        assertEquals(0.5f, p.x, 0.001f)
        assertEquals(0.5f, p.y, 0.001f)
    }

    @Test
    fun `north is up and east is right`() {
        val (south, north) = projectToUnit(listOf(ajaccio, bastia))
        assertTrue("bastia is north of ajaccio", north.y < south.y)
        assertTrue("bastia is east of ajaccio", north.x > south.x)
    }

    @Test
    fun `every point shows with the margin around it`() {
        val all = projectToUnit(listOf(ajaccio, bastia, porto))
        all.forEach {
            assertTrue(it.x >= MAP_MARGIN - 0.001f && it.x <= 1 - MAP_MARGIN + 0.001f)
            assertTrue(it.y >= MAP_MARGIN - 0.001f && it.y <= 1 - MAP_MARGIN + 0.001f)
        }
    }

    @Test
    fun `the largest spread touches the margin and the points are centered`() {
        val all = projectToUnit(listOf(ajaccio, bastia))
        val minY = all.minOf { it.y }
        val maxY = all.maxOf { it.y }
        // Higher than wide on the ground, so the height is what fits.
        assertEquals(MAP_MARGIN, minY, 0.01f)
        assertEquals(1 - MAP_MARGIN, maxY, 0.01f)
        assertEquals(0.5f, (minY + maxY) / 2, 0.01f)
    }

    @Test
    fun `two steps in the same town stay close instead of filling the map`() {
        val (a, b) = projectToUnit(listOf(GeoPoint(48.8566, 2.3522), GeoPoint(48.8606, 2.3376)))
        assertTrue(hypot(a.x - b.x, a.y - b.y) < 0.2f)
    }

    @Test
    fun `identical points do not break the projection`() {
        val (a, b) = projectToUnit(listOf(ajaccio, ajaccio))
        assertEquals(a, b)
        assertEquals(0.5f, a.x, 0.001f)
    }

    @Test
    fun `a wider map spreads the points sideways`() {
        val east = listOf(GeoPoint(45.0, 0.0), GeoPoint(45.0, 20.0))
        val narrow = projectToUnit(east, aspect = 0.5f)
        val wide = projectToUnit(east, aspect = 2f)
        // Compared as a share of the height: the wide map shows more of the width.
        assertTrue((wide[1].x - wide[0].x) * 2f > (narrow[1].x - narrow[0].x) * 0.5f)
    }

    @Test
    fun `the date line and the poles do not give impossible points`() {
        val all = projectToUnit(listOf(GeoPoint(89.9, 179.9), GeoPoint(-89.9, -179.9)))
        all.forEach {
            assertTrue(it.x in 0f..1f)
            assertTrue(it.y in 0f..1f)
        }
    }

    private fun step(id: String, lat: Double? = null, lng: Double? = null) =
        TripStep(id, StepType.Car, "Lieu $id", LocalDate.of(2027, 7, 10), null, null, null, null, lat = lat, lng = lng)

    @Test
    fun `a step without a point keeps its rank but is not on the map`() {
        val stops = mapStops(listOf(step("a", 41.92, 8.74), step("b"), step("c", 42.70, 9.45)))
        assertEquals(listOf(1, 2, 3), stops.map { it.number })
        assertTrue(stops.all { it.total == 3 })
        assertNull(stops[1].point)
        assertTrue(stops[0].point != null && stops[2].point != null)
    }

    @Test
    fun `no step placed means no point at all`() {
        assertTrue(mapStops(listOf(step("a"), step("b"))).all { it.point == null })
    }
}
