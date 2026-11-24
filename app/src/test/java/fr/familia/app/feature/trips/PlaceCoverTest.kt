package fr.familia.app.feature.trips

import fr.familia.app.FamiliaApplication
import fr.familia.app.core.place.PlacePhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceCoverTest {
    @Test
    fun theCoverIsAPhotoOrTheDrawingNeverTheMap() {
        // DESIGN-104 A: only two layers, the photo of the place and the drawn landscape.
        val photo = CoverLayer.Photo(PlacePhoto("https://upload.wikimedia.org/a.jpg", "Wikimedia Commons"))
        assertNotEquals(CoverLayer.Drawn, photo)
    }

    @Test
    fun aFailedImageGivesTheNextPhotoOnceThenTheDrawing() {
        // Retour E-47: the next nearby photo before the drawing, never the same image again.
        val failed = "https://upload.wikimedia.org/a.jpg"
        val next = PlacePhoto("https://upload.wikimedia.org/b.jpg", "Wikimedia Commons")
        assertEquals(CoverLayer.Photo(next), nextCoverLayer(failed, next))
        assertEquals(CoverLayer.Drawn, nextCoverLayer(failed, PlacePhoto(failed, "Wikimedia Commons")))
        assertEquals(CoverLayer.Drawn, nextCoverLayer(failed, null))
    }

    @Test
    fun theImagesOfWikimediaGetTheUserAgentOfTheApp() {
        assertTrue(FamiliaApplication.isWikimedia("upload.wikimedia.org"))
        assertTrue(FamiliaApplication.isWikimedia("fr.wikipedia.org"))
        assertFalse(FamiliaApplication.isWikimedia("firebasestorage.googleapis.com"))
        assertFalse(FamiliaApplication.isWikimedia("notwikimedia.org"))
    }
}
