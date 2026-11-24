package fr.familia.app.feature.trips.step

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceNameTest {
    @Test
    fun `la rochelle takes its capitals`() {
        assertEquals("La Rochelle", placeName("La rochelle"))
        assertEquals("La Rochelle", placeName("  la   rochelle "))
    }

    @Test
    fun `the small words inside stay small`() {
        assertEquals("Saint-Jean-de-Luz", placeName("saint-jean-de-luz"))
        assertEquals("Aix-en-Provence", placeName("aix-en-provence"))
        assertEquals("Les Sables-d'Olonne", placeName("les sables-d'olonne"))
        assertEquals("L'Île-Rousse", placeName("l'île-rousse"))
    }

    @Test
    fun `a word typed with capitals stays as typed`() {
        assertEquals("McDonald's Royan", placeName("McDonald's royan"))
        assertEquals("LA", placeName("LA"))
    }

    @Test
    fun `nothing typed stays empty`() {
        assertEquals("", placeName("   "))
    }
}
