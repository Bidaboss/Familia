package fr.familia.app.feature.trips.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
