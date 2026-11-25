package fr.familia.app.feature.trips.enrich

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.model.TripStep
import fr.familia.app.feature.trips.detail.placeName
import fr.familia.app.feature.trips.detail.tripTimeline
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * « Enrichir avec une IA » (récit 106, règles 27 to 30). The family copies a prompt into the AI of
 * their choice and pastes the answer back. That answer is UNTRUSTED text: it is read here as strict
 * JSON with closed limits, every text is cut and cleaned, nothing with a link is kept, nothing is
 * opened or run, and nothing is written before the person has seen and ticked it.
 */
object TripEnrich {
    /** The longest answer read: well over what 15 destinations of 15 places need. */
    const val MAX_CHARS = 30_000
    const val MAX_DESTINATIONS = 15
    const val MAX_PLACES_PER_DESTINATION = 15
    const val NAME_MAX = 120
    const val ADDRESS_MAX = 200
    const val NOTE_MAX = 600
    const val COST_MAX = 1_000_000.0
}

/** Where the prompt is for: the whole trip, or one destination of it (règle 28). */
sealed interface EnrichTarget {
    data object Trip : EnrichTarget
    data class Destination(val stepId: String) : EnrichTarget
}

/** A place proposed by the AI, cleaned, under its destination. */
data class ProposedPlace(
    val kind: TripPlaceKind,
    val name: String,
    val address: String?,
    val arriveTime: LocalTime?,
    val note: String?,
    val cost: Double?,
)

/**
 * A destination of the answer: an existing one of the trip ([existingStepId], dates kept as they
 * are), or a new stay from [start] to [end]. [anchor] is the point of the existing one, read with it,
 * near which its places are looked for (règle 29, retour C2 de #579).
 */
data class ProposedDestination(
    val city: String,
    val existingStepId: String?,
    val start: LocalDate?,
    val end: LocalDate?,
    val places: List<ProposedPlace>,
    val anchor: Pair<Double, Double>? = null,
) {
    val isNew: Boolean get() = existingStepId == null
}

/** Why a part of the answer was left out, said in the preview. */
enum class SkipReason { Link, BadDates, OutsideTrip, Overlap, MissingName, BadKind, TooMany }

data class Skipped(val what: String, val reason: SkipReason)

/** The answer once read: what may be added, and what was left out. */
sealed interface EnrichReading {
    data class Read(val destinations: List<ProposedDestination>, val skipped: List<Skipped>) : EnrichReading {
        val count: Int get() = destinations.count { it.isNew } + destinations.sumOf { it.places.size }
    }

    /** Nothing usable: empty, too long, or not the JSON the prompt asks for. */
    enum class Refused : EnrichReading { Empty, TooLong, NotJson, NothingToAdd }
}

private val ISO_DAY: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val FRENCH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE)

/**
 * The prompt to copy (règle 28; règles de la réponse de l'admin, 25/11 soir : the AI first asks to
 * confirm, may be challenged, and gives the JSON in a block to copy once validated). It carries only what the AI needs: the destinations and the dates,
 * the number of travellers and the currency. Never a first name, never the title of the trip.
 */
