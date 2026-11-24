package fr.familia.app.feature.trips.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import fr.familia.app.feature.trips.map.GeoPoint
import org.junit.Test

class TripsGlobeTest {
    @Test fun turnsFourDegreesPerSecondEastward() = assertEquals(14.0, turnedLongitude(10.0, 1_000_000_000L), 0.0001)

    @Test fun longitudeWrapsAround() = assertEquals(-178.0, turnedLongitude(178.0, 1_000_000_000L), 0.0001)

    @Test fun turnsOnlyWhenSeenAndFreeToMove() {
        assertTrue(globeTurns(started = true, stoppedByMember = false, powerSave = false, animationsOff = false))
        assertFalse(globeTurns(started = false, stoppedByMember = false, powerSave = false, animationsOff = false))
        assertFalse(globeTurns(started = true, stoppedByMember = true, powerSave = false, animationsOff = false))
        assertFalse(globeTurns(started = true, stoppedByMember = false, powerSave = true, animationsOff = false))
        assertFalse(globeTurns(started = true, stoppedByMember = false, powerSave = false, animationsOff = true))
    }

    @Test fun tripsAtTheSamePlaceShareOneDot() {
        val palmyre = GeoPoint(45.6886, -1.1874)
        val spots = globeSpots(
            listOf(
                GlobeMarker(palmyre, highlighted = true, tripId = "a"),
                GlobeMarker(GeoPoint(43.7696, 11.2558), highlighted = false, tripId = "b"),
                GlobeMarker(GeoPoint(45.6889, -1.1871), highlighted = false, tripId = "c"),
            ),
        )
        assertEquals(2, spots.size)
        assertEquals(listOf("a", "c"), spots[0].tripIds)
        assertTrue(spots[0].highlighted)
        assertEquals(listOf("b"), spots[1].tripIds)
        assertFalse(spots[1].highlighted)
    }

    @Test fun aTouchOpensTheFirstTripOfTheDot() {
        assertEquals("a", spotTrip(GlobeSpot(GeoPoint(0.0, 0.0), true, listOf("a", "c"))))
        assertEquals(null, spotTrip(null))
    }

    // E-34: once its style is there the globe stays, even while its imagery still comes.
    @Test fun theGlobeGivesUpOnlyWithoutItsStyle() {
        assertEquals(true, globeGivesUp(styleLoaded = false))
        assertEquals(false, globeGivesUp(styleLoaded = true))
    }

    @Test fun theGlobeTurnsAgainAFewSecondsAfterATouch() = assertEquals(3_000L, GLOBE_RESUME_AFTER_TOUCH_MS)
}
