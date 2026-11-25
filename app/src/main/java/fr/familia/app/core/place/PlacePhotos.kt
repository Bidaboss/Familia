package fr.familia.app.core.place

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A real photo of a place (règle 12, PRODUIT-153), from Wikimedia Commons through the French
 * Wikipedia page of the place, with the credit to show beside it (author and licence).
 */
data class PlacePhoto(val url: String, val credit: String)

/** The photo of a place, read at display time; the images themselves go through the cache of Coil. */
interface PlacePhotos {
    /** The photo of [place], or null when there is none, no network, or a page that names several places. */
    suspend fun of(place: String): PlacePhoto?

    /**
     * The photo of [place], else, when its page has none (« La Samoure », DESIGN-104 A), the photo
     * of the nearest Wikipedia page around [lat], [lng] that has one. Null when none is found.
     */
    suspend fun of(place: String, lat: Double?, lng: Double?): PlacePhoto? = of(place)

    /**
     * The photo of the nearest Wikipedia page around [lat], [lng] that has one, other than [except]:
     * the second chance of a cover whose image could not be shown (retour E-47).
     */
    suspend fun near(lat: Double, lng: Double, except: String? = null): PlacePhoto? = null
}

/** The summary service of Wikipedia in French then the image info of Commons: free, no key, a generic User-Agent. */
class WikimediaPlacePhotos(
    /** Why a photo could not be had (retour du 24/11 soir): « refusé 403 fr.wikipedia.org », for « Plantages ». */
    private val onRefused: (String) -> Unit = {},
) : PlacePhotos {
    private val known = java.util.concurrent.ConcurrentHashMap<String, Result<PlacePhoto?>>()

    override suspend fun of(place: String): PlacePhoto? {
        val title = place.trim().take(MAX_TITLE).takeIf { it.isNotEmpty() } ?: return null
        known[title]?.let { return it.getOrNull() }
        val found = withContext(Dispatchers.IO) {
            withTimeoutOrNull(TIMEOUT_MS) {
                runCatching {
                    // Retour du 24/11 23 h 57 (« refusé 404 »): no page has the exact name. The name before
                    // its comma, then the search of Wikipedia, before the pages around (in of(place, lat, lng)).
                    val image = titleCandidates(title).firstNotNullOfOrNull { candidate -> read(photoSummaryUrl(candidate))?.let(::parseSummaryImage) }
                        ?: read(searchPhotosUrl(title))?.let { parseNearbyImage(it) }
                        ?: return@runCatching null
                    val credit = read(commonsInfoUrl(image.file))?.let(::parseCredit) ?: DEFAULT_CREDIT
                    PlacePhoto(image.url, credit)
                }
            }
        }
        // Only a real answer is kept for the session: no network tries again next time.
        if (found != null && found.isSuccess) known[title] = found
        refusal(found)?.let(onRefused)
        return found?.getOrNull()
    }

    override suspend fun of(place: String, lat: Double?, lng: Double?): PlacePhoto? {
        of(place)?.let { return it }
        // Then the item of OpenStreetMap at the point (DEV-1), before the nearest page with a photo.
        val around = if (lat == null || lng == null) null else wikidataAt(place, lat, lng) ?: near(lat, lng)
        // For « Plantages »: where the search went, never the place itself.
        if (around == null) onRefused(noPhotoLine(withPoint = lat != null && lng != null))
        return around
    }

    override suspend fun near(lat: Double, lng: Double, except: String?): PlacePhoto? {
        val key = "@%.3f,%.3f".format(java.util.Locale.ROOT, lat, lng) + (except?.let { "-$it" }.orEmpty())
        known[key]?.let { return it.getOrNull() }
        val found = withContext(Dispatchers.IO) {
            withTimeoutOrNull(TIMEOUT_MS) {
                runCatching {
                    val image = read(nearbyPhotosUrl(lat, lng))?.let { parseNearbyImage(it, except) } ?: return@runCatching null
                    val credit = read(commonsInfoUrl(image.file))?.let(::parseCredit) ?: DEFAULT_CREDIT
                    PlacePhoto(image.url, credit)
                }
            }
        }
        if (found != null && found.isSuccess) known[key] = found
        refusal(found)?.let(onRefused)
        return found?.getOrNull()
    }

    /**
     * The Wikidata photo of the town or site of OpenStreetMap at [lat], [lng] (DEV-1, 25/11): the one
     * named like [place] first. Null when OpenStreetMap is busy or none has a photo.
     */
    private suspend fun wikidataAt(place: String, lat: Double, lng: Double): PlacePhoto? {
        val key = "wd@%.3f,%.3f-".format(java.util.Locale.ROOT, lat, lng) + place.trim().take(MAX_TITLE)
        known[key]?.let { return it.getOrNull() }
        val found = withContext(Dispatchers.IO) {
            withTimeoutOrNull(WIKIDATA_TIMEOUT_MS) {
                runCatching {
                    val query = wikidataAroundQuery(lat, lng)
                    val items = OVERPASS_SERVERS.firstNotNullOfOrNull { server ->
                        readQuietly(server + "?data=" + java.net.URLEncoder.encode(query, "UTF-8"))?.let { parseWikidataItems(it, lat, lng, place) }
                    } ?: return@runCatching null
                    items.firstNotNullOfOrNull { item ->
                        val file = read(wikidataImageUrl(item))?.let(::parseWikidataImage) ?: return@firstNotNullOfOrNull null
                        read(commonsPhotoUrl(file))?.let(::parseCommonsPhoto)
                    }
                }
            }
        }
        // Kept only when an item was found: OpenStreetMap busy or slow tries again next time.
        if (found != null && found.getOrNull() != null) known[key] = found
        return found?.getOrNull()
    }

    /** An answer of OpenStreetMap, or null: a busy server is common and is not a failure for « Plantages ». */
    private fun readQuietly(address: String): String? = runCatching {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode == 200) connection.inputStream.bufferedReader().readText() else null
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun read(address: String): String? {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code != 200) {
                // A 404 is a page that does not exist, not a refusal: the next way is tried.
                if (code != 404) onRefused(photoRefusalLine(code, connection.url.host))
                null
            } else {
                connection.inputStream.bufferedReader().readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val USER_AGENT = WIKIMEDIA_USER_AGENT
        private const val TIMEOUT_MS = 8_000L
        private const val WIKIDATA_TIMEOUT_MS = 14_000L
        private const val MAX_TITLE = 100
    }
}

