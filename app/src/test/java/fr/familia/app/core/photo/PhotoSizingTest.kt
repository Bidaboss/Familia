package fr.familia.app.core.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSizingTest {

    @Test
    fun `a big photo keeps its shape with its longest side at 1600 px`() {
        assertEquals(1600 to 1200, PhotoSizing.scaledSize(4000, 3000, PhotoSizing.PHOTO_MAX_SIDE))
        assertEquals(900 to 1600, PhotoSizing.scaledSize(2250, 4000, PhotoSizing.PHOTO_MAX_SIDE))
        assertEquals(400 to 100, PhotoSizing.scaledSize(1600, 400, PhotoSizing.THUMB_MAX_SIDE))
    }

    @Test
    fun `a small photo is never enlarged`() {
        assertEquals(800 to 600, PhotoSizing.scaledSize(800, 600, PhotoSizing.PHOTO_MAX_SIDE))
    }

    @Test
    fun `a very thin panorama keeps at least one pixel`() {
        assertEquals(1600 to 1, PhotoSizing.scaledSize(20000, 2, PhotoSizing.PHOTO_MAX_SIDE))
    }

    @Test
    fun `the photo is decoded at a power of two still above 1600 px`() {
        assertEquals(2, PhotoSizing.inSampleSize(4000, 3000, 1600))
        assertEquals(4, PhotoSizing.inSampleSize(8000, 6000, 1600))
        // A 50 Mpx photo (8160 × 6120) is read at 2040 × 1530: about 12 MB, never 200.
        assertEquals(4, PhotoSizing.inSampleSize(8160, 6120, 1600))
        assertEquals(1, PhotoSizing.inSampleSize(1600, 1200, 1600))
        assertEquals(1, PhotoSizing.inSampleSize(800, 600, 1600))
        assertTrue(8000 / PhotoSizing.inSampleSize(8000, 6000, 1600) >= 1600)
    }

    @Test
    fun `the targets stay under the limits of storage rules`() {
        assertTrue(PhotoSizing.PHOTO_TARGET_BYTES < PhotoSizing.PHOTO_LIMIT_BYTES)
        assertTrue(PhotoSizing.THUMB_TARGET_BYTES < PhotoSizing.THUMB_LIMIT_BYTES)
        assertEquals(512 * 1024, PhotoSizing.PHOTO_LIMIT_BYTES)
        assertEquals(64 * 1024, PhotoSizing.THUMB_LIMIT_BYTES)
    }
}
