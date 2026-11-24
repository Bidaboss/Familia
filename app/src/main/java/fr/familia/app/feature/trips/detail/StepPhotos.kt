package fr.familia.app.feature.trips.detail

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.albumPhotoOrder
import fr.familia.app.core.album.shotAt
import java.time.ZoneId

/** The photos of a trip by destination (récit 104, règle 31), and those that fall in none: « Sans étape ». */
data class StepPhotos(val byStay: Map<String, List<AlbumPhoto>>, val withoutStep: List<AlbumPhoto>)

/**
 * The gentle migration of récit 104: a photo without a step goes to the destination whose days hold
 * the day it was taken (else the day it was added), in the time of the phone. On the day one stay
 * ends and the next begins, the photo goes to the one that begins. Nothing is written: the photos
 * keep their records, and a photo out of every destination is « Sans étape ».
 */
fun stepPhotos(line: TripTimeline, photos: List<AlbumPhoto>, zone: ZoneId): StepPhotos {
    val stays = line.destinations
    val byStay = mutableMapOf<String, MutableList<AlbumPhoto>>()
    val without = mutableListOf<AlbumPhoto>()
    photos.sortedWith(albumPhotoOrder).forEach { photo ->
        val day = photo.shotAt?.atZone(zone)?.toLocalDate()
        val stay = day?.let { d ->
            stays.lastOrNull { !d.isBefore(it.start) && !d.isAfter(it.end) }
        }
        if (stay == null) without += photo else byStay.getOrPut(stay.step.id) { mutableListOf() } += photo
    }
    return StepPhotos(byStay, without)
}
