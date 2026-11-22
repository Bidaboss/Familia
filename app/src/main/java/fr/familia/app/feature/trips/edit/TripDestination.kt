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
