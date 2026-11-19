package fr.familia.app.core.model

import java.time.LocalDate
import java.time.LocalTime

/** Who an event is for (PRD, récit 2). Stored on the event so the security rules can check it. */
sealed interface EventTarget {
    data class ForSelf(val memberId: String) : EventTarget
    data class ForChild(val memberId: String) : EventTarget
    data class ForHousehold(val householdId: String) : EventTarget
    data object ForFamily : EventTarget
}

/**
 * One event of the planning. [authorName] is the author's first name when the event was created,
 * shown only once the author has been removed from the family. [coverPhoto]: the base of its cover
 * in Storage (core/photo/SinglePhoto, PRD récit 44), or null. [seriesId] and [occurrence]: for an
 * event that comes back (récit 54), its series and the day of the series it stands for.
 */
data class Event(
    val id: String,
    val title: String,
    val date: LocalDate,
    val time: LocalTime?,
    val place: String?,
    val note: String?,
    val target: EventTarget,
    val authorId: String,
    val authorName: String,
    val coverPhoto: String? = null,
    val seriesId: String? = null,
    val occurrence: LocalDate? = null,
    /** When it was put in the Planning (null before the server time arrives): the date of its card in the fil (récit 67). */
    val createdAt: java.time.Instant? = null,
    /** What [place] is (récit 74): free text, an address, or a household of the family. */
    val placeKind: PlaceKind = PlaceKind.Other,
    /** The household of « Chez un foyer »; [place] then holds its name, for the screens and old apps. */
    val placeHouseholdId: String? = null,
)

/**
 * Where an event takes place (récit 74). [Other] is the free text of before (an event without the
 * field), [Address] an address that « Itinéraire » hands to Maps or Waze, [Household] « Chez un foyer »,
 * whose address comes from the household's Coordonnées (récit 66).
 */
enum class PlaceKind(val firestoreValue: String?) {
    Other(null), Address("address"), Household("household");

    companion object {
        fun from(value: String?): PlaceKind = entries.firstOrNull { it.firestoreValue != null && it.firestoreValue == value } ?: Other
    }
}
