package fr.familia.app.feature.trips.photos

import fr.familia.app.core.album.AlbumRights
import fr.familia.app.core.album.albumCaredForIds
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.isOwner
import fr.familia.app.feature.trips.isParticipant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/*
 * The photos of a trip (PRD, récit 15) are an album in trips/{tripId}: each participant, children
 * included, adds up to 30 photos of their own; the followers see them all. A photo is removed by
 * its author, a responsible adult of the child who added it, or the admin principal, never by the
 * creator of the trip when another adult added it (récit 121). The rules check the same.
 */

fun tripAlbumFolder(tripId: String): String = "trips/$tripId"

/** What [currentMemberId] may do in the album of [tripId], updated live. */
fun tripAlbumRights(trips: TripRepository, members: MemberRepository, tripId: String, currentMemberId: String): Flow<AlbumRights> =
    combine(trips.observeTrip(tripId), members.observeFamily()) { trip, family ->
        val me = family.members.firstOrNull { it.id == currentMemberId }
        if (trip == null || me == null) {
            AlbumRights(me = null, canAdd = false, canManage = false)
        } else {
            AlbumRights(me, canAdd = isParticipant(trip, me), canManage = family.isOwner(me), caredForIds = albumCaredForIds(family, me))
        }
    }
