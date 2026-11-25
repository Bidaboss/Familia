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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A short text about a place, from Wikivoyage (licence CC BY-SA), with the page to read the rest. */
data class PlaceArticle(val title: String, val extract: String, val url: String)

/** What the web knows about a place (récit 96, « À savoir sur… »). Nothing is stored: read at display time. */
interface PlaceArticles {
    /** The article of [place], or null when there is none, no network, or a page that names several places. */
    suspend fun about(place: String): PlaceArticle?
}

/**
 * The summary service of a Wikimedia site in French: free, no key, a generic User-Agent. Wikivoyage
 * for « À savoir sur… » (récit 96), Wikipédia for the anecdote of a destination (récit 105, règle 45).
 */
class WikimediaPlaceArticles(private val host: String = WIKIVOYAGE_HOST) : PlaceArticles {
    override suspend fun about(place: String): PlaceArticle? {
        val title = place.trim().take(MAX_TITLE).takeIf { it.isNotEmpty() } ?: return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(TIMEOUT_MS) {
                runCatching {
                    val connection = URL(summaryUrl(title, host)).openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 5_000
                        connection.readTimeout = 5_000
                        connection.setRequestProperty("User-Agent", USER_AGENT)
                        connection.setRequestProperty("Accept", "application/json")
                        if (connection.responseCode != 200) null else parseArticle(connection.inputStream.bufferedReader().readText())
                    } finally {
                        connection.disconnect()
                    }
                }.getOrNull()
            }
        }
    }

    companion object {
        private const val USER_AGENT = "Familia/1.0 (application familiale)"
        private const val TIMEOUT_MS = 8_000L
        private const val MAX_TITLE = 100
    }
}

/** The summary address of a page: spaces become underscores, the rest is encoded. */
fun summaryUrl(title: String, host: String = WIKIVOYAGE_HOST): String =
    "https://$host/api/rest_v1/page/summary/" + URLEncoder.encode(title.trim().replace(' ', '_'), "UTF-8").replace("+", "%20")

const val WIKIVOYAGE_HOST = "fr.wikivoyage.org"
const val WIKIPEDIA_HOST = "fr.wikipedia.org"

/** At most this many characters are shown, about three lines. */
const val ARTICLE_MAX_CHARS = 280

/** Reads a summary answer; null for a disambiguation page, an empty text or something unreadable. */
fun parseArticle(json: String): PlaceArticle? = runCatching {
    val root: JsonObject = Json.parseToJsonElement(json).jsonObject
    if (root["type"]?.jsonPrimitive?.contentOrNull == "disambiguation") return@runCatching null
    val extract = root["extract"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (extract.isEmpty()) return@runCatching null
    val title = root["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val page = root["content_urls"]?.jsonObject?.get("desktop")?.jsonObject?.get("page")?.jsonPrimitive?.contentOrNull
    val url = page?.takeIf { it.startsWith("https://") } ?: return@runCatching null
    PlaceArticle(title = title, extract = shorten(extract), url = url)
}.getOrNull()

/** Cuts a text at the last word before [ARTICLE_MAX_CHARS], with « … ». */
fun shorten(text: String, max: Int = ARTICLE_MAX_CHARS): String {
    if (text.length <= max) return text
    val cut = text.take(max).substringBeforeLast(' ', text.take(max)).trimEnd(',', ';', ':', ' ')
    return "$cut…"
}