/** The line of « Plantages » for an answer other than 200: the code and the site, never the place. */
fun photoRefusalLine(code: Int, host: String?): String = "refusé $code ${host.orEmpty()}".trim().take(60)

/** The line of « Plantages » when no way gave a photo: whether the pages around could be asked. */
fun noPhotoLine(withPoint: Boolean): String =
    if (withPoint) "aucune photo : nom, recherche, Wikidata et 10 km" else "aucune photo : nom et recherche, lieu sans position"

/**
 * The titles tried for [place]: itself, then the part before its first comma
 * (« Samoure, Charente » then « Samoure »), never empty, never twice.
 */
fun titleCandidates(place: String): List<String> =
    listOf(place.trim(), place.substringBefore(',').trim()).filter { it.isNotEmpty() }.distinct()

/** The search of French Wikipedia for [place]: the three best pages, with their main image. */
fun searchPhotosUrl(place: String): String =
    "https://fr.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&generator=search" +
        "&gsrsearch=" + URLEncoder.encode(place.trim(), "UTF-8") +
        "&gsrlimit=3&prop=pageimages&piprop=thumbnail%7Coriginal&pithumbsize=$PHOTO_WIDTH"

/** A search that broke (no network, unreadable answer) or ran out of time; null when it answered. */
internal fun refusal(found: Result<PlacePhoto?>?): String? = when {
    found == null -> "délai dépassé"
    found.isFailure -> found.exceptionOrNull()?.javaClass?.simpleName?.take(60) ?: "erreur"
    else -> null
}

/** Shown when Commons does not say who took the photo. */
const val DEFAULT_CREDIT = "Wikimedia Commons"

/**
 * The width asked to Commons: sharp on a phone, light enough on mobile data. Wikimedia now serves
 * its thumbnails only at standard widths (20, 40, 60, 120, 250, 330, 500, 960, 1280, 1920, 3840);
 * another one is refused, and the cover fell back on its drawing (retour E-47, 1080 before).
 */
const val PHOTO_WIDTH = 960

/** The thumbnail widths Wikimedia serves (retour E-47). */
val WIKIMEDIA_WIDTHS = listOf(20, 40, 60, 120, 250, 330, 500, 960, 1280, 1920, 3840)

/**
 * The User-Agent of every call to Wikipedia, Wikivoyage and Commons, images included: the policy of
 * Wikimedia refuses the default one of a library (« okhttp/… »). Generic: nothing about the family.
 */
const val WIKIMEDIA_USER_AGENT = "Familia/1.0 (application familiale Android)"

/** The summary address of a Wikipedia page: spaces become underscores, the rest is encoded. */
fun photoSummaryUrl(title: String): String =
    "https://fr.wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(title.trim().replace(' ', '_'), "UTF-8").replace("+", "%20")

