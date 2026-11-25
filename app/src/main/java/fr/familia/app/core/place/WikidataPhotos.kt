package fr.familia.app.core.place

import java.net.URLEncoder
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * The photo of a destination through OpenStreetMap and Wikidata (DEV-1, 25/11): the town, village
 * or site of OpenStreetMap at the point of the destination often names its Wikidata item, whose
 * image (P18) is a Commons file. It finds a photo for places that have no page on French
 * Wikipedia, abroad above all. Free, no key; only the point leaves the phone.
 */

/** Towns and villages within this many metres of the point. */
const val WIKIDATA_PLACE_RADIUS = 5_000

/** Sites (a beach, a castle, a museum) within this many metres: a destination can be one. */
const val WIKIDATA_SITE_RADIUS = 1_500

/** The Overpass servers, as for the search of a place: a second one when the first is busy. */
val OVERPASS_SERVERS = listOf(
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
)

/**
 * The wider circles around a destination when nothing was found within 10 km (demande du 25/11 soir):
 * 20, then 30, then 40 km, one after the other, the first photo found ends the search.
 */
val WIKIDATA_WIDER_RADII = listOf(20_000, 30_000, 40_000)

/**
 * The Overpass question: the named towns and sites around the point that name their Wikidata item.
 * A [wide] circle (beyond 10 km) only asks the cities, towns and villages: the hamlets and the sites
 * would be too many, and too far to stand for the destination.
 */
fun wikidataAroundQuery(lat: Double, lng: Double, radius: Int = WIKIDATA_PLACE_RADIUS, wide: Boolean = false): String {
    val point = "${"%.5f".format(Locale.US, lat)},${"%.5f".format(Locale.US, lng)}"
    val kinds = if (wide) "city|town|village" else "city|town|village|hamlet|suburb|island|islet|locality"
    val place = "node[\"place\"~\"^($kinds)$\"][\"wikidata\"][\"name\"](around:$radius,$point);"
    val site = if (wide) "" else listOf("tourism", "historic", "natural", "leisure")
        .joinToString("") { "nwr[\"$it\"][\"wikidata\"][\"name\"](around:$WIKIDATA_SITE_RADIUS,$point);" }
    return "[out:json][timeout:15];($place$site);out center 40;"
}

/**
 * The Wikidata items of an Overpass answer, best first: those whose name is the one of the
 * destination (« Ajaccio », any case and accent), then the towns, then the sites, each the nearest
 * first. At most [max]; empty when none; null when it is not readable.
 */
