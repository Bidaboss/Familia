package fr.familia.app.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** What a shop page says of itself in its Open Graph tags (PRD, récit 8 bis). */
data class LinkPreview(val title: String?, val imageUrl: String?, val price: Double?)

/** Reads the preview of a shared link. Nothing is stored by Familia but the image link. */
fun interface LinkPreviewRepository {
    /** Null when the page cannot be read: the member fills the wish by hand. */
    suspend fun preview(url: String): LinkPreview?
}

/** Reads the page from the phone, the beginning only: the tags sit in its head. */
class HttpLinkPreviewRepository : LinkPreviewRepository {
    override suspend fun preview(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "text/html")
                if (connection.responseCode !in 200..299) return@withContext null
                if (connection.contentType?.contains("html", ignoreCase = true) == false) return@withContext null
                val head = connection.inputStream.use { stream ->
                    decodePage(stream.readNBytesCompat(MAX_BYTES), connection.contentType)
                }
                parseLinkPreview(head, base = connection.url.toString())
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            // Offline, a refused page or a strange link: no preview.
            null
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < max) {
            val read = read(buffer, 0, minOf(buffer.size, max - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000
        const val MAX_BYTES = 256 * 1024
    }
}

private val META = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE)
private val ATTRIBUTE = Regex("""([a-zA-Z:_-]+)\s*=\s*("([^"]*)"|'([^']*)')""")
private val TITLE_TAG = Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE)

/**
 * The title, picture and price a page announces: Open Graph first (og:title, og:image,
 * product:price:amount), then the usual fallbacks. Only an https picture is kept, as the rules ask.
 */
fun parseLinkPreview(html: String, base: String): LinkPreview? {
    val tags = META.findAll(html).map { tag ->
        ATTRIBUTE.findAll(tag.value).associate { m -> m.groupValues[1].lowercase() to (m.groups[3]?.value ?: m.groups[4]?.value.orEmpty()) }
    }.toList()
    fun meta(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        tags.firstOrNull { (it["property"] ?: it["name"])?.lowercase() == name }?.get("content")?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() }
    }
    val title = meta("og:title", "twitter:title") ?: TITLE_TAG.find(html)?.groupValues?.get(1)?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() }
    val image = meta("og:image:secure_url", "og:image", "twitter:image")
        ?.let { resolve(it, base) }
        ?.takeIf { it.startsWith("https://") && it.length <= 1000 }
    // A price in another currency would mislead: only euros, or a price with no currency given.
    val currency = meta("product:price:currency", "og:price:currency")?.uppercase()
    val price = meta("product:price:amount", "og:price:amount")
        ?.takeIf { currency == null || currency == "EUR" }
        ?.replace(',', '.')
        ?.toDoubleOrNull()
        ?.takeIf { it >= 0 && it <= 100_000 }
    if (title == null && image == null && price == null) return null
    return LinkPreview(title?.take(80), image, price)
}

private fun resolve(link: String, base: String): String? = try {
    URL(URL(base), link).toString()
} catch (e: Exception) {
    null
}

private fun unescape(text: String): String = text
    .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
    .replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")

private val CHARSET = Regex("""charset\s*=\s*["']?([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)

/** The page in its own charset: the server's header first, then the page's meta, else UTF-8. */
internal fun decodePage(bytes: ByteArray, contentType: String?): String {
    val declared = contentType?.let { CHARSET.find(it)?.groupValues?.get(1) }
        ?: CHARSET.find(String(bytes, Charsets.ISO_8859_1).take(4096))?.groupValues?.get(1)
    val charset = declared?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
    return String(bytes, charset)
}
