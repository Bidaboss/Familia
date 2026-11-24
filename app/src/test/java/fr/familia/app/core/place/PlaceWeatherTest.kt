package fr.familia.app.core.place

import fr.familia.app.feature.trips.word.weatherLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PlaceWeatherTest {
    @Test
    fun `only the point, rounded, is sent`() {
        assertEquals(
            "https://api.open-meteo.com/v1/forecast?latitude=13.76&longitude=100.50&current=temperature_2m,weather_code&timezone=auto",
            weatherUrl(13.756331, 100.501765),
        )
    }

    @Test
    fun `an answer is read, anything else gives nothing`() {
        val good = """{"timezone":"Asia/Bangkok","utc_offset_seconds":25200,"current":{"time":"2026-11-24T05:30","temperature_2m":27.4,"weather_code":2}}"""
        assertEquals(WeatherNow(27.4, 2, ZoneId.of("Asia/Bangkok")), parseWeather(good))
        assertNull(parseWeather("""{"timezone":"Nowhere/Land","current":{"temperature_2m":1,"weather_code":0}}"""))
        assertNull(parseWeather("""{"timezone":"Europe/Rome","current":{"weather_code":0}}"""))
        assertNull(parseWeather("oops"))
    }

    @Test
    fun `one line with the local time and the gap with France`() {
        val at = Instant.parse("2026-11-23T22:40:00Z")
        assertEquals("27 °C, quelques nuages · 5 h 40 sur place (+6 h)", weatherLine(WeatherNow(27.4, 2, ZoneId.of("Asia/Bangkok")), at))
        assertEquals("12 °C, couvert · 23 h 40 sur place", weatherLine(WeatherNow(12.2, 3, ZoneId.of("Europe/Rome")), at))
        assertEquals("orage", weatherWords(95))
        assertEquals("temps inconnu", weatherWords(42))
    }

    @Test
    fun `the anecdote comes from Wikipedia in French`() {
        assertEquals("https://fr.wikipedia.org/api/rest_v1/page/summary/La_Samoure", summaryUrl("La Samoure", WIKIPEDIA_HOST))
    }
}