fun parseWikidataItems(json: String, lat: Double, lng: Double, name: String, max: Int = 3): List<String>? = runCatching {
    val elements = Json.parseToJsonElement(json).jsonObject["elements"]?.jsonArray ?: return@runCatching null
    val wanted = titleCandidates(name).map(::foldedName).toSet()
    data class Item(val id: String, val sameName: Boolean, val isTown: Boolean, val meters: Int)
    elements.mapNotNull { element ->
        val item = element.jsonObject
        val tags = item["tags"]?.jsonObject ?: return@mapNotNull null
        fun tag(key: String) = tags[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val id = tag("wikidata")?.takeIf { it.matches(Regex("^Q[1-9][0-9]{0,11}$")) } ?: return@mapNotNull null
        val center = item["center"]?.jsonObject
        val pointLat = (item["lat"] ?: center?.get("lat"))?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val pointLng = (item["lon"] ?: center?.get("lon"))?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val names = listOfNotNull(tag("name"), tag("name:fr")).map(::foldedName)
        Item(id, names.any { it in wanted }, tag("place") != null, distanceMeters(lat, lng, pointLat, pointLng))
    }
        .sortedWith(compareByDescending<Item> { it.sameName }.thenByDescending { it.isTown }.thenBy { it.meters })
        .map { it.id }
        .distinct()
        .take(max)
}.getOrNull()

/** « Île-Rousse » and « ile rousse » alike: no case, no accent, no dash. */
fun foldedName(text: String): String =
    java.text.Normalizer.normalize(text.trim().lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[\\s'’-]+"), " ")
        .trim()

/** The image (P18) of a Wikidata item, without the rest of the item. */
fun wikidataImageUrl(item: String): String =
    "https://www.wikidata.org/w/api.php?action=wbgetclaims&format=json&property=P18&entity=" + URLEncoder.encode(item, "UTF-8")

/** The Commons file of the first image of an item: « Ajaccio port.jpg »; null for none, a drawing, or unreadable. */
fun parseWikidataImage(json: String): String? = runCatching {
    val claims = Json.parseToJsonElement(json).jsonObject["claims"]?.jsonObject?.get("P18")?.jsonArray ?: return@runCatching null
    claims.firstNotNullOfOrNull { claim ->
        claim.jsonObject["mainsnak"]?.jsonObject?.get("datavalue")?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull
            ?.trim()?.takeIf(::isPhotoFile)
    }
}.getOrNull()

/**
 * One real photo (jpg, png, webp): never a drawing, a map or a flag (svg), never a montage of
 * several photos (retour du 25/11 soir: the infobox of a town is often « Montage Ajaccio.jpg »).
 */
fun isPhotoFile(file: String): Boolean =
    file.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp") } && !isMontageName(file)

/** The words of a file name that say it puts several photos together, in French, English, Italian, Spanish, German. */
private val MONTAGE_WORDS = Regex(
    "(montage|collage|mosa[iï]que|mosaic|mosaico|composite|compilation|collag|fotomontage|photomontage|kolaz|vues de |views of )",
    RegexOption.IGNORE_CASE,
)

/** « Montage Ajaccio.jpg », « Lyon collage 2.jpg », « Mosaïque de Nice.jpg »: several photos in one file. */
fun isMontageName(file: String): Boolean = MONTAGE_WORDS.containsMatchIn(file.replace('_', ' '))

/**
 * True when an image of [width] × [height] can fill a cover: a montage is often much taller than
 * wide (three or four photos stacked), a panorama much wider. Unknown sizes pass.
 */
fun hasCoverShape(width: Int?, height: Int?): Boolean {
    if (width == null || height == null || width <= 0 || height <= 0) return true
    val ratio = width.toDouble() / height
    return ratio in MIN_COVER_RATIO..MAX_COVER_RATIO
}

/**
 * Taller than 1 × 2 is a stack of photos more often than a photo; wider than 3 × 1 a panorama strip.
 * A photo in portrait (3 × 4, 2 × 3, 9 × 16: a lighthouse, a church, a street) passes: at 0,8 nearly
 * no photo was left (retour du 25/11, « les photos ne chargent plus »).
 */
const val MIN_COVER_RATIO = 0.5
const val MAX_COVER_RATIO = 3.0

/** A Commons file at [PHOTO_WIDTH] pixels with its author and licence, in one call. */
fun commonsPhotoUrl(file: String): String =
    "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2&prop=imageinfo" +
        "&iiprop=url%7Cextmetadata&iiurlwidth=$PHOTO_WIDTH&titles=" + URLEncoder.encode("File:$file", "UTF-8")

/** The photo of a Commons answer: its address at [PHOTO_WIDTH] and its credit; null when unreadable or off Commons. */
fun parseCommonsPhoto(json: String): PlacePhoto? = runCatching {
    val info = Json.parseToJsonElement(json).jsonObject["query"]?.jsonObject?.get("pages")?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("imageinfo")?.jsonArray?.firstOrNull()?.jsonObject ?: return@runCatching null
    val url = (info["thumburl"] ?: info["url"])?.jsonPrimitive?.contentOrNull
        ?.takeIf { it.startsWith("https://upload.wikimedia.org/") } ?: return@runCatching null
    val width = (info["thumbwidth"] ?: info["width"])?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    val height = (info["thumbheight"] ?: info["height"])?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    if (!hasCoverShape(width, height)) return@runCatching null
    PlacePhoto(url, parseCredit(json) ?: DEFAULT_CREDIT)
}.getOrNull()