fun enrichPrompt(trip: Trip, steps: List<TripStep>, target: EnrichTarget): String {
    val line = tripTimeline(trip, steps)
    val currency = trip.currency ?: "EUR"
    val travellers = trip.participantIds.size
    val placeFormat = """{"sorte": "hebergement" ou "restaurant" ou "spot", "nom": "…", "adresse": "…", "heure": "HH:MM" ou null, "note": "une phrase", "cout": nombre en $currency ou null}"""
    val rules = """
        |Règles de la réponse :
        |- Demande confirmation des étapes proposées.
        |- Je peux te challenger dans tes propositions.
        |- Lorsque je valide, propose un objet JSON valide, sans texte avant ni après, sans Markdown, dans un bloc prévu à copier par l'utilisateur via un bouton copier.
        |- Aucun lien, aucune adresse web, aucun code.
        |- Des lieux réels et connus ; si tu n'es pas sûr, n'en mets pas.
        |- « note » : 200 caractères au plus, en français.
    """.trimMargin()
    return when (target) {
        EnrichTarget.Trip -> {
            val known = if (line.destinations.isEmpty()) {
                "Aucune destination n'est encore prévue."
            } else {
                "Destinations déjà prévues :\n" + line.destinations.joinToString("\n") { d ->
                    "- ${placeName(d.step)} : du ${d.start.format(ISO_DAY)} au ${d.end.format(ISO_DAY)}"
                }
            }
            """
                |Nous préparons un voyage en famille ($travellers personnes) du ${trip.startDate.format(FRENCH_DAY)} au ${trip.endDate.format(FRENCH_DAY)}.
                |$known
                |
                |Propose le programme : pour chaque destination, un hébergement, quelques restaurants et quelques spots à voir. Tu peux ajouter des destinations dans les nuits encore libres, avec leurs dates (entre le ${trip.startDate.format(ISO_DAY)} et le ${trip.endDate.format(ISO_DAY)}). Garde le nom exact d'une destination déjà prévue.
                |
                |Format attendu :
                |{"destinations": [{"ville": "…", "du": "AAAA-MM-JJ", "au": "AAAA-MM-JJ", "lieux": [$placeFormat]}]}
                |
                |$rules
            """.trimMargin()
        }
        is EnrichTarget.Destination -> {
            val destination = line.destinations.firstOrNull { it.step.id == target.stepId }
            val city = destination?.let { placeName(it.step) } ?: steps.firstOrNull { it.id == target.stepId }?.let(::placeName).orEmpty()
            val dates = destination?.let { " du ${it.start.format(FRENCH_DAY)} au ${it.end.format(FRENCH_DAY)}" }.orEmpty()
            """
                |Nous serons en famille ($travellers personnes) à $city$dates.
                |
                |Propose un hébergement, quelques restaurants et quelques spots à voir à $city.
                |
                |Format attendu :
                |{"lieux": [$placeFormat]}
                |
                |$rules
            """.trimMargin()
        }
    }
}

/**
 * Reads the pasted [answer] for [target]. Only the keys of the prompt are read; anything else is
 * ignored. A destination named like an existing one of the trip enriches it, its dates untouched.
 */
fun readEnrichAnswer(answer: String, trip: Trip, steps: List<TripStep>, target: EnrichTarget): EnrichReading {
    val text = answer.trim()
    if (text.isEmpty()) return EnrichReading.Refused.Empty
    if (text.length > TripEnrich.MAX_CHARS) return EnrichReading.Refused.TooLong
    // An AI often wraps the object in a code block or a sentence: only the object is read.
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return EnrichReading.Refused.NotJson
    val root = try {
        Json.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject
    } catch (e: IllegalArgumentException) {
        null
    } ?: return EnrichReading.Refused.NotJson

    val skipped = mutableListOf<Skipped>()
    val stays = tripTimeline(trip, steps).destinations
    val destinations = when (target) {
        EnrichTarget.Trip -> {
            val list = root["destinations"] as? JsonArray ?: return EnrichReading.Refused.NotJson
            // The nights already held, from the first night to the day of leaving.
            val taken = stays.map { it.start to it.end }.toMutableList()
            if (list.size > TripEnrich.MAX_DESTINATIONS) skipped += Skipped("${list.size - TripEnrich.MAX_DESTINATIONS}", SkipReason.TooMany)
            list.take(TripEnrich.MAX_DESTINATIONS).mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val city = cleanText(item.text("ville"), TripEnrich.NAME_MAX)
                if (city == null) {
                    skipped += Skipped("", SkipReason.MissingName)
                    return@mapNotNull null
                }
                if (hasLink(city)) {
                    skipped += Skipped(city, SkipReason.Link)
                    return@mapNotNull null
                }
                val places = readPlaces(item["lieux"], skipped)
                val existing = stays.firstOrNull { sameName(placeName(it.step), city) }
                if (existing != null) return@mapNotNull ProposedDestination(placeName(existing.step), existing.step.id, null, null, places, anchorOf(existing.step))
                val from = day(item.text("du"))
                val to = day(item.text("au"))
                when {
                    from == null || to == null || to <= from -> {
                        skipped += Skipped(city, SkipReason.BadDates)
                        null
                    }
                    from < trip.startDate || to > trip.endDate -> {
                        skipped += Skipped(city, SkipReason.OutsideTrip)
                        null
                    }
                    // Two stays never share a night: neither an existing one, nor one of this answer.
                    taken.any { (start, end) -> from < end && start < to } -> {
                        skipped += Skipped(city, SkipReason.Overlap)
                        null
                    }
                    else -> {
                        taken += from to to
                        ProposedDestination(city, null, from, to, places)
                    }
                }
            }
        }
        is EnrichTarget.Destination -> {
            val step = steps.firstOrNull { it.id == target.stepId } ?: return EnrichReading.Refused.NothingToAdd
            val places = readPlaces(root["lieux"] ?: return EnrichReading.Refused.NotJson, skipped)
            listOf(ProposedDestination(placeName(step), step.id, null, null, places, anchorOf(step)))
        }
    }.filter { it.isNew || it.places.isNotEmpty() }
    if (destinations.isEmpty()) return if (skipped.isEmpty()) EnrichReading.Refused.NothingToAdd else EnrichReading.Read(emptyList(), skipped)
    return EnrichReading.Read(destinations, skipped)
}

