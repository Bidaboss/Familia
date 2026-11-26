package fr.familia.app.feature.trips.carnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** « Carnet de baroudeur » (récit 107): what it counts, on fictional trips. */
class CarnetStatsTest {
    private val lisbon = CarnetStop("Lisbonne", 38.72, -9.14, "PT")
    private val porto = CarnetStop("Porto", 41.15, -8.61, "PT")
    private val marrakech = CarnetStop("Marrakech", 31.63, -8.0, "MA")
    private val rome = CarnetStop("Rome", 41.9, 12.5, "IT")
    private val home = 45.76 to 4.84

    @Test
    fun `an empty carnet holds France, at home, and nothing else`() {
        val carnet = carnetOf(emptyList(), home)
        assertEquals(listOf(Stamp("FR", null)), carnet.stamps)
        assertEquals(CarnetRank.Hiker, carnet.rank)
        assertEquals(CarnetRank.Explorer, carnet.next)
        assertEquals(setOf(Continent.Europe), carnet.continents)
        assertNull(carnet.farthest)
        assertFalse(carnet.trophies.first().second)
    }

    @Test
    fun `each country counts once, stamped with its first trip`() {
        val carnet = carnetOf(
            listOf(
                CarnetTrip(LocalDate.of(2025, 8, 1), 8, listOf(rome)),
                CarnetTrip(LocalDate.of(2024, 5, 3), 4, listOf(lisbon, porto)),
                CarnetTrip(LocalDate.of(2026, 4, 10), 5, listOf(porto)),
            ),
            home,
        )
        assertEquals(listOf("FR", "PT", "IT"), carnet.stamps.map { it.code })
        assertEquals(LocalDate.of(2024, 5, 3), carnet.stamps[1].first)
        assertEquals(3, carnet.countries)
        assertEquals(CarnetRank.Explorer, carnet.rank)
        assertEquals(CarnetRank.GlobeTrotter, carnet.next)
        assertEquals(17, carnet.days)
        assertEquals(3, carnet.trips)
        assertEquals(3, carnet.destinations)
    }

    @Test
    fun `kilometres join the placed steps, never a hand-added destination`() {
        val trip = CarnetTrip(LocalDate.of(2024, 5, 3), 4, listOf(lisbon, porto))
        val manual = CarnetTrip(LocalDate.of(2019, 5, 3), 6, listOf(marrakech, rome), manual = true)
        assertTrue(trip.km in 270..290)
        assertEquals(0, manual.km)
        val carnet = carnetOf(listOf(trip, manual), home)
        assertEquals(trip.km, carnet.km)
        assertEquals(2, carnet.tripPoints.size)
        assertEquals(2, carnet.manualPoints.size)
    }

    @Test
    fun `two continents and the farthest place from home`() {
        val carnet = carnetOf(listOf(CarnetTrip(LocalDate.of(2026, 4, 10), 5, listOf(lisbon, marrakech), photos = 64)), home)
        assertEquals(setOf(Continent.Europe, Continent.Africa), carnet.continents)
        assertEquals("Marrakech", carnet.farthest?.name)
        assertTrue(carnet.farthest!!.km in 1_850..1_990)
        assertEquals(64, carnet.photos)
        val won = carnet.trophies.filter { it.second }.map { it.first }
        assertEquals(listOf(Trophy.FirstTrip, Trophy.ThreeCountries, Trophy.TwoContinents), won)
    }

    @Test
    fun `without a home point there is no farthest place`() {
        assertNull(carnetOf(listOf(CarnetTrip(LocalDate.of(2026, 4, 10), 5, listOf(marrakech))), null).farthest)
    }

    @Test
    fun `a step the phone could not place in a country counts as a destination only`() {
        val carnet = carnetOf(listOf(CarnetTrip(LocalDate.of(2026, 4, 10), 2, listOf(CarnetStop("Quelque part", 10.0, 10.0, null)))), home)
        assertEquals(1, carnet.countries)
        assertEquals(1, carnet.destinations)
    }

    @Test
    fun `twelve countries make a great traveller, the last rank`() {
        val codes = listOf("PT", "ES", "IT", "DE", "BE", "NL", "GB", "IE", "GR", "HR", "AT")
        val carnet = carnetOf(listOf(CarnetTrip(LocalDate.of(2020, 1, 1), 30, codes.map { CarnetStop(it, 45.0, 5.0, it) })), null)
        assertEquals(12, carnet.countries)
        assertEquals(CarnetRank.GreatTraveller, carnet.rank)
        assertNull(carnet.next)
        assertEquals(6, carnet.worldPercent)
    }

    @Test
    fun `continents, flags and names of the countries`() {
        assertEquals(Continent.Asia, continentOf("tr"))
        assertEquals(Continent.America, continentOf("US"))
        assertEquals(Continent.Oceania, continentOf("NZ"))
        assertNull(continentOf("AQ"))
        assertEquals("🇵🇹", flagOf("pt"))
        assertEquals("", flagOf("P"))
        assertEquals("Portugal", countryName("PT"))
    }

    private fun place(name: String, date: LocalDate, days: Int) =
        fr.familia.app.core.model.TravelPlace(name, name, null, "GR", 37.0, 25.0, date, days)

    @Test
    fun `hand-added destinations that follow each other make one trip`() {
        val d = LocalDate.of(2024, 6, 1)
        // A week in the islands added one destination at a time, then another trip a month later.
        val places = listOf(
            place("Samoure", d, 2),
            place("Camilles", d.plusDays(2), 1),
            place("Noé", d.plusDays(3), 3),
            place("Noé bis", d.plusDays(4), 1),
            place("Lyon", d.plusDays(30), 2),
        )
        val trips = manualTrips(places)
        assertEquals(2, trips.size)
        assertEquals(listOf("Samoure", "Camilles", "Noé", "Noé bis"), trips[0].stops.map { it.name })
        // From the 1st to the 6th: six days, not the sum of each destination.
        assertEquals(6, trips[0].days)
        assertEquals(d, trips[0].start)
        assertTrue(trips.all { it.manual })
        assertEquals(2, carnetOf(trips, null).trips)
        assertEquals(8, carnetOf(trips, null).days)
        // A day without a destination between two: two trips.
        assertEquals(2, manualTrips(listOf(place("A", d, 1), place("B", d.plusDays(2), 1))).size)
        assertEquals(emptyList<CarnetTrip>(), manualTrips(emptyList()))
    }
}
