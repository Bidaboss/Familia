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
}
