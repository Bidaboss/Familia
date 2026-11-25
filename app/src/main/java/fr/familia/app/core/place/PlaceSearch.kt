package fr.familia.app.core.place

import fr.familia.app.core.model.TripPlaceKind
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/**
 * The search of an accommodation, a restaurant or a spot around a destination (règle 20), from
 * OpenStreetMap (© les contributeurs d'OpenStreetMap). Only the point of the destination and the
 * typed words leave the phone; nothing is stored.
 */
interface PlaceSearch {
    /** Up to [PLACE_SEARCH_MAX] places within [PLACE_SEARCH_RADIUS] m, nearest first; empty when none, null when the service did not answer. */
    suspend fun search(lat: Double, lng: Double, kind: TripPlaceKind, text: String): List<FoundPlace>?
}

/** Overpass, as for « Autour de l'étape »: a second server when the first is busy. */
class OverpassPlaceSearch : PlaceSearch {
    override suspend fun search(lat: Double, lng: Double, kind: TripPlaceKind, text: String): List<FoundPlace>? = withContext(Dispatchers.IO) {
        val query = placeSearchQuery(kind, lat, lng, text)
        for (server in SERVERS) {
            val found = withTimeoutOrNull(TIMEOUT_MS) { ask(server, query, lat, lng, kind) }
            if (found != null) return@withContext found
        }
        null
    }

    private fun ask(server: String, query: String, lat: Double, lng: Double, kind: TripPlaceKind): List<FoundPlace>? = runCatching {
        val connection = URL(server + "?data=" + URLEncoder.encode(query, "UTF-8")).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 6_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("User-Agent", "Familia/1.0 (application familiale)")
            if (connection.responseCode != 200) null else parseFoundPlaces(connection.inputStream.bufferedReader().readText(), lat, lng, kind)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private companion object {
        const val TIMEOUT_MS = 16_000L
        val SERVERS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )
    }
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
