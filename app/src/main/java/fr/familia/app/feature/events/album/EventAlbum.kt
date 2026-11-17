package fr.familia.app.feature.events.album

import fr.familia.app.core.album.AlbumRights
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.RsvpRepository
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.core.model.isOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import java.time.LocalDate

/*
 * The album of an event (PRD, récit 41, carte ENG-2): one album per event, opened on its day.
 * Those who said « je viens » add photos of their own (30 at most); everyone who sees the event
 * sees the album. The author of a photo, the author of the event or the admin principal removes
 * it. The rules check the same (firestore.rules, storage.rules). Hidden behind Feature.EventAlbums.
 */

fun eventAlbumFolder(eventId: String): String = "events/$eventId"

/** The album opens on the day of the event, so the photos come from the event itself. */
fun isAlbumOpen(event: Event, today: LocalDate): Boolean = !today.isBefore(event.date)

/** What [currentMemberId] may do in the album of [eventId], updated live. */
fun eventAlbumRights(
    events: EventRepository,
    members: MemberRepository,
    rsvps: RsvpRepository,
    eventId: String,
    currentMemberId: String,
    today: Flow<LocalDate>,
): Flow<AlbumRights> = combine(events.observeEvent(eventId), members.observeFamily(), rsvps.observeRsvps(eventId), today) { event, family, answers, day ->
    val me = family.members.firstOrNull { it.id == currentMemberId }
    if (event == null || me == null) {
        AlbumRights(me = null, canAdd = false, canManage = false)
    } else {
        val coming = answers.any { it.memberId == me.id && it.status == RsvpStatus.Yes }
        AlbumRights(me, canAdd = coming && isAlbumOpen(event, day), canManage = event.authorId == me.id || family.isOwner(me))
    }
}

/** All the photos of an event, with its film. */
@Serializable
data class EventAlbumDestination(val eventId: String, val photoId: String? = null)
