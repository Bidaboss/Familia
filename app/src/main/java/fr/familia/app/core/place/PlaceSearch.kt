package fr.familia.app.core.place

import fr.familia.app.core.model.TripPlaceKind
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A place found around a destination (récit 103, règle 20): its name, its kind in French, its address when known, its point. */
data class FoundPlace(val name: String, val genre: String, val address: String?, val lat: Double, val lng: Double, val meters: Int)

/** Why OpenStreetMap gave no places (retour E-45): said in the message, so a failure on Wi-Fi is understood. */
enum class SearchFailure {
    /** The phone could not reach the server: no network, or a network that filters it. */
    Offline,

    /** Too many questions at once (code 429), or the server overloaded (502, 503, 504). */
    Busy,

    /** No answer in time. */
    Slow,

    /** Another refusal, or an answer that cannot be read. */
    Other,
}

/** What a search gives: the places (empty when none), or why there are none. */
sealed interface PlaceSearchAnswer {
    data class Found(val places: List<FoundPlace>) : PlaceSearchAnswer
    data class Failed(val cause: SearchFailure) : PlaceSearchAnswer
}

/**
 * The search of an accommodation, a restaurant or a spot around a destination (règle 20), from
 * OpenStreetMap (© les contributeurs d'OpenStreetMap). Only the point of the destination and the
 * typed words leave the phone; nothing is stored.
 */
interface PlaceSearch {
    /** Up to [PLACE_SEARCH_MAX] places within [PLACE_SEARCH_RADIUS] m, nearest first; empty when none, or why the service did not answer. */
    suspend fun search(lat: Double, lng: Double, kind: TripPlaceKind, text: String): PlaceSearchAnswer
}

/**
 * Overpass, as for « Autour de l'étape »: other servers when the first is busy. The question is
 * really stopped when a newer one replaces it (a key typed): before, each key left its question
 * running, and the main server answers 429 past two at once from the same phone (retour E-45).
 */
class OverpassPlaceSearch : PlaceSearch {
    override suspend fun search(lat: Double, lng: Double, kind: TripPlaceKind, text: String): PlaceSearchAnswer {
        val query = placeSearchQuery(kind, lat, lng, text)
        var first: SearchFailure? = null
        for (server in SERVERS) {
            val answer = withTimeoutOrNull(TIMEOUT_MS) { runInterruptible(Dispatchers.IO) { ask(server, query, lat, lng, kind) } }
                ?: PlaceSearchAnswer.Failed(SearchFailure.Slow)
            if (answer is PlaceSearchAnswer.Found) return answer
            // The cause of the main server is the one said: the others are only a help.
            if (first == null) first = (answer as PlaceSearchAnswer.Failed).cause
        }
        return PlaceSearchAnswer.Failed(first ?: SearchFailure.Other)
    }

    private fun ask(server: String, query: String, lat: Double, lng: Double, kind: TripPlaceKind): PlaceSearchAnswer = try {
        val connection = URL(server + "?data=" + URLEncoder.encode(query, "UTF-8")).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 6_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "Familia/1.0 (application familiale)")
            val code = connection.responseCode
            if (code != 200) {
                PlaceSearchAnswer.Failed(failureOfCode(code))
            } else {
                parseFoundPlaces(connection.inputStream.bufferedReader().readText(), lat, lng, kind)?.let { PlaceSearchAnswer.Found(it) }
                    ?: PlaceSearchAnswer.Failed(SearchFailure.Other)
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: InterruptedIOException) {
        // Stopped by a newer question, or no answer in time (SocketTimeoutException is one too).
        PlaceSearchAnswer.Failed(SearchFailure.Slow)
    } catch (e: UnknownHostException) {
        PlaceSearchAnswer.Failed(SearchFailure.Offline)
    } catch (e: ConnectException) {
        PlaceSearchAnswer.Failed(SearchFailure.Offline)
    } catch (e: SSLException) {
        // A Wi-Fi that asks to sign in first, or that filters the server, breaks the secure link.
        PlaceSearchAnswer.Failed(SearchFailure.Offline)
    } catch (e: IOException) {
        PlaceSearchAnswer.Failed(SearchFailure.Other)
    }

    private companion object {
        const val TIMEOUT_MS = 12_000L
        val SERVERS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )
    }
}

/** The cause said for an HTTP [code] of Overpass. */
fun failureOfCode(code: Int): SearchFailure = when (code) {
    429, 502, 503, 504 -> SearchFailure.Busy
    else -> SearchFailure.Other
}

const val PLACE_SEARCH_RADIUS = 10_000
const val PLACE_SEARCH_MAX = 12

