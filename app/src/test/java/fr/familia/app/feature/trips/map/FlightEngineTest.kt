package fr.familia.app.feature.trips.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightEngineTest {
    private val lyon = GeoPoint(45.76, 4.83)
    private val marseille = GeoPoint(43.30, 5.37)
    private val samoure = GeoPoint(45.68, -1.17)

    private fun engine(vararg p: GeoPoint) = FlightEngine(p.toList())

    @Test
    fun `fewer than two placed steps, nothing to fly`() {
        val e = engine(lyon)
        e.start()
        assertFalse(e.canFly)
        assertEquals(FlightPhase.Overview, e.state.phase)
        assertFalse(engine().canFly)
        assertNull(engine().camera())
    }

    @Test
    fun `it leaves the globe, flies, stops on each step, then ends`() {
        val e = engine(lyon, marseille, samoure)
        assertEquals(FlightPhase.Overview, e.state.phase)
        assertEquals(FlightEngine.GLOBE_ZOOM, e.camera()!!.zoom, 0.0)
        e.start()
        assertEquals(FlightPhase.Flying(0), e.state.phase)
        assertTrue(e.state.playing)
        e.onMotionEnded()
        assertEquals(FlightPhase.Dwelling(0), e.state.phase)
        e.onDwellEnded()
        assertEquals(FlightPhase.Flying(1), e.state.phase)
        e.onMotionEnded(); e.onDwellEnded()
        e.onMotionEnded()
        assertEquals(FlightPhase.Dwelling(2), e.state.phase)
        e.onDwellEnded()
        assertTrue(e.finished)
        assertEquals(2, e.currentIndex)
    }

    @Test
    fun `pause keeps the phase, resume plays again`() {
        val e = engine(lyon, marseille)
        e.start()
        e.pause()
        assertFalse(e.state.playing)
        assertEquals(FlightPhase.Flying(0), e.state.phase)
        e.resume()
        assertTrue(e.state.playing)
    }

    @Test
    fun `next and previous move one step, and stay inside the trip`() {
        val e = engine(lyon, marseille, samoure)
        e.start()
        e.onMotionEnded()
        e.next()
        assertEquals(FlightPhase.Flying(1), e.state.phase)
        e.previous()
        assertEquals(FlightPhase.Flying(0), e.state.phase)
        e.previous()
        assertEquals(FlightPhase.Flying(0), e.state.phase)
        e.next(); e.next()
        assertEquals(FlightPhase.Flying(2), e.state.phase)
        e.next()
        assertTrue(e.finished)
    }

    @Test
    fun `skip goes to the end and replay goes back to the globe`() {
        val e = engine(lyon, marseille, samoure)
        e.skip()
        assertEquals(FlightPhase.Overview, e.state.phase)
        e.start()
        e.skip()
        assertTrue(e.finished)
        assertFalse(e.state.playing)
        e.reset()
        assertEquals(FlightState(), e.state)
    }

    @Test
    fun `the trail is drawn leg by leg`() {
        val e = engine(lyon, marseille, samoure)
        assertEquals(0, e.completedLegs())
        e.start()
        assertEquals(0, e.completedLegs())
        assertNull(e.drawingLeg())
        e.onMotionEnded(); e.onDwellEnded()
        assertEquals(FlightPhase.Flying(1), e.state.phase)
        assertEquals(0, e.drawingLeg())
        assertEquals(0, e.completedLegs())
        e.onMotionEnded()
        assertEquals(1, e.completedLegs())
        e.skip()
        assertEquals(2, e.completedLegs())
    }

    @Test
    fun `the camera is near a step close to its neighbour and wider when far`() {
        val near = engine(GeoPoint(45.76, 4.83), GeoPoint(45.77, 4.84))
        near.start()
        assertEquals(FlightEngine.ZOOM_STREET, near.camera()!!.zoom, 0.0)
        val far = engine(lyon, GeoPoint(48.86, 2.35))
        far.start()
        assertEquals(FlightEngine.ZOOM_REGION, far.camera()!!.zoom, 0.0)
    }

    @Test
    fun `the end shows the whole trip, flat`() {
        val e = engine(lyon, marseille, samoure)
        e.start()
        e.skip()
        val camera = e.camera()!!
        assertEquals(0.0, camera.pitch, 0.0)
        assertTrue(camera.zoom in 1.0..15.0)
        assertNotNull(camera.point)
    }

    @Test
    fun `a flight lasts between 2,5 and 6 seconds and the first one 4,5`() {
        assertEquals(4_500L, FlightEngine.flightMillis(null, lyon))
        assertEquals(FlightEngine.MIN_FLY_MILLIS, FlightEngine.flightMillis(lyon, lyon))
        assertEquals(FlightEngine.MAX_FLY_MILLIS, FlightEngine.flightMillis(lyon, GeoPoint(-33.86, 151.2)))
    }
}
