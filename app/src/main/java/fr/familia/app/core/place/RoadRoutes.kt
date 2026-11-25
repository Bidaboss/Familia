package fr.familia.app.core.place

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/** A drive between two points: its time and its length, by the fastest road (motorways included). */
data class RoadRoute(val minutes: Int, val km: Int)

/**
 * The time by road between two steps of a trip (retour du 25/11 soir: « en voiture, le temps de route
 * par autoroute ; à vol d'oiseau pour l'avion et le train »). Only the two points are sent, rounded to
 * about 100 m, never a name or anything about the family. Read at display time, kept in memory.
 */
interface RoadRoutes {
    /** Null without network, without road between them (an island) or when the answer cannot be read. */
    suspend fun between(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): RoadRoute?
}

/** Mapbox Directions, profile « driving » (fastest way, motorways allowed), with the public token of the maps. */
class MapboxRoadRoutes(private val token: String) : RoadRoutes {
    private val known = HashMap<String, RoadRoute?>()

    override suspend fun between(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): RoadRoute? {
        if (token.isBlank()) return null
        val address = roadRouteUrl(fromLat, fromLng, toLat, toLng, token) ?: return null
        val key = address.substringBefore('?')
        synchronized(known) { if (key in known) return known[key] }
        val found = withContext(Dispatchers.IO) {
            withTimeoutOrNull(TIMEOUT_MS) {
                runCatching {
                    val connection = URL(address).openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 5_000
                        connection.readTimeout = 5_000
                        connection.setRequestProperty("User-Agent", USER_AGENT)
                        if (connection.responseCode != 200) null else parseRoadRoute(connection.inputStream.bufferedReader().use { it.readText() })
                    } finally {
                        connection.disconnect()
                    }
                }.getOrNull()
            }
        }
        // A failure (no network) is not kept: the next opening asks again.
        if (found != null) synchronized(known) { known[key] = found }
        return found
    }

    private companion object {
        const val USER_AGENT = "Familia/1.0 (application familiale Android)"
        const val TIMEOUT_MS = 8_000L
    }
}

/** The address of the drive, points rounded to 3 decimals; null for a point out of the earth. */
fun roadRouteUrl(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, token: String): String? {
    if (listOf(fromLat, toLat).any { it !in -90.0..90.0 } || listOf(fromLng, toLng).any { it !in -180.0..180.0 }) return null
    return String.format(
        Locale.ROOT,
        "https://api.mapbox.com/directions/v5/mapbox/driving/%.3f,%.3f;%.3f,%.3f?overview=false&alternatives=false&access_token=%s",
        fromLng,
        fromLat,
        toLng,
        toLat,
        token,
    )
}

/** Reads the first route of a Mapbox Directions answer; null when there is none (« NoRoute »). */
fun parseRoadRoute(json: String): RoadRoute? = runCatching {
    val route = Json.parseToJsonElement(json).jsonObject["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@runCatching null
    val seconds = route["duration"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
    val metres = route["distance"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
    RoadRoute(minutes = (seconds / 60).roundToInt().coerceAtLeast(1), km = (metres / 1000).roundToInt())
}.getOrNull()

/** « 45 min », « 4 h », « 4 h 35 ». */
fun roadTime(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> String.format(Locale.FRANCE, "%d h %02d", minutes / 60, minutes % 60)
}