/** The OpenStreetMap tags of each kind. */
private fun filters(kind: TripPlaceKind): List<String> = when (kind) {
    TripPlaceKind.Lodging -> listOf("[\"tourism\"~\"^(hotel|guest_house|hostel|motel|chalet|apartment|camp_site|caravan_site|alpine_hut)$\"]")
    TripPlaceKind.Restaurant -> listOf("[\"amenity\"~\"^(restaurant|cafe|fast_food|bar|pub|ice_cream|biergarten)$\"]")
    TripPlaceKind.Spot -> listOf(
        "[\"tourism\"~\"^(attraction|museum|viewpoint|gallery|zoo|theme_park|aquarium)$\"]",
        "[\"historic\"~\"^(castle|monument|ruins|memorial|archaeological_site|fort)$\"]",
        "[\"natural\"=\"beach\"]",
        "[\"leisure\"~\"^(park|water_park|nature_reserve)$\"]",
    )
}

/**
 * The Overpass question: named places of [kind] within [PLACE_SEARCH_RADIUS] of the point, whose
 * name holds the typed [text] (any case) when there is one. The text is escaped: it can never
 * change the question.
 */
fun placeSearchQuery(kind: TripPlaceKind, lat: Double, lng: Double, text: String): String {
    val around = "(around:$PLACE_SEARCH_RADIUS,${"%.5f".format(Locale.US, lat)},${"%.5f".format(Locale.US, lng)})"
    val words = searchWords(text)
    val name = if (words.isEmpty()) "[\"name\"]" else "[\"name\"~\"$words\",i]"
    val body = filters(kind).joinToString("") { "nwr$it$name$around;" }
    return "[out:json][timeout:20];($body);out center 80;"
}

/** The typed words, at most 60 characters, as a literal for an Overpass regular expression inside quotes. */
fun searchWords(text: String): String {
    val clean = text.trim().replace(Regex("\\s+"), " ").take(60)
    return buildString {
        clean.forEach { c ->
            when {
                c == '"' || c == '\\' -> Unit
                c in "^$.|?*+()[]{}" -> append("\\\\").append(c)
                else -> append(c)
            }
        }
    }
}

/** Reads an Overpass answer: named places, nearest first, at most [PLACE_SEARCH_MAX]; null when it is not readable. */
fun parseFoundPlaces(json: String, lat: Double, lng: Double, kind: TripPlaceKind): List<FoundPlace>? = runCatching {
    val elements = Json.parseToJsonElement(json).jsonObject["elements"]?.jsonArray ?: return@runCatching null
    elements.mapNotNull { element ->
        val item: JsonObject = element.jsonObject
        val tags = item["tags"]?.jsonObject ?: return@mapNotNull null
        fun tag(key: String) = tags[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val name = tag("name") ?: return@mapNotNull null
        val center = item["center"]?.jsonObject
        val pointLat = (item["lat"] ?: center?.get("lat"))?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val pointLng = (item["lon"] ?: center?.get("lon"))?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val street = listOfNotNull(tag("addr:housenumber"), tag("addr:street")).joinToString(" ").ifEmpty { null }
        val address = listOfNotNull(street, tag("addr:city")).joinToString(", ").ifEmpty { null }
        val key = listOf("tourism", "amenity", "historic", "natural", "leisure").firstNotNullOfOrNull { tag(it) }
        FoundPlace(name.take(120), foundGenre(key, kind), address?.take(200), pointLat, pointLng, distanceMeters(lat, lng, pointLat, pointLng))
    }.distinctBy { it.name.lowercase() }.sortedBy { it.meters }.take(PLACE_SEARCH_MAX)
}.getOrNull()

/** The kind of a found place in French, from its OpenStreetMap tag. */
fun foundGenre(tag: String?, kind: TripPlaceKind): String = when (tag) {
    "hotel", "motel" -> "Hôtel"
    "guest_house" -> "Chambre d'hôtes"
    "hostel" -> "Auberge de jeunesse"
    "chalet" -> "Gîte"
    "apartment" -> "Appartement"
    "camp_site" -> "Camping"
    "caravan_site" -> "Aire de camping-car"
    "alpine_hut" -> "Refuge"
    "biergarten" -> "Bar"
    "fort" -> "Fort"
    "beach" -> "Plage"
    "park" -> "Parc"
    "water_park" -> "Parc aquatique"
    "nature_reserve" -> "Réserve naturelle"
    null -> when (kind) {
        TripPlaceKind.Lodging -> "Hébergement"
        TripPlaceKind.Restaurant -> "Restaurant"
        TripPlaceKind.Spot -> "Lieu à voir"
    }
    else -> genreOf(tag)
}
