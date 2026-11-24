package fr.familia.app.feature.trips.map

import fr.familia.app.core.album.AlbumPhoto
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkerPhotosTest {
    private fun photo(slot: Int) = AlbumPhoto("trips/t/photos", "a", "A", slot, null)

    @Test
    fun `each placed step gets the thumbnail of its first photo, in the order of the rounds`() {
        val photos = mapOf("palmyre" to listOf(photo(2), photo(3)), "bordeaux" to listOf(photo(5)))
        assertEquals(
            listOf(null, "trips/t/photos/a/5-thumb.jpg", "trips/t/photos/a/2-thumb.jpg"),
            markerPhotoPaths(listOf("car", "bordeaux", "palmyre"), photos),
        )
    }

    @Test
    fun `no photo, no picture in the rounds`() {
        assertEquals(listOf<String?>(null, null), markerPhotoPaths(listOf("a", "b"), emptyMap()))
    }
}
