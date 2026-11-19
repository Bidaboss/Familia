package fr.familia.app.feature.events

import fr.familia.app.core.model.Event
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.PlaceKind
import fr.familia.app.core.place.directionsUri
import fr.familia.app.core.place.directionsUriForAddress

/** The place under the time on the event's screen (récit 74), and where « Itinéraire » goes. */
data class EventPlaceView(val text: String, val directions: String?)

/**
 * « Chez un foyer » shows the household's name as it is now, and « Itinéraire » once the household
 * has an address in its Coordonnées (récit 66). « Adresse » always has its « Itinéraire ». « Autre »
 * (the free text of before) has one only with the place component on ([placesOn], récit 33).
 */
fun eventPlaceView(event: Event, households: List<Household>, placesOn: Boolean): EventPlaceView? = when (event.placeKind) {
    PlaceKind.Household -> {
        val household = households.firstOrNull { it.id == event.placeHouseholdId }
        val text = household?.name ?: event.place
        text?.let { EventPlaceView(it, household?.address?.trim()?.ifEmpty { null }?.let(::directionsUri)) }
    }
    PlaceKind.Address -> event.place?.let { EventPlaceView(it, directionsUriForAddress(it)) }
    PlaceKind.Other -> event.place?.let { EventPlaceView(it, if (placesOn) directionsUri(it) else null) }
}
