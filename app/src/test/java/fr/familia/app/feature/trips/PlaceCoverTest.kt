package fr.familia.app.feature.trips

import fr.familia.app.core.place.PlacePhoto
import fr.familia.app.feature.trips.map.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceCoverTest {
    @Test
    fun theSatelliteViewNeedsAPointAndAToken() {
        assertNull(satelliteStillUrl(null, "pk.x"))
        assertNull(satelliteStillUrl(GeoPoint(45.0, -1.0), ""))
        assertEquals(
            "https://api.mapbox.com/styles/v1/mapbox/satellite-streets-v12/static/-1.2500,45.6000,10.5,0/640x400@2x?access_token=pk.x",
            satelliteStillUrl(GeoPoint(45.6, -1.25), "pk.x"),
        )
    }

    @Test
    fun aPhotoThatFailsGivesTheSatelliteThenTheDrawing() {
        val photo = CoverLayer.Photo(PlacePhoto("https://upload.wikimedia.org/a.jpg", "Wikimedia Commons"))
        assertEquals(CoverLayer.Satellite("s"), nextLayer(photo, "s"))
        assertEquals(CoverLayer.Drawn, nextLayer(photo, null))
        assertEquals(CoverLayer.Drawn, nextLayer(CoverLayer.Satellite("s"), "s"))
    }
}
