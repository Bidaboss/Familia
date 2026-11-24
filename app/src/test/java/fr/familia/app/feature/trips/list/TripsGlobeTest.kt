package fr.familia.app.feature.trips.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import fr.familia.app.feature.trips.map.GeoPoint
import fr.familia.app.feature.trips.map.GlobeLook
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
        val samoure = GeoPoint(45.6886, -1.1874)
        val spots = globeSpots(
            listOf(
                GlobeMarker(samoure, highlighted = true, tripId = "a"),
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

    // E-38: the accent of the theme drawn on the globe, and one label image per place name.
    @Test fun theAccentYellowGoesToMapboxAsHex() =
        assertEquals("#F2C14E", accentHex(androidx.compose.ui.graphics.Color(0xFFF2C14E)))

    @Test fun aPlaceKeepsItsLabelAndTwoTripsThereShareIt() {
        val here = GeoPoint(43.7, 7.26)
        val spots = globeSpots(listOf(GlobeMarker(here, true, "a", "Nice"), GlobeMarker(here, false, "b", "Nice")))
        assertEquals("Nice", spots.single().name)
        assertEquals(labelImageId("Nice"), labelImageId("Nice"))
    }

    // E-48: the globe fills about 85 % of the width of a phone.
    @Test fun theListGlobeIsBiggerThanBefore() = assertEquals(1.1, GLOBE_LIST_ZOOM, 0.0)

    @Test fun theGlobeOfTheListShowsTheNaturalColoursFirst() {
        assertEquals(GlobeLook.Vivid, GlobeLook.LIST_DEFAULT)
        assertEquals(com.mapbox.maps.Style.SATELLITE, GlobeLook.Vivid.styleUri)
        assertEquals(GlobeLook.Vivid, GlobeLook.of(null))
        assertEquals(GlobeLook.Vivid, GlobeLook.of("inconnue"))
        assertEquals(GlobeLook.Soft, GlobeLook.of("Soft"))
    }

    @Test fun aLongTouchGoesThroughTheThreeVariants() {
        assertEquals(GlobeLook.Soft, GlobeLook.Vivid.next())
        assertEquals(GlobeLook.Standard, GlobeLook.Soft.next())
        assertEquals(GlobeLook.Vivid, GlobeLook.Standard.next())
        assertEquals(listOf("A", "B", "C"), GlobeLook.entries.map { it.letter })
    }
}
