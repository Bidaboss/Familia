package fr.familia.app.feature.trips.detail

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.albumPhotoOrder
import fr.familia.app.core.album.shotAt
import java.time.ZoneId

/** The photos of a trip by destination (récit 104, règle 31), and those that fall in none: « Sans étape ». */
data class StepPhotos(val byStay: Map<String, List<AlbumPhoto>>, val withoutStep: List<AlbumPhoto>)

/**
 * A photo added from a destination goes under it (récit 104, partie 2). The gentle migration of
 * récit 104: a photo without a step, or whose step was removed, goes to the destination whose days hold
 * the day it was taken (else the day it was added), in the time of the phone. On the day one stay
 * ends and the next begins, the photo goes to the one that begins. Nothing is written: the photos
 * keep their records, and a photo out of every destination is « Sans étape ».
 */
fun stepPhotos(line: TripTimeline, photos: List<AlbumPhoto>, zone: ZoneId): StepPhotos {
    val stays = line.destinations
    val byStay = mutableMapOf<String, MutableList<AlbumPhoto>>()
    val without = mutableListOf<AlbumPhoto>()
    photos.sortedWith(albumPhotoOrder).forEach { photo ->
        // Added from a destination (partie 2): under it, while it is still in the trip.
        val chosen = photo.stepId?.let { id -> stays.firstOrNull { it.step.id == id } }
        val day = photo.shotAt?.atZone(zone)?.toLocalDate()
        val stay = chosen ?: day?.let { stayOn(line, it) }
        if (stay == null) without += photo else byStay.getOrPut(stay.step.id) { mutableListOf() } += photo
    }
    return StepPhotos(byStay, without)
}

/**
 * The destination of [today] during the trip, where « ajout rapide » files what is added (récit 104,
 * règle 35, DESIGN-90 A): on the day of a move, the one that begins. Null out of every destination.
 */
fun currentStay(line: TripTimeline, today: java.time.LocalDate): TimelineRow.Destination? = stayOn(line, today)

/** The destination whose days hold [day]; on the day of a move, the one that begins. */
fun stayOn(line: TripTimeline, day: java.time.LocalDate): TimelineRow.Destination? =
    line.destinations.lastOrNull { !day.isBefore(it.start) && !day.isAfter(it.end) }
