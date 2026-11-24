package fr.familia.app.feature.trips.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceLinksTest {
    @Test
    fun `three links for a place, with its name and nothing else`() {
        val links = placeLinks("La Samoure")!!
        assertEquals(listOf(PlaceLinkKind.Restaurants, PlaceLinkKind.Hotels, PlaceLinkKind.Tripadvisor), links.map { it.kind })
        assertEquals("https://www.google.com/maps/search/?api=1&query=restaurants+La+Samoure", links[0].url)
        assertEquals("https://www.google.com/maps/search/?api=1&query=h%C3%B4tels+La+Samoure", links[1].url)
        assertEquals("https://www.tripadvisor.fr/Search?q=La+Samoure", links[2].url)
    }

    @Test
    fun `a blank place has no link and a long one is cut`() {
        assertNull(placeLinks("   "))
        val long = placeLinks("x".repeat(500))!!
        assertTrue(long.all { it.url.length < 400 })
        assertFalse(long.any { it.url.contains(" ") })
    }

    @Test
    fun `special characters are encoded`() {
        val url = placeLinks("Chez Mamie & Papi")!![0].url
        assertTrue(url.endsWith("query=restaurants+Chez+Mamie+%26+Papi"))
    }
}
