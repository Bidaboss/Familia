package fr.familia.app.core.model

import java.time.LocalTime

/** The three kinds of place a destination programmes (récit 103, règles 18 to 21), [key] as stored in Firestore. */
enum class PlaceKind(val key: String) {
    Lodging("lodging"),
    Restaurant("restaurant"),
    Spot("spot"),
    ;

    companion object {
        fun of(key: String?): PlaceKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A place programmed under a destination of a trip (récit 103): where to sleep, where to eat, what
 * to see. [stepId] is the destination (a step of the trip). [arriveTime] and [note] are what the
 * page of the place asks (règle 21); [cost] is optional, in the currency of the trip (règle 40).
 * Kept in `trips/{tripId}/places/{placeId}`: read by the followers, written by the participants,
 * as the steps.
 */
data class TripPlace(
    val id: String,
    val stepId: String,
    val kind: PlaceKind,
    val name: String,
    val address: String?,
    val lat: Double?,
    val lng: Double?,
    val arriveTime: LocalTime?,
    val note: String?,
    val cost: Double? = null,
) {
    val hasPosition: Boolean get() = lat != null && lng != null
}
