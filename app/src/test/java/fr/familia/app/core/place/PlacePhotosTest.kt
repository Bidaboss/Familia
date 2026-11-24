package fr.familia.app.core.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlacePhotosTest {
    private val summary = """
        {"type":"standard","title":"La Rochelle",
         "thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Vieux_Port_La_Rochelle.jpg/320px-Vieux_Port_La_Rochelle.jpg"},
         "originalimage":{"source":"https://upload.wikimedia.org/wikipedia/commons/a/ab/Vieux_Port_La_Rochelle.jpg"}}
    """.trimIndent()

    @Test
    fun theImageOfASummaryIsAskedWideWithItsFile() {
        val image = parseSummaryImage(summary)!!
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Vieux_Port_La_Rochelle.jpg/${PHOTO_WIDTH}px-Vieux_Port_La_Rochelle.jpg", image.url)
        assertEquals("Vieux_Port_La_Rochelle.jpg", image.file)
    }

    @Test
    fun aDisambiguationADrawingOrNoImageGiveNothing() {
        assertNull(parseSummaryImage("""{"type":"disambiguation","thumbnail":{"source":"https://upload.wikimedia.org/x/320px-a.jpg"}}"""))
        assertNull(parseSummaryImage("""{"type":"standard","title":"Paris"}"""))
        assertNull(
            parseSummaryImage(
                """{"type":"standard","thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/1/12/Blason.svg/320px-Blason.svg.png"},"originalimage":{"source":"https://upload.wikimedia.org/wikipedia/commons/1/12/Blason.svg"}}""",
            ),
        )
        assertNull(parseSummaryImage("pas du json"))
    }

    @Test
    fun theCreditSaysTheAuthorAndTheLicenceWithoutHtml() {
        val info = """{"query":{"pages":[{"imageinfo":[{"extmetadata":{
            "Artist":{"value":"<a href=\"//commons.wikimedia.org/wiki/User:Jean\">Jean&nbsp;Dupont</a>"},
            "LicenseShortName":{"value":"CC BY-SA 4.0"}}}]}]}}"""
        assertEquals("Jean Dupont, CC BY-SA 4.0, Wikimedia Commons", parseCredit(info))
        assertEquals("Wikimedia Commons", parseCredit("""{"query":{"pages":[{"imageinfo":[{"extmetadata":{}}]}]}}"""))
        assertNull(parseCredit("""{"query":{"pages":[{"missing":true}]}}"""))
    }

    @Test
    fun addressesAreEncoded() {
        assertEquals("https://fr.wikipedia.org/api/rest_v1/page/summary/La_Samoure", photoSummaryUrl(" La Samoure "))
        assertEquals(
            "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2&prop=imageinfo&iiprop=extmetadata&titles=File%3APort+%C3%A9t%C3%A9.jpg",
            commonsInfoUrl("Port été.jpg"),
        )
    }
}
