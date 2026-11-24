package fr.familia.app.feature.trips.edit

import fr.familia.app.core.place.Place

/**
 * The name a trip takes from its destination (récit 95): the first part of the address, « La
 * Palmyre » out of « La Palmyre, Les Mathes, France », at most [MAX_TRIP_TITLE] characters. The
 * member can still change it.
 */
fun destinationName(place: Place): String {
    val text = place.title.orEmpty()
    val first = text.substringBefore(',').trim().ifEmpty { text.trim() }
    return first.take(MAX_TRIP_TITLE)
}

/** What the phone's geocoder gave for a search: the places, or a reason why there is none. */
sealed interface DestinationSearch {
    data object Idle : DestinationSearch
    data object Searching : DestinationSearch
    data class Found(val places: List<Place>) : DestinationSearch
    data object NothingFound : DestinationSearch
}

/** DESIGN-103 A: the places come while the member types, from this many letters. */
const val SUGGEST_MIN_CHARS = 3

/** Typing pauses this long before the phone's geocoder is asked: one question per pause, not per letter. */
const val SUGGEST_PAUSE_MS = 450L

/** What to ask the geocoder while the member types: the trimmed text, or null when it is too short or a place is chosen. */
fun suggestionQuery(typed: String, chosen: Place?): String? =
    typed.trim().takeIf { chosen == null && it.length >= SUGGEST_MIN_CHARS }
