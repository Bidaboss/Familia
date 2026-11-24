package fr.familia.app.feature.trips.place

import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.model.TripPlace
import fr.familia.app.core.place.FoundPlace
import java.time.LocalTime

/**
 * Where a place is, as chosen in a search (règle 20) or as saved: its name then, its address and
 * its point. It stays true only while the typed name is still [name].
 */
data class PlacePick(val name: String, val address: String? = null, val lat: Double? = null, val lng: Double? = null)

/** What is being typed on the page of a place (récit 103, règle 21): its name, the hour, a note, a cost. */
data class PlaceForm(
    val name: String = "",
    val arriveTime: LocalTime? = null,
    val note: String = "",
    /** As typed: « 80 », « 12,50 »; empty for none. */
    val cost: String = "",
    /** The place chosen in the search, or the saved one; null when the name was only typed. */
    val pick: PlacePick? = null,
) {
    /** The place to save under [stepId], with the address and the point of [pick] when its name is still the one typed. */
    fun toPlace(id: String, stepId: String, kind: TripPlaceKind): TripPlace {
        // A new name is another place: its old address and point would lie.
        val where = pick?.takeIf { it.name == name.trim() }
        return TripPlace(
            id = id,
            stepId = stepId,
            kind = kind,
            name = name.trim(),
            address = where?.address,
            lat = where?.lat?.takeIf { where.lng != null },
            lng = where?.lng?.takeIf { where.lat != null },
            arriveTime = arriveTime,
            note = note.trim().ifEmpty { null },
            cost = parseCost(cost),
        )
    }

    /** [found] in place of the name and the location; the hour, the note and the cost stay. */
    fun choose(found: FoundPlace): PlaceForm = copy(
        name = found.name.take(MAX_PLACE_NAME),
        pick = PlacePick(found.name.take(MAX_PLACE_NAME), found.address, found.lat, found.lng),
    )

    companion object {
        fun from(place: TripPlace) = PlaceForm(
            name = place.name,
            arriveTime = place.arriveTime,
            note = place.note.orEmpty(),
            cost = place.cost?.let(::costText).orEmpty(),
            pick = PlacePick(place.name, place.address, place.lat, place.lng),
        )
    }
}

enum class PlaceFormError { NameMissing, NameTooLong, NoteTooLong, CostInvalid }

/** Same limits as the security rules. */
const val MAX_PLACE_NAME = 120
const val MAX_PLACE_NOTE = 600
const val MAX_PLACE_COST = 1_000_000.0

fun validate(form: PlaceForm): Set<PlaceFormError> = buildSet {
    val name = form.name.trim()
    if (name.isEmpty()) add(PlaceFormError.NameMissing)
    if (name.length > MAX_PLACE_NAME) add(PlaceFormError.NameTooLong)
    if (form.note.trim().length > MAX_PLACE_NOTE) add(PlaceFormError.NoteTooLong)
    if (form.cost.isNotBlank() && parseCost(form.cost) == null) add(PlaceFormError.CostInvalid)
}

/** « 12,50 », « 12.5 » or « 1 200 » as a number of at most two decimals; null when blank, negative, too big or not a number. */
fun parseCost(text: String): Double? {
    val clean = text.replace(Regex("[\\s\\u00A0\\u202F€$£]"), "").replace(',', '.')
    if (clean.isEmpty() || !clean.matches(Regex("^\\d+(\\.\\d{1,2})?$"))) return null
    return clean.toDoubleOrNull()?.takeIf { it in 0.0..MAX_PLACE_COST }
}

/** « 80 » or « 12,50 », as a French phone shows a price. */
fun costText(cost: Double): String =
    if (cost % 1.0 == 0.0) cost.toLong().toString() else String.format(java.util.Locale.FRANCE, "%.2f", cost)
