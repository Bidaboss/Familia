package fr.familia.app.core.place

import fr.familia.app.core.model.TripPlaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Récit 103, règle 20: the OpenStreetMap question and answer of the search by kind. */
class PlaceSearchTest {
    // Ajaccio.
    private val lat = 41.9192
    private val lng = 8.7386

    @Test
    fun `each kind asks for its own tags, named, around the destination`() {
        val lodging = placeSearchQuery(TripPlaceKind.Lodging, lat, lng, "")
        assertTrue(lodging.contains("\"tourism\"~\"^(hotel|"))
        assertTrue(lodging.contains("[\"name\"](around:10000,41.91920,8.73860)"))
        assertFalse(lodging.contains("amenity"))
        assertTrue(placeSearchQuery(TripPlaceKind.Restaurant, lat, lng, "").contains("\"amenity\"~\"^(restaurant|"))
        val spot = placeSearchQuery(TripPlaceKind.Spot, lat, lng, "")
        assertEquals(4, Regex("nwr\\[").findAll(spot).count())
        assertTrue(spot.contains("\"natural\"=\"beach\""))
    }

    @Test
    fun `the typed words filter the name, any case, and can never change the question`() {
        assertTrue(placeSearchQuery(TripPlaceKind.Restaurant, lat, lng, "  Chez   Paulo ").contains("[\"name\"~\"Chez Paulo\",i]"))
        assertEquals("a\\\\.b\\\\(c\\\\)", searchWords("a.b(c)"))
        assertEquals("x];out;", searchWords("x\"];out;").replace("\\\\", ""))
        assertFalse(searchWords("\"\\").contains("\""))
        assertEquals(60, searchWords("a".repeat(80)).length)
    }

    @Test
    fun `the places come nearest first, named, without twins, with their address`() {
        val answer = """
            {"elements":[
              {"type":"node","lat":41.9300,"lon":8.7386,"tags":{"name":"Hôtel Fesch","tourism":"hotel","addr:housenumber":"7","addr:street":"rue Fesch","addr:city":"Ajaccio"}},
              {"type":"way","center":{"lat":41.9195,"lon":8.7386},"tags":{"name":"Camping Barbicaja","tourism":"camp_site"}},
              {"type":"node","lat":41.9200,"lon":8.7386,"tags":{"tourism":"hotel"}},
              {"type":"node","lat":41.9400,"lon":8.7386,"tags":{"name":"hôtel fesch","tourism":"hotel"}}
            ]}
        """.trimIndent()
        val places = parseFoundPlaces(answer, lat, lng, TripPlaceKind.Lodging)!!
        assertEquals(listOf("Camping Barbicaja", "Hôtel Fesch"), places.map { it.name })
        assertEquals(listOf("Camping", "Hôtel"), places.map { it.genre })
        assertEquals("7 rue Fesch, Ajaccio", places[1].address)
        assertNull(places[0].address)
        assertTrue(places[0].meters < places[1].meters)
    }

    @Test
    fun `an answer that cannot be read is no answer, an empty one is none`() {
        assertNull(parseFoundPlaces("<html>busy</html>", lat, lng, TripPlaceKind.Spot))
        assertEquals(emptyList<FoundPlace>(), parseFoundPlaces("{\"elements\":[]}", lat, lng, TripPlaceKind.Spot))
    }

    @Test
    fun `a place without its own tag takes the name of its kind`() {
        assertEquals("Lieu à voir", foundGenre(null, TripPlaceKind.Spot))
        assertEquals("Plage", foundGenre("beach", TripPlaceKind.Spot))
    }
}
