package fr.familia.app.core.place

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
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

/** What the member looks for around a step (récit 96, « Autour de l'étape »). */
enum class NearbyKind { Eat, See }

/** One place around a step: its [name], its kind in a few words, and how far it is. */
data class NearbyPlace(val name: String, val genre: String, val lat: Double, val lng: Double, val meters: Int)

/** The places around a point, from OpenStreetMap (© les contributeurs d'OpenStreetMap). Nothing is stored. */
interface NearbyPlaces {
    /** Up to 5 places within 5 km, nearest first; an empty list when there is none, null when the service did not answer. */
    suspend fun around(lat: Double, lng: Double, kind: NearbyKind): List<NearbyPlace>?
}

/**
 * Overpass, the free query service of OpenStreetMap, with a generic User-Agent. The main server is
 * often busy (it answers 429 or 504 at some hours): the same question then goes to a second
 * server, and only when both fail is the service said not to answer.
 */
class OverpassNearbyPlaces : NearbyPlaces {
    override suspend fun around(lat: Double, lng: Double, kind: NearbyKind): List<NearbyPlace>? = withContext(Dispatchers.IO) {
        val query = overpassQuery(kind, lat, lng)
        for (server in SERVERS) {
            val places = withTimeoutOrNull(TIMEOUT_MS) { ask(server, query, lat, lng) }
            if (places != null) return@withContext places
        }
        null
    }

    private fun ask(server: String, query: String, lat: Double, lng: Double): List<NearbyPlace>? = runCatching {
        val connection = URL(server + "?data=" + URLEncoder.encode(query, "UTF-8")).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 6_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("User-Agent", "Familia/1.0 (application familiale)")
            if (connection.responseCode != 200) null else parseNearby(connection.inputStream.bufferedReader().readText(), lat, lng)
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

const val NEARBY_RADIUS_METERS = 5_000
const val NEARBY_MAX = 5

/** The Overpass question: named places of the kind within [NEARBY_RADIUS_METERS] of the point. */
fun overpassQuery(kind: NearbyKind, lat: Double, lng: Double): String {
    val around = "(around:$NEARBY_RADIUS_METERS,${"%.5f".format(Locale.US, lat)},${"%.5f".format(Locale.US, lng)})"
    val filters = when (kind) {
        NearbyKind.Eat -> listOf("[\"amenity\"~\"^(restaurant|cafe|fast_food|bar|pub|ice_cream)$\"]")
        NearbyKind.See -> listOf(
            "[\"tourism\"~\"^(attraction|museum|viewpoint|gallery|zoo|theme_park|aquarium)$\"]",
            "[\"historic\"~\"^(castle|monument|ruins|memorial|archaeological_site)$\"]",
        )
    }
    val body = filters.joinToString("") { "nwr$it[\"name\"]$around;" }
    return "[out:json][timeout:20];($body);out center 60;"
}

/** Reads an Overpass answer: named places, nearest first, at most [NEARBY_MAX]; null when it is not readable. */
fun parseNearby(json: String, lat: Double, lng: Double): List<NearbyPlace>? = runCatching {
    val elements = Json.parseToJsonElement(json).jsonObject["elements"]?.jsonArray ?: return@runCatching null
    elements.mapNotNull { element ->
        val item: JsonObject = element.jsonObject
        val tags = item["tags"]?.jsonObject ?: return@mapNotNull null
        val name = tags["name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val center = item["center"]?.jsonObject
        val pointLat = (item["lat"] ?: center?.get("lat"))?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val pointLng = (item["lon"] ?: center?.get("lon"))?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val key = listOf("amenity", "tourism", "historic").firstNotNullOfOrNull { tags[it]?.jsonPrimitive?.contentOrNull }
        NearbyPlace(name.take(80), genreOf(key), pointLat, pointLng, distanceMeters(lat, lng, pointLat, pointLng))
    }.distinctBy { it.name }.sortedBy { it.meters }.take(NEARBY_MAX)
}.getOrNull()

/** The kind of a place in French, from its OpenStreetMap tag. */
fun genreOf(tag: String?): String = when (tag) {
    "restaurant" -> "Restaurant"
    "cafe" -> "Café"
    "fast_food" -> "Restauration rapide"
    "bar" -> "Bar"
    "pub" -> "Pub"
    "ice_cream" -> "Glacier"
    "museum" -> "Musée"
    "gallery" -> "Galerie"
    "viewpoint" -> "Point de vue"
    "attraction" -> "Site à voir"
    "zoo" -> "Zoo"
    "theme_park" -> "Parc de loisirs"
    "aquarium" -> "Aquarium"
    "castle" -> "Château"
    "monument" -> "Monument"
    "ruins" -> "Ruines"
    "memorial" -> "Mémorial"
    "archaeological_site" -> "Site archéologique"
    else -> "Lieu"
}

/** The distance in metres between two points (haversine). */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
    val r = 6_371_000.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = p2 - p1
    val dl = Math.toRadians(lng2 - lng1)
    val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return (2 * r * asin(sqrt(a))).roundToInt()
}

/** « 350 m », « 1,2 km »: metres up to 1 km, then kilometres with a comma. */
fun distanceLabel(meters: Int): String =
    if (meters < 1_000) "${maxOf(10, (meters + 5) / 10 * 10)} m" else "${"%.1f".format(Locale.FRANCE, meters / 1000.0)} km"

/** The link that opens a place in the map app of the phone (geo: intent). */
fun geoLink(place: NearbyPlace): String =
    "geo:${place.lat},${place.lng}?q=${place.lat},${place.lng}(${URLEncoder.encode(place.name, "UTF-8").replace("+", "%20")})"
