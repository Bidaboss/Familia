package fr.familia.app.core.data

import fr.familia.app.core.model.TravelPlace
import kotlinx.coroutines.flow.Flow

/**
 * The destinations a member added by hand to their « Carnet de baroudeur » (récit 107), in
 * `members/{memberId}/travelPlaces/{placeId}`. Private: only that member reads and writes them.
 * They go with the member (server). See docs/data-model.md.
 */
interface TravelPlaceRepository {
    /** The member's hand-added destinations, most recent first, updated live; none for anyone else. */
    fun observePlaces(memberId: String): Flow<List<TravelPlace>>

    /** Adds [place] (its id is ignored, a new one is made). */
    suspend fun addPlace(memberId: String, place: TravelPlace)

    suspend fun deletePlace(memberId: String, placeId: String)

    /**
     * « Ajouter un voyage »: adds [places] as one trip named [name] (null: the first place), all at
     * once where the store allows it. Their ids, [TravelPlace.tripId] and [TravelPlace.tripName] are made here.
     */
    suspend fun addTrip(memberId: String, places: List<TravelPlace>, name: String?) {
        places.forEach { addPlace(memberId, it.copy(tripName = name)) }
    }

    /**
     * « Modifier le voyage »: [oldIds] give way to [places], named [name], as one trip, all at once
     * where the store allows it. The places are never changed in place (rules): they are replaced.
     */
    suspend fun replaceTrip(memberId: String, oldIds: List<String>, places: List<TravelPlace>, name: String?) {
        addTrip(memberId, places, name)
        deletePlaces(memberId, oldIds)
    }

    /** Removes a whole trip: each of its places. */
    suspend fun deletePlaces(memberId: String, placeIds: List<String>) {
        placeIds.forEach { deletePlace(memberId, it) }
    }
}
