package fr.familia.app.feature.trips.word

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.video.FilmStyle
import fr.familia.app.core.video.filmZoomAt
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class StepFilmTest {
    private fun photo(slot: Int, stepId: String?) = AlbumPhoto("trips/t1", "adult1", "adult1", slot, Instant.ofEpochSecond(slot.toLong()), stepId = stepId)

    @Test fun theFilmOfAStepTakesOnlyItsPhotos() {
        val album = listOf(photo(1, "s1"), photo(2, "s2"), photo(3, null), photo(4, "s1"))
        assertEquals(listOf(1, 4), stepFilmPhotos(album, "s1").map { it.slot })
        assertEquals(emptyList<Int>(), stepFilmPhotos(album, "absent").map { it.slot })
    }

    @Test fun eachPhotoZoomsSlowlyFromItsStart() {
        val photoUs = 2_500_000L
        assertEquals(1f, filmZoomAt(0L, photoUs, 1.08f), 0.0001f)
        assertEquals(1.04f, filmZoomAt(1_250_000L, photoUs, 1.08f), 0.0001f)
        // The next photo starts again from 1, whether the time counts from the photo or from the film.
        assertEquals(1f, filmZoomAt(photoUs, photoUs, 1.08f), 0.0001f)
        assertEquals(1.04f, filmZoomAt(photoUs + 1_250_000L, photoUs, 1.08f), 0.0001f)
    }

    @Test fun albumsKeepTheirStillPhotos() {
        assertEquals(1f, filmZoomAt(1_000_000L, 2_500_000L, FilmStyle.Album.zoomTo), 0.0f)
        assertEquals(null, FilmStyle.Album.bitrate)
        assertEquals(1_500_000, FilmStyle.Trip.bitrate)
    }

    @Test fun aStepFilmIsLightEnoughToSend() {
        // 45 s at the bit rate of the trips: under 9 Mo (étude du film, § 3).
        val bytes = 45 * FilmStyle.Trip.bitrate!! / 8
        assertEquals(true, bytes <= 9 * 1024 * 1024)
        assertEquals(2.5, STEP_FILM_SECONDS, 0.0)
    }
}
