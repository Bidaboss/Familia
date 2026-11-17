package fr.familia.app.core.place

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * A precise place (PRD, récit 33), the one component that every module attaches: « Rejoignez-moi »
 * now, then a Bonne nouvelle, an event or a step of a trip. [address] comes from the geocoder of the
 * phone (free) and [name] is the member's own (« Plage des Sables »). There is no map in the app,
 * so no API key: « Itinéraire » hands the place to Google Maps or Waze.
 */
data class Place(
    val lat: Double,
    val lng: Double,
    val name: String? = null,
    val address: String? = null,
) {
    /** What a row shows first: the name, else the address, else nothing (the caller says « position »). */
    val title: String? get() = name ?: address

    companion object {
        const val MAX_NAME = 60
        const val MAX_ADDRESS = 200
    }
}

/**
 * What a pasted text says about a place: exact coordinates when it holds some, a [name] when a
 * Google Maps link names the place, or a [query] (an address to look up) when it only searches.
 */
data class PastedPlace(val lat: Double? = null, val lng: Double? = null, val name: String? = null, val query: String? = null) {
    val hasPosition: Boolean get() = lat != null && lng != null
}

/** Reads a Google Maps link, a geo: link or bare coordinates (« 46.49, -1.79 »); null for anything else. */
fun parsePastedPlace(text: String): PastedPlace? {
    val raw = text.trim()
    if (raw.isEmpty()) return null
    coordinates(raw)?.let { (lat, lng) -> return PastedPlace(lat, lng) }
    // A link inside a longer text (« Regarde : https://… »): only the link counts.
    val link = LINK.find(raw)?.value ?: return null
    return when {
        link.startsWith("geo:", ignoreCase = true) -> parseGeo(link)
        isGoogleMaps(link) -> parseGoogleMaps(link)
        else -> null
    }
}

/** A short link (maps.app.goo.gl, goo.gl/maps) that the phone must open once to read the real one. */
fun isShortMapsLink(text: String): Boolean {
    val link = LINK.find(text.trim())?.value ?: return false
    val host = hostOf(link)
    return host == "maps.app.goo.gl" || (host == "goo.gl" && link.contains("/maps", ignoreCase = true))
}

/** The link of [text] when it holds one, for [isShortMapsLink]. */
fun linkIn(text: String): String? = LINK.find(text.trim())?.value

/**
 * The geo: link that opens the route in the app the member chooses (Google Maps, Waze…): the pin
 * itself, labelled with the name of the place.
 */
fun directionsUri(place: Place): String {
    val point = "${coord(place.lat)},${coord(place.lng)}"
    val label = place.title?.let { "(${encode(it)})" } ?: ""
    return "geo:$point?q=$point$label"
}

/**
 * Google Street View at [place]: the Maps app opens the panorama nearest to the point, a browser does
 * the same without it. Free, no key. Where the street has no images, Maps shows the closest one.
 */
fun streetViewUri(place: Place): String =
    "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=${coord(place.lat)}%2C${coord(place.lng)}"

/** The same, for a place known only by its text (the « Lieu » of an event). */
fun directionsUri(address: String): String = "geo:0,0?q=${encode(address.trim())}"

/** A link anyone can open, with or without Familia (sharing to a messaging app, récit 31). */
fun webMapsLink(place: Place): String =
    "https://www.google.com/maps/search/?api=1&query=${coord(place.lat)}%2C${coord(place.lng)}"

private val LINK = Regex("""(https?://\S+|geo:\S+)""", RegexOption.IGNORE_CASE)
private val PAIR = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")
private val AT = Regex("""@(-?\d{1,2}\.\d+),(-?\d{1,3}\.\d+)""")
private val PIN = Regex("""!3d(-?\d{1,2}\.\d+)!4d(-?\d{1,3}\.\d+)""")

private fun coordinates(text: String): Pair<Double, Double>? {
    val match = PAIR.find(decode(text)) ?: return null
    val lat = match.groupValues[1].toDoubleOrNull() ?: return null
    val lng = match.groupValues[2].toDoubleOrNull() ?: return null
    return if (lat in -90.0..90.0 && lng in -180.0..180.0 && (lat != 0.0 || lng != 0.0)) lat to lng else null
}

private fun hostOf(link: String): String =
    link.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore(':').lowercase(Locale.ROOT)

private fun isGoogleMaps(link: String): Boolean {
    val host = hostOf(link)
    val google = host == "google.com" || host.endsWith(".google.com") || Regex("""(^|\.)google\.[a-z.]+$""").containsMatchIn(host)
    return host.startsWith("maps.") && google || google && link.contains("/maps", ignoreCase = true)
}

private fun parameter(link: String, name: String): String? {
    val query = link.substringAfter('?', "").substringBefore('#')
    return query.split('&').firstOrNull { it.substringBefore('=') == name }?.substringAfter('=', "")?.let(::decode)?.takeIf { it.isNotBlank() }
}

private fun parseGeo(link: String): PastedPlace? {
    val point = coordinates(link.substringAfter(':').substringBefore('?'))
    val q = parameter(link, "q")
    // geo:0,0?q=lat,lng(Nom) or geo:0,0?q=une adresse
    val label = q?.let { Regex("""\(([^)]*)\)\s*$""").find(it)?.groupValues?.get(1)?.takeIf { l -> l.isNotBlank() } }
    val qPoint = q?.substringBefore('(')?.let(::coordinates)
    val position = qPoint ?: point
    return when {
        position != null -> PastedPlace(position.first, position.second, name = label)
        q != null -> PastedPlace(query = q.substringBefore('(').trim().ifEmpty { null } ?: return null)
        else -> null
    }
}

private fun parseGoogleMaps(link: String): PastedPlace? {
    val path = link.substringAfter("://").substringBefore('?')
    val name = Regex("""/maps/place/([^/@]+)""").find(path)?.groupValues?.get(1)?.let(::decode)?.trim()?.takeIf { it.isNotEmpty() }
    val pin = PIN.find(link) ?: AT.find(link)
    val fromPin = pin?.let { m -> coordinates("${m.groupValues[1]},${m.groupValues[2]}") }
    val q = parameter(link, "q") ?: parameter(link, "query") ?: parameter(link, "ll") ?: parameter(link, "destination")
    val fromQuery = q?.let(::coordinates)
    val position = fromPin ?: fromQuery
    return when {
        position != null -> PastedPlace(position.first, position.second, name = name?.takeUnless { coordinates(it) != null })
        name != null -> PastedPlace(name = name, query = name)
        q != null -> PastedPlace(query = q)
        else -> null
    }
}

private fun decode(text: String): String = try {
    URLDecoder.decode(text, "UTF-8")
} catch (e: IllegalArgumentException) {
    text
}

private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

/** Six decimals: about 10 cm, more than a phone knows. */
private fun coord(value: Double): String = String.format(Locale.ROOT, "%.6f", value).trimEnd('0').trimEnd('.')