private fun anchorOf(step: TripStep): Pair<Double, Double>? {
    val lat = step.lat ?: return null
    val lng = step.lng ?: return null
    return lat to lng
}

private fun readPlaces(element: JsonElement?, skipped: MutableList<Skipped>): List<ProposedPlace> {
    val list = element as? JsonArray ?: return emptyList()
    if (list.size > TripEnrich.MAX_PLACES_PER_DESTINATION) skipped += Skipped("${list.size - TripEnrich.MAX_PLACES_PER_DESTINATION}", SkipReason.TooMany)
    return list.take(TripEnrich.MAX_PLACES_PER_DESTINATION).mapNotNull { e ->
        val item = e as? JsonObject ?: return@mapNotNull null
        val name = cleanText(item.text("nom"), TripEnrich.NAME_MAX)
        if (name == null) {
            skipped += Skipped("", SkipReason.MissingName)
            return@mapNotNull null
        }
        val kind = when (item.text("sorte")?.let(::folded)) {
            "hebergement", "logement", "hotel" -> TripPlaceKind.Lodging
            "restaurant" -> TripPlaceKind.Restaurant
            "spot", "visite", "activite" -> TripPlaceKind.Spot
            else -> null
        }
        if (kind == null) {
            skipped += Skipped(name, SkipReason.BadKind)
            return@mapNotNull null
        }
        val address = cleanText(item.text("adresse"), TripEnrich.ADDRESS_MAX)
        val note = cleanText(item.text("note"), TripEnrich.NOTE_MAX)
        if (listOfNotNull(name, address, note).any(::hasLink)) {
            skipped += Skipped(name, SkipReason.Link)
            return@mapNotNull null
        }
        val cost = (item["cout"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() && it in 0.0..TripEnrich.COST_MAX }
        ProposedPlace(kind, name, address, time(item.text("heure")), note, cost)
    }
}

/** The text of [key], or null when it is missing, null or not a text. */
private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * One line of plain text: control characters and the characters of a bidi trick removed, spaces
 * folded, cut to [max]. Null when nothing is left.
 */
internal fun cleanText(text: String?, max: Int): String? {
    if (text == null) return null
    val plain = text
        .replace(Regex("[\\p{Cc}\\p{Cf}]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return plain.take(max).trim().ifEmpty { null }
}

/** A link of any kind, written in full or not: nothing of it is kept (règle 29, « aucun lien »). */
internal fun hasLink(text: String): Boolean =
    LINK.containsMatchIn(text)

private val LINK = Regex(
    "(?i)([a-z][a-z0-9+.-]*://|www\\.|\\b[a-z0-9-]+\\.(com|fr|net|org|io|be|ch|es|it|de|uk|co|app|info|link|ly|me)\\b|\\bjavascript:|\\bdata:|mailto:)",
)

private fun day(text: String?): LocalDate? = try {
    text?.trim()?.let { LocalDate.parse(it, ISO_DAY) }
} catch (e: DateTimeParseException) {
    null
}

private fun time(text: String?): LocalTime? {
    val match = Regex("^([01]?[0-9]|2[0-3])[:h]([0-5][0-9])$").matchEntire(text?.trim().orEmpty()) ?: return null
    return LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
}

private fun folded(text: String): String =
    Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT)

private fun sameName(a: String, b: String): Boolean = folded(a) == folded(b)

/** The new stay of [destination], for the trip (a step « Séjour », règle 16). */
fun ProposedDestination.toStay(id: String): TripStep =
    TripStep(
        id = id,
        type = StepType.Lodging,
        place = city,
        departDate = requireNotNull(start),
        departTime = null,
        arriveDate = requireNotNull(end),
        arriveTime = null,
        note = null,
    )
