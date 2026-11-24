package fr.familia.app.core.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyPhotoTest {
    @Test
    fun theNearestRealPhotoIsKept() {
        val json = """
            {"query":{"pages":[
              {"pageid":3,"title":"Zoo de La Samoure","index":2,"thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Zoo.jpg/1080px-Zoo.jpg"},"original":{"source":"https://upload.wikimedia.org/wikipedia/commons/a/ab/Zoo.jpg"}},
              {"pageid":1,"title":"Les Camilles","index":1,"thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/Blason.svg/1080px-Blason.svg.png"},"original":{"source":"https://upload.wikimedia.org/wikipedia/commons/c/cd/Blason.svg"}},
              {"pageid":2,"title":"Phare de la Noé","index":3,"thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/e/ef/Phare.jpg/1080px-Phare.jpg"},"original":{"source":"https://upload.wikimedia.org/wikipedia/commons/e/ef/Phare.jpg"}}
            ]}}
        """.trimIndent()
        val image = parseNearbyImage(json)
        // The coat of arms (svg) of the nearest page is not a photo: the zoo comes next.
        assertEquals("Zoo.jpg", image?.file)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Zoo.jpg/${PHOTO_WIDTH}px-Zoo.jpg", image?.url)
    }

    @Test
    fun theFailedImageIsSkippedForTheNextOne() {
        val json = """
            {"query":{"pages":[
              {"pageid":3,"title":"Zoo de La Samoure","index":1,"thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Zoo.jpg/1080px-Zoo.jpg"},"original":{"source":"https://upload.wikimedia.org/wikipedia/commons/a/ab/Zoo.jpg"}},
              {"pageid":2,"title":"Phare de la Noé","index":2,"thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/e/ef/Phare.jpg/1080px-Phare.jpg"},"original":{"source":"https://upload.wikimedia.org/wikipedia/commons/e/ef/Phare.jpg"}}
            ]}}
        """.trimIndent()
        val failed = parseNearbyImage(json)!!.url
        // Retour E-47: the image that could not be shown is not offered again, the lighthouse comes next.
        assertEquals("Phare.jpg", parseNearbyImage(json, except = failed)?.file)
        val zooAlone = json.lines().filterNot { "Phare" in it }.joinToString("\n").replace("}},\n]", "}}\n]")
        assertNull(parseNearbyImage(zooAlone, except = failed))
    }

    @Test
    fun theWidthIsOneThatWikimediaServes() {
        // Retour E-47: Commons refuses the widths outside its standard list.
        assertTrue(PHOTO_WIDTH in WIKIMEDIA_WIDTHS)
    }

    @Test
    fun noPageWithAPhoto() {
        assertNull(parseNearbyImage("""{"query":{"pages":[{"pageid":1,"title":"Dune","index":1}]}}"""))
        assertNull(parseNearbyImage("""{"batchcomplete":true}"""))
        assertNull(parseNearbyImage("pas du json"))
    }

    @Test
    fun theAddressAsksAround10Km() {
        val url = nearbyPhotosUrl(45.6912, -1.1834)
        assertTrue(url.startsWith("https://fr.wikipedia.org/w/api.php?"))
        assertTrue("ggscoord=45.691%7C-1.183" in url)
        assertTrue("ggsradius=10000" in url)
    }
}
