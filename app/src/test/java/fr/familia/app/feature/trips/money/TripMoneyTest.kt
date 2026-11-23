package fr.familia.app.feature.trips.money

import fr.familia.app.core.money.Currencies
import fr.familia.app.core.money.ExchangeRates
import fr.familia.app.core.money.RatesTable
import fr.familia.app.core.money.parseRates
import fr.familia.app.feature.trips.FakeTrips
import fr.familia.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TripMoneyTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the euro and 42 other currencies, each once`() {
        assertEquals(43, Currencies.ALL.size)
        assertEquals(43, Currencies.ALL.map { it.code }.toSet().size)
        assertTrue(Currencies.ALL.none { it.code == "HRK" })
    }

    @Test
    fun `search by code or by name, without accents`() {
        assertEquals(listOf("THB"), Currencies.search("thb").map { it.code })
        assertTrue(Currencies.search("baht").any { it.code == "THB" })
        assertTrue(Currencies.search("neo-zelandais").any { it.code == "NZD" })
        assertEquals(Currencies.ALL, Currencies.search("  "))
        assertTrue(Currencies.search("zzz").isEmpty())
    }

    @Test
    fun `an unknown code is the euro, and the countries give their currency`() {
        assertEquals("EUR", Currencies.of(null).code)
        assertEquals("EUR", Currencies.of("XYZ").code)
        assertEquals("THB", Currencies.of("thb").code)
        assertEquals(listOf("THB", "MAD"), Currencies.ofCountries(listOf("TH", "ES", "MA", "TH")).map { it.code })
    }

    @Test
    fun `an answer of the source is read with its date, anything else gives no rate`() {
        val good = """{"result":"success","base_code":"EUR","time_last_update_unix":1795392000,"rates":{"EUR":1,"THB":37.52,"MAD":10.81,"XXX":2,"USD":-1}}"""
        val table = parseRates(good)!!
        assertEquals(37.52, table.perEuro("THB")!!, 0.0001)
        assertEquals(1.0, table.perEuro("EUR")!!, 0.0)
        assertNull(table.perEuro("USD"))
        assertNull(table.perEuro("XXX"))
        assertEquals(LocalDate.of(2026, 11, 23), table.date)
        assertNull(parseRates("""{"result":"error","error-type":"unsupported-code"}"""))
        assertNull(parseRates("""{"result":"success","base_code":"USD","time_last_update_unix":1,"rates":{"THB":33}}"""))
        assertNull(parseRates("not json"))
    }

    @Test
    fun `amounts typed in French convert both ways`() {
        assertEquals(1200.5, parseAmount("1 200,5")!!, 0.0)
        assertEquals(12.5, parseAmount("12.5")!!, 0.0)
        assertNull(parseAmount("abc"))
        assertNull(parseAmount("-3"))
        assertEquals(10.0, toEuro(375.2, 37.52)!!, 0.0001)
        assertEquals(375.2, fromEuro(10.0, 37.52)!!, 0.0001)
        assertNull(toEuro(null, 37.52))
        assertNull(toEuro(10.0, null))
    }

    @Test
    fun `the rates are read once, and none means unavailable, never a guess`() {
        val table = RatesTable(mapOf("THB" to 37.52), LocalDate.of(2026, 11, 23))
        val ready = TripMoneyViewModel(FakeTrips(), object : ExchangeRates { override suspend fun latest() = table }, "t1")
        assertEquals(RatesState.Ready(table), ready.rates.value)
        val none = TripMoneyViewModel(FakeTrips(), object : ExchangeRates { override suspend fun latest(): RatesTable? = null }, "t1")
        assertEquals(RatesState.Unavailable, none.rates.value)
        assertEquals(RatesState.Unavailable, TripMoneyViewModel(FakeTrips(), null, "t1").rates.value)
    }

    @Test
    fun `the euro takes the currency back`() {
        val trips = FakeTrips()
        val viewModel = TripMoneyViewModel(trips, null, "t1")
        viewModel.setCurrency("THB")
        viewModel.setCurrency("EUR")
        assertEquals(listOf("t1" to "THB", "t1" to null), trips.currencies)
    }
}
