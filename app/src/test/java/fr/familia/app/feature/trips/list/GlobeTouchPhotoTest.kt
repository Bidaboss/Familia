package fr.familia.app.feature.trips.list

import fr.familia.app.core.place.noPhotoLine
import fr.familia.app.core.place.parseNearbyImage
import fr.familia.app.core.place.searchPhotosUrl
import fr.familia.app.core.place.titleCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Retour du 24/11 23 h 56: a globe whose images came, taken for black; a photo lost on a 404. */
class GlobeTouchPhotoTest {
    @Test
    fun `a tile without the loaded flag is imagery that came`() {
        assertTrue(imageryTile(isTile = true, loaded = null))
        assertTrue(imageryTile(isTile = true, loaded = true))
        assertFalse(imageryTile(isTile = true, loaded = false))
        assertFalse(imageryTile(isTile = false, loaded = true))
    }

    @Test
    fun `the name before its comma is tried after the whole name`() {
        assertEquals(listOf("Samoure, Charente", "Samoure"), titleCandidates(" Samoure, Charente "))
        assertEquals(listOf("Camilles"), titleCandidates("Camilles"))
        assertEquals(listOf(", Noé"), titleCandidates(", Noé"))
    }

    @Test
    fun `the search of Wikipedia asks three pages with their image`() {
        val url = searchPhotosUrl("Port de Samoure")
        assertTrue(url.startsWith("https://fr.wikipedia.org/w/api.php?"))
        assertTrue("gsrsearch=Port+de+Samoure" in url)
        assertTrue("gsrlimit=3" in url)
        assertTrue("prop=pageimages" in url)
    }

    @Test
    fun `a search answer gives the photo of its best page`() {
        val json = """{"query":{"pages":[
            {"index":2,"title":"B","thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/B.jpg/960px-B.jpg"},"original":{"source":"https://upload.wikimedia.org/wikipedia/commons/a/ab/B.jpg"}},
            {"index":1,"title":"A","thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/Port_A.jpg/960px-Port_A.jpg"},"original":{"source":"https://upload.wikimedia.org/wikipedia/commons/c/cd/Port_A.jpg"}}
        ]}}"""
        assertEquals("Port_A.jpg", parseNearbyImage(json)?.file)
    }

    @Test
    fun `no photo says where the search went, never the place`() {
        assertEquals("aucune photo : nom, recherche et 10 km autour", noPhotoLine(withPoint = true))
        assertEquals("aucune photo : nom et recherche, lieu sans position", noPhotoLine(withPoint = false))
    }
}
