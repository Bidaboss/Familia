package fr.familia.app.core.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyPlacesTest {
    // La Samoure.
    private val lat = 45.6350
    private val lng = -1.1850

    private val answer = """
        {"elements":[
          {"type":"node","lat":45.6390,"lon":-1.1850,"tags":{"name":"Le Cabanon","amenity":"restaurant"}},
          {"type":"way","center":{"lat":45.6352,"lon":-1.1851},"tags":{"name":"Chez Rose","amenity":"cafe"}},
          {"type":"node","lat":45.6360,"lon":-1.1860,"tags":{"amenity":"restaurant"}},
          {"type":"node","lat":45.6370,"lon":-1.1870,"tags":{"name":"Le Cabanon","amenity":"restaurant"}}
        ]}
    """.trimIndent()

    @Test
    fun `the places come nearest first, named, without twins`() {
        val places = parseNearby(answer, lat, lng)!!
        assertEquals(listOf("Chez Rose", "Le Cabanon"), places.map { it.name })
        assertEquals(listOf("Café", "Restaurant"), places.map { it.genre })
        assertTrue(places[0].meters < places[1].meters)
    }

    @Test
    fun `no more than 5 places are kept`() {
        val many = (1..9).joinToString(",") { """{"type":"node","lat":${45.64 + it / 1000.0},"lon":-1.185,"tags":{"name":"Lieu $it","amenity":"bar"}}""" }
        assertEquals(NEARBY_MAX, parseNearby("""{"elements":[$many]}""", lat, lng)!!.size)
    }

    @Test
    fun `an empty answer is a list without place, an unreadable one is null`() {
        assertEquals(emptyList<NearbyPlace>(), parseNearby("""{"elements":[]}""", lat, lng))
        assertNull(parseNearby("pas du json", lat, lng))
        assertNull(parseNearby("""{"remark":"runtime error"}""", lat, lng))
    }

    @Test
    fun `the distance between two points is in metres`() {
        assertEquals(0, distanceMeters(lat, lng, lat, lng))
        val d = distanceMeters(45.0, 5.0, 45.0, 5.01)
        assertTrue("$d", d in 770..800)
    }

    @Test
    fun `distances are shown in metres then in kilometres`() {
        assertEquals("10 m", distanceLabel(3))
        assertEquals("350 m", distanceLabel(348))
        assertEquals("1,2 km", distanceLabel(1_240))
    }

    @Test
    fun `the question names the kind, the radius and the point`() {
        val eat = overpassQuery(NearbyKind.Eat, lat, lng)
        assertTrue(eat.contains("restaurant|cafe"))
        assertTrue(eat.contains("around:5000,45.63500,-1.18500"))
        val see = overpassQuery(NearbyKind.See, lat, lng)
        assertTrue(see.contains("tourism") && see.contains("historic"))
    }

    @Test
    fun `the map link holds the point and the encoded name`() {
        assertEquals("geo:45.0,5.0?q=45.0,5.0(Chez%20Rose)", geoLink(NearbyPlace("Chez Rose", "Café", 45.0, 5.0, 10)))
    }
}
