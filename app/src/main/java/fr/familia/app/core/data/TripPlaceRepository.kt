package fr.familia.app.core.data

import fr.familia.app.core.model.TripPlace
import kotlinx.coroutines.flow.Flow

/**
 * The places programmed under the destinations of a trip (récit 103), in
 * `trips/{tripId}/places/{placeId}`. The followers read them; the participants add, change and
 * delete them. A place goes with its destination and with its trip (server). See docs/data-model.md.
 */
interface TripPlaceRepository {
    /** The places of the trip, updated live. A member who may not read them gets none. */
    fun observePlaces(tripId: String): Flow<List<TripPlace>>

    /** One place, or null when it is gone or may not be read. */
    suspend fun getPlace(tripId: String, placeId: String): TripPlace?

    /** A new id, known before the first write (kept with the draft). */
    fun newPlaceId(tripId: String): String

    suspend fun savePlace(tripId: String, place: TripPlace, isNew: Boolean)

    suspend fun deletePlace(tripId: String, placeId: String)
}
