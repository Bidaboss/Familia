package fr.familia.app.feature.trips.place

import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.model.TripPlace
import java.time.LocalTime

/** What is being typed on the page of a place (récit 103, règle 21): its name, the hour, a note, a cost. */
data class PlaceForm(
    val name: String = "",
    val arriveTime: LocalTime? = null,
    val note: String = "",
    /** As typed: « 80 », « 12,50 »; empty for none. */
    val cost: String = "",
) {
    /** The place to save under [stepId]; the address and the point stay those of [before]. */
    fun toPlace(id: String, stepId: String, kind: TripPlaceKind, before: TripPlace?): TripPlace {
        val sameName = before != null && before.name == name.trim()
        return TripPlace(
            id = id,
            stepId = stepId,
            kind = kind,
            name = name.trim(),
            // A new name is another place: its old address and point would lie.
            address = before?.address?.takeIf { sameName },
            lat = before?.lat?.takeIf { sameName },
            lng = before?.lng?.takeIf { sameName },
            arriveTime = arriveTime,
            note = note.trim().ifEmpty { null },
            cost = parseCost(cost),
        )
    }

    companion object {
        fun from(place: TripPlace) = PlaceForm(
            name = place.name,
            arriveTime = place.arriveTime,
            note = place.note.orEmpty(),
            cost = place.cost?.let(::costText).orEmpty(),
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
