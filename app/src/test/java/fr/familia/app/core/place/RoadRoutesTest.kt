package fr.familia.app.core.place

import fr.familia.app.core.model.StepType
import fr.familia.app.feature.trips.detail.goesByRoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadRoutesTest {
    @Test
    fun readsTheFirstRoute() {
        val json = """{"routes":[{"duration":16500.4,"distance":452300.0,"legs":[]}],"code":"Ok"}"""
        assertEquals(RoadRoute(minutes = 275, km = 452), parseRoadRoute(json))
    }

    @Test
    fun noRoadGivesNothing() {
        assertNull(parseRoadRoute("""{"routes":[],"code":"NoRoute"}"""))
        assertNull(parseRoadRoute("pas du json"))
    }

    @Test
    fun theAddressSendsOnlyTwoRoundedPoints() {
        val url = roadRouteUrl(45.764043, 4.835659, 45.7, -1.1, "pk.test")!!
        assertTrue(url.startsWith("https://api.mapbox.com/directions/v5/mapbox/driving/4.836,45.764;-1.100,45.700?"))
        assertNull(roadRouteUrl(95.0, 0.0, 0.0, 0.0, "pk.test"))
    }

    @Test
    fun timeReadsAsInFrench() {
        assertEquals("45 min", roadTime(45))
        assertEquals("4 h", roadTime(240))
        assertEquals("4 h 05", roadTime(245))
    }

    @Test
    fun onlyCarAndBusGoByRoad() {
        assertTrue(goesByRoad(StepType.Car))
        assertTrue(goesByRoad(StepType.Bus))
        assertFalse(goesByRoad(StepType.Flight))
        assertFalse(goesByRoad(StepType.Train))
    }
}
