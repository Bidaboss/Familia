package fr.familia.app.feature.albums.book

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoBookCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `photos are kept until the cap, then no more`() {
        val folder = temp.newFolder("photo-book")
        assertTrue(PhotoBookCache.hasRoomFor(folder, 400, max = 1000))
        folder.resolve("a.jpg").writeBytes(ByteArray(400))
        folder.resolve("b.jpg").writeBytes(ByteArray(400))
        assertTrue(PhotoBookCache.hasRoomFor(folder, 200, max = 1000))
        assertFalse(PhotoBookCache.hasRoomFor(folder, 201, max = 1000))
    }

    @Test
    fun `a folder not there yet has all the room`() {
        assertTrue(PhotoBookCache.hasRoomFor(temp.root.resolve("absent"), 1000, max = 1000))
    }
}
