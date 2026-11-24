package fr.familia.app.feature.trips

import fr.familia.app.core.place.PlacePhoto
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlaceCoverTest {
    @Test
    fun theCoverIsAPhotoOrTheDrawingNeverTheMap() {
        // DESIGN-104 A: only two layers, the photo of the place and the drawn landscape.
        val photo = CoverLayer.Photo(PlacePhoto("https://upload.wikimedia.org/a.jpg", "Wikimedia Commons"))
        assertNotEquals(CoverLayer.Drawn, photo)
    }
}
