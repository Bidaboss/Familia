package fr.familia.app.feature.trips.edit

import fr.familia.app.core.place.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class TripDestinationTest {
    @Test
    fun `the trip takes the first part of the address as its name`() {
        assertEquals("La Palmyre", destinationName(Place(45.7, -1.2, address = "La Palmyre, Les Mathes, France")))
    }

    @Test
    fun `a name given by the member comes before the address`() {
        assertEquals("Plage des Sables", destinationName(Place(1.0, 2.0, name = "Plage des Sables", address = "Quelque part, France")))
    }

    @Test
    fun `an address without comma is kept whole`() {
        assertEquals("Ajaccio", destinationName(Place(41.9, 8.7, address = "Ajaccio")))
    }

    @Test
    fun `the name never goes over the limit of a trip title`() {
        val long = "A".repeat(MAX_TRIP_TITLE + 30)
        assertEquals(MAX_TRIP_TITLE, destinationName(Place(1.0, 2.0, address = long)).length)
    }

    @Test
    fun `a place without any text gives an empty name`() {
        assertEquals("", destinationName(Place(1.0, 2.0)))
    }
}
