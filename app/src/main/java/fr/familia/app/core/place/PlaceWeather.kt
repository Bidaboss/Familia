package fr.familia.app.core.place

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.time.DateTimeException
import java.time.ZoneId
import java.util.Locale

/** The weather of the moment at a destination, and its time zone for the local time. */
data class WeatherNow(val temperatureC: Double, val code: Int, val zone: ZoneId)

/**
 * Weather and local time of a destination (récit 105, règle 45). Only the point of the step is
 * sent, never a name or anything about the family. Nothing is stored: read at display time.
 */
interface PlaceWeather {
    /** Null when there is no network or the answer cannot be read. */
    suspend fun now(lat: Double, lng: Double): WeatherNow?
}

/** Open-Meteo: free, no key, data under CC BY 4.0 (credited on the screen). One call gives both. */
class OpenMeteoWeather : PlaceWeather {
    override suspend fun now(lat: Double, lng: Double): WeatherNow? {
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(TIMEOUT_MS) {
                runCatching {
                    val connection = URL(weatherUrl(lat, lng)).openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 5_000
                        connection.readTimeout = 5_000
                        connection.setRequestProperty("User-Agent", USER_AGENT)
                        connection.setRequestProperty("Accept", "application/json")
                        if (connection.responseCode != 200) null else parseWeather(connection.inputStream.bufferedReader().use { it.readText() })
                    } finally {
                        connection.disconnect()
                    }
                }.getOrNull()
            }
        }
    }

    companion object {
        const val SOURCE = "Open-Meteo"
        private const val USER_AGENT = "Familia/1.0 (application familiale)"
        private const val TIMEOUT_MS = 8_000L
    }
}

/** The point rounded to 2 decimals (about 1 km): enough for the weather, no more precise than needed. */
fun weatherUrl(lat: Double, lng: Double): String =
    String.format(
        Locale.ROOT,
        "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code&timezone=auto",
        lat,
        lng,
    )

/** Reads an answer of Open-Meteo; null when the temperature, the code or the zone is missing. */
fun parseWeather(json: String): WeatherNow? = runCatching {
    val root = Json.parseToJsonElement(json).jsonObject
    val current = root["current"]?.jsonObject ?: return@runCatching null
    val temperature = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
    val code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: return@runCatching null
    val zoneName = root["timezone"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
    val zone = try {
        ZoneId.of(zoneName)
    } catch (e: DateTimeException) {
        return@runCatching null
    }
    WeatherNow(temperature, code, zone)
}.getOrNull()

/** The words of a WMO weather code, as Open-Meteo gives it. */
fun weatherWords(code: Int): String = when (code) {
    0 -> "ciel dégagé"
    1 -> "plutôt dégagé"
    2 -> "quelques nuages"
    3 -> "couvert"
    45, 48 -> "brouillard"
    51, 53, 55, 56, 57 -> "bruine"
    61, 63, 65, 66, 67 -> "pluie"
    71, 73, 75, 77 -> "neige"
    80, 81, 82 -> "averses"
    85, 86 -> "averses de neige"
    95, 96, 99 -> "orage"
    else -> "temps inconnu"
}
