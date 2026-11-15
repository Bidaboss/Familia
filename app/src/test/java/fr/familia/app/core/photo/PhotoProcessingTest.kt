package fr.familia.app.core.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PhotoProcessingTest {

    /** Random coloured dots: the hardest kind of picture to compress. */
    private fun noisyPicture(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val random = Random(42)
        for (x in 0 until width step 4) for (y in 0 until height step 4) {
            paint.color = random.nextInt() or 0xFF000000.toInt()
            canvas.drawRect(x.toFloat(), y.toFloat(), x + 4f, y + 4f, paint)
        }
        return bitmap
    }

    @Test
    fun `a large picture becomes a square JPEG of at most 512 pixels and 100 KB`() {
        val jpeg = PhotoProcessing.encode(noisyPicture(1600, 1200), SquareCrop(1600, 1200))
        assertTrue("${jpeg.size} bytes", jpeg.size <= PhotoProcessing.MAX_PHOTO_BYTES)
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertEquals(decoded.width, decoded.height)
        assertTrue(decoded.width <= PhotoProcessing.PHOTO_SIDE)
    }

    @Test
    fun `a plain picture keeps the full 512 pixels`() {
        val plain = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF88AACC.toInt()) }
        val jpeg = PhotoProcessing.encode(plain, SquareCrop(1600, 1200))
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertEquals(512, decoded.width)
    }

    @Test
    fun `a small picture is never enlarged`() {
        val jpeg = PhotoProcessing.encode(noisyPicture(300, 200), SquareCrop(300, 200))
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertEquals(200, decoded.width)
        assertEquals(200, decoded.height)
    }
}
