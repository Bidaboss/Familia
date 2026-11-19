package fr.familia.app.core.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading the links a member pastes, and the links handed to Maps (récit 33). Fictional places. */
class PlaceTest {

    @Test
    fun `bare coordinates are read, nonsense is not`() {
        assertEquals(PastedPlace(46.49, -1.79), parsePastedPlace(" 46.49, -1.79 "))
        assertNull(parsePastedPlace("95, 12"))
        assertNull(parsePastedPlace("0, 0"))
        assertNull(parsePastedPlace("12 rue des Lilas"))
        assertNull(parsePastedPlace(""))
    }

    @Test
    fun `a Google Maps place link gives the pin and the name`() {
        val link = "https://www.google.com/maps/place/Plage+des+Sables/@46.4912,-1.7923,17z/data=!3m1!4b1!8m2!3d46.4915!4d-1.7931"
        assertEquals(PastedPlace(46.4915, -1.7931, name = "Plage des Sables"), parsePastedPlace(link))
        assertEquals(PastedPlace(46.5, -1.8), parsePastedPlace("https://www.google.fr/maps/@46.5,-1.8,15z"))
    }

    @Test
    fun `query links give coordinates or an address to look up`() {
        assertEquals(PastedPlace(46.5, -1.8), parsePastedPlace("https://maps.google.com/?q=46.5,-1.8"))
        assertEquals(PastedPlace(46.5, -1.8), parsePastedPlace("https://www.google.com/maps/search/?api=1&query=46.5%2C-1.8"))
        assertEquals(PastedPlace(query = "12 rue des Lilas Nantes"), parsePastedPlace("Regarde : https://www.google.com/maps?q=12+rue+des+Lilas+Nantes"))
    }

    @Test
    fun `geo links are read with their label`() {
        assertEquals(PastedPlace(46.5, -1.8, name = "Chez Mamie"), parsePastedPlace("geo:46.5,-1.8?q=46.5,-1.8(Chez%20Mamie)"))
        assertEquals(PastedPlace(query = "12 rue des Lilas"), parsePastedPlace("geo:0,0?q=12%20rue%20des%20Lilas"))
    }

    @Test
    fun `other sites are not maps, short links are recognised`() {
        assertNull(parsePastedPlace("https://example.com/maps/@46.4,1.2"))
        assertTrue(isShortMapsLink("https://maps.app.goo.gl/AbCd123"))
        assertTrue(isShortMapsLink("Rejoins-nous https://goo.gl/maps/AbCd"))
        assertFalse(isShortMapsLink("https://www.google.com/maps/@46.5,-1.8,15z"))
        assertEquals("https://maps.app.goo.gl/AbCd123", linkIn("Ici https://maps.app.goo.gl/AbCd123"))
    }

    @Test
    fun `Maps and Waze get the pin with its name, or the address`() {
        assertEquals("geo:46.4915,-1.7931?q=46.4915,-1.7931(Plage%20des%20Sables)", directionsUri(Place(46.4915, -1.7931, name = "Plage des Sables")))
        assertEquals("geo:46.5,-1.8?q=46.5,-1.8", directionsUri(Place(46.5, -1.8)))
        assertEquals("geo:0,0?q=12%20rue%20des%20Lilas%2C%20Nantes", directionsUri(" 12 rue des Lilas, Nantes "))
        assertEquals("https://www.google.com/maps/search/?api=1&query=46.5%2C-1.8", webMapsLink(Place(46.5, -1.8)))
        assertEquals(
            "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=46.4915%2C-1.7931",
            streetViewUri(Place(46.4915, -1.7931, name = "Plage des Sables")),
        )
    }

    @Test
    fun `an event's address from a pasted link, a short link kept, a typed text as is (récit 74)`() {
        val link = "https://www.google.com/maps/place/Plage+des+Sables/@46.4912,-1.7923,17z/data=!3m1!4b1!8m2!3d46.4915!4d-1.7931"
        assertEquals("Plage des Sables", addressFromPasted(link))
        assertEquals("46.49, -1.79", addressFromPasted("geo:46.49,-1.79"))
        assertEquals("https://maps.app.goo.gl/AbCd123", addressFromPasted("Ici https://maps.app.goo.gl/AbCd123"))
        assertEquals("12 rue des Roses", addressFromPasted("12 rue des Roses"))
        assertEquals("https://maps.app.goo.gl/AbCd123", directionsUriForAddress("https://maps.app.goo.gl/AbCd123"))
        assertEquals("geo:0,0?q=12%20rue%20des%20Roses", directionsUriForAddress(" 12 rue des Roses "))
    }
}
