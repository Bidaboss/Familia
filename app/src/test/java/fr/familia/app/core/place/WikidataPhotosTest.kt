package fr.familia.app.core.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DEV-1 (25/11): the photo of a destination through OpenStreetMap and Wikidata. Fictional places. */
class WikidataPhotosTest {
    // A point by the sea.
    private val lat = 41.9192
    private val lng = 8.7386

    @Test
    fun `the question asks named towns and sites that name their Wikidata item, around the point`() {
        val query = wikidataAroundQuery(lat, lng)
        assertTrue(query.contains("node[\"place\"~\"^(city|town|village|hamlet|suburb|island|islet|locality)$\"][\"wikidata\"][\"name\"](around:5000,41.91920,8.73860);"))
        assertTrue(query.contains("nwr[\"historic\"][\"wikidata\"][\"name\"](around:1500,41.91920,8.73860);"))
        assertTrue(query.startsWith("[out:json]"))
    }

    @Test
    fun `beyond 10 km the circles grow to 20, 30 then 40 km, and only ask towns and villages`() {
        assertEquals(listOf(20_000, 30_000, 40_000), WIKIDATA_WIDER_RADII)
        val wide = wikidataAroundQuery(lat, lng, radius = 30_000, wide = true)
        assertTrue(wide.contains("node[\"place\"~\"^(city|town|village)$\"][\"wikidata\"][\"name\"](around:30000,41.91920,8.73860);"))
        assertFalse(wide.contains("hamlet"))
        assertFalse(wide.contains("tourism"))
    }

    @Test
    fun `the item named like the destination comes first, then the towns, then the sites, nearest first`() {
        val answer = """
            {"elements":[
              {"type":"node","lat":41.9200,"lon":8.7386,"tags":{"name":"Citadelle","historic":"castle","wikidata":"Q11"}},
              {"type":"node","lat":41.9500,"lon":8.7386,"tags":{"name":"Mezzavia","place":"village","wikidata":"Q22"}},
              {"type":"node","lat":41.9300,"lon":8.7386,"tags":{"name":"Île-Rousse","place":"town","wikidata":"Q33"}},
              {"type":"node","lat":41.9195,"lon":8.7386,"tags":{"name":"Sans item","place":"suburb"}},
              {"type":"node","lat":41.9196,"lon":8.7386,"tags":{"name":"Mauvais","place":"suburb","wikidata":"pas un item"}}
            ]}
        """.trimIndent()
        assertEquals(listOf("Q33", "Q22", "Q11"), parseWikidataItems(answer, lat, lng, "ile rousse, Corse"))
        assertEquals(listOf("Q33", "Q22"), parseWikidataItems(answer, lat, lng, "Ailleurs", max = 2))
        assertEquals(emptyList<String>(), parseWikidataItems("{\"elements\":[]}", lat, lng, "Ajaccio"))
        assertNull(parseWikidataItems("<html>occupé</html>", lat, lng, "Ajaccio"))
    }

    @Test
    fun `a name matches without case, accent nor dash`() {
        assertEquals("ile rousse", foldedName("  Île-Rousse "))
        assertEquals(foldedName("L'Île Rousse"), foldedName("l ile rousse"))
    }

    @Test
    fun `the image of an item is its first photo, never a drawing`() {
        val claims = """
            {"claims":{"P18":[
              {"mainsnak":{"datavalue":{"value":"Blason.svg","type":"string"}}},
              {"mainsnak":{"datavalue":{"value":"Port de nuit.jpg","type":"string"}}}
            ]}}
        """.trimIndent()
        assertEquals("Port de nuit.jpg", parseWikidataImage(claims))
        assertNull(parseWikidataImage("{\"claims\":{}}"))
        assertNull(parseWikidataImage("pas du json"))
        assertTrue(wikidataImageUrl("Q33").endsWith("&property=P18&entity=Q33"))
        assertFalse(isPhotoFile("Carte.svg"))
    }

    @Test
    fun `the Commons file comes at the width of the phone with its credit`() {
        assertTrue(commonsPhotoUrl("Port de nuit.jpg").contains("iiurlwidth=$PHOTO_WIDTH&titles=File%3APort+de+nuit.jpg"))
        val answer = """
            {"query":{"pages":[{"imageinfo":[{
              "thumburl":"https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Port_de_nuit.jpg/960px-Port_de_nuit.jpg",
              "url":"https://upload.wikimedia.org/wikipedia/commons/a/ab/Port_de_nuit.jpg",
              "extmetadata":{"Artist":{"value":"<a href=\"x\">Camille Martin</a>"},"LicenseShortName":{"value":"CC BY-SA 4.0"}}
            }]}]}}
        """.trimIndent()
        val photo = parseCommonsPhoto(answer)!!
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Port_de_nuit.jpg/960px-Port_de_nuit.jpg", photo.url)
        assertEquals("Camille Martin, CC BY-SA 4.0, Wikimedia Commons", photo.credit)
        assertNull(parseCommonsPhoto("""{"query":{"pages":[{"imageinfo":[{"url":"https://ailleurs.example/a.jpg"}]}]}}"""))
    }
}