/** The image information of a Commons file, with its author and licence. */
fun commonsInfoUrl(file: String): String =
    "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2&prop=imageinfo&iiprop=extmetadata&titles=" +
        URLEncoder.encode("File:$file", "UTF-8")

/** The pages of French Wikipedia within 10 km of a point, nearest first, with their main image. */
fun nearbyPhotosUrl(lat: Double, lng: Double): String =
    "https://fr.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&generator=geosearch" +
        "&ggscoord=" + URLEncoder.encode("%.3f|%.3f".format(java.util.Locale.ROOT, lat, lng), "UTF-8") +
        "&ggsradius=10000&ggslimit=10&prop=pageimages&piprop=thumbnail%7Coriginal&pithumbsize=$PHOTO_WIDTH"

/**
 * The first photo among the nearby pages (their order is « index », the nearest first): a real
 * photo (jpg, png, webp), never a drawing, a map or a flag. Null when none has one.
 */
fun parseNearbyImage(json: String, except: String? = null): SummaryImage? = runCatching {
    val pages = Json.parseToJsonElement(json).jsonObject["query"]?.jsonObject?.get("pages")?.jsonArray ?: return@runCatching null
    pages.map { it.jsonObject }
        .sortedBy { it["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: Int.MAX_VALUE }
        .firstNotNullOfOrNull { page ->
            val thumb = page["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.contentOrNull
            val original = page["original"]?.jsonObject?.get("source")?.jsonPrimitive?.contentOrNull
            val source = (thumb ?: original)?.takeIf { it.startsWith("https://upload.wikimedia.org/") } ?: return@firstNotNullOfOrNull null
            val file = fileName(original ?: source)?.takeIf { name -> name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp") } }
                ?: return@firstNotNullOfOrNull null
            SummaryImage(url = widened(source), file = file).takeIf { it.url != except }
        }
}.getOrNull()

/** The image of a summary: its address at [PHOTO_WIDTH] and the name of its Commons file. */
data class SummaryImage(val url: String, val file: String)

/** Reads the image of a summary answer; null for a disambiguation page, no image, or something unreadable. */
fun parseSummaryImage(json: String): SummaryImage? = runCatching {
    val root: JsonObject = Json.parseToJsonElement(json).jsonObject
    if (root["type"]?.jsonPrimitive?.contentOrNull == "disambiguation") return@runCatching null
    val thumb = root["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.contentOrNull
    val original = root["originalimage"]?.jsonObject?.get("source")?.jsonPrimitive?.contentOrNull
    val source = thumb ?: original ?: return@runCatching null
    if (!source.startsWith("https://upload.wikimedia.org/")) return@runCatching null
    // Drawings (svg, maps, flags, coats of arms) are not photos of the place.
    val file = fileName(original ?: source) ?: return@runCatching null
    if (!file.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp") }) return@runCatching null
    SummaryImage(url = widened(source), file = file)
}.getOrNull()

/** « …/thumb/a/ab/Port.jpg/320px-Port.jpg » asked at [PHOTO_WIDTH] pixels; an original stays as it is. */
fun widened(url: String): String = url.replace(Regex("/\\d+px-([^/]+)$"), "/${PHOTO_WIDTH}px-$1")

/** The file of a Commons address, decoded: « Port de Samoure.jpg ». */
fun fileName(url: String): String? {
    val path = url.substringBefore('?')
    val name = if ("/thumb/" in path) path.substringAfter("/thumb/").split('/').getOrNull(2) else path.substringAfterLast('/')
    return name?.takeIf { it.isNotBlank() }?.let { java.net.URLDecoder.decode(it, "UTF-8") }
}

/** « Photo : Jean Dupont, CC BY-SA 4.0, Wikimedia Commons » from the image information, without its HTML. */
fun parseCredit(json: String): String? = runCatching {
    val page = Json.parseToJsonElement(json).jsonObject["query"]?.jsonObject?.get("pages")?.jsonArray?.firstOrNull()?.jsonObject
    val meta = page?.get("imageinfo")?.jsonArray?.firstOrNull()?.jsonObject?.get("extmetadata")?.jsonObject ?: return@runCatching null
    fun value(key: String) = meta[key]?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull?.let(::plain)?.takeIf { it.isNotBlank() }
    val parts = listOfNotNull(value("Artist")?.take(60), value("LicenseShortName"), DEFAULT_CREDIT)
    parts.joinToString(", ")
}.getOrNull()

/** The text of a bit of HTML: no tags, no entities, one line. */
fun plain(html: String): String =
    html.replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
