package fr.familia.app.core.place

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * « Autre photo » (demande de l'admin du 25/11 soir): the other photos around a destination, to
 * change the one of its cover. The pages of French Wikipedia within 10 km, nearest first, each
 * tap the next one, back to the first after the last. Free, no key, the generic User-Agent; nothing
 * about the family is sent, only the point of the destination, rounded.
 */
class CoverChoices(
    /** The answer at an address, or null (no network, refused): replaced in the tests. */
    private val read: (String) -> String? = ::readWikimedia,
) {
    private val around = java.util.concurrent.ConcurrentHashMap<String, List<SummaryImage>>()

    /** The photo after [current] around [lat], [lng], with its credit; null when there is no other one. */
    suspend fun next(lat: Double, lng: Double, current: String?): PlacePhoto? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TIMEOUT_MS) {
            runCatching {
                val key = "%.3f,%.3f".format(java.util.Locale.ROOT, lat, lng)
                val images = around[key] ?: read(nearbyPhotosUrl(lat, lng))?.let(::parseNearbyImages)?.also { if (it.isNotEmpty()) around[key] = it }
                val image = nextChoice(images.orEmpty(), current) ?: return@runCatching null
                PlacePhoto(image.url, read(commonsInfoUrl(image.file))?.let(::parseCredit) ?: DEFAULT_CREDIT)
            }.getOrNull()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}

/**
 * The photo after [current] in [images], back to the first after the last; the first when [current]
 * is not among them (the photo of the page of the place itself). Null when none is another photo.
 */
fun nextChoice(images: List<SummaryImage>, current: String?): SummaryImage? {
    val others = images.filter { it.url != current }
    if (others.isEmpty()) return null
    val at = images.indexOfFirst { it.url == current }
    if (at < 0) return others.first()
    return (1..images.size).map { images[(at + it) % images.size] }.first { it.url != current }
}

/**
 * Every photo among the nearby pages, nearest first, each once, with the same choice as for the
 * cover ([parseNearbyImage]): a real photo, never a drawing, a map, a flag or a montage.
 */
fun parseNearbyImages(json: String): List<SummaryImage> = runCatching {
    val pages = Json.parseToJsonElement(json).jsonObject["query"]?.jsonObject?.get("pages")?.jsonArray ?: return@runCatching emptyList()
    pages.map { it.jsonObject }
        .sortedBy { it["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: Int.MAX_VALUE }
        .mapNotNull { page ->
            val single = Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), page)
            parseNearbyImage("""{"query":{"pages":[$single]}}""")
        }
        .distinctBy { it.file }
}.getOrDefault(emptyList())

/** A GET with the generic User-Agent of Familia, or null. */
private fun readWikimedia(address: String): String? = runCatching {
    val connection = java.net.URL(address).openConnection() as java.net.HttpURLConnection
    connection.connectTimeout = 6_000
    connection.readTimeout = 6_000
    connection.setRequestProperty("User-Agent", WIKIMEDIA_USER_AGENT)
    try {
        if (connection.responseCode != 200) null else connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}.getOrNull()
