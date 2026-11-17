package fr.familia.app.core.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The photos of the evening of the 21, chosen from small phones: a big photo, a screenshot, a
 * photo turned by its EXIF, a video chosen by mistake. Each becomes the two files sent, upright,
 * never above the limits of storage.rules; a file that is not a picture is said so, without a crash.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PhotoEncoderTest {

    private val context = RuntimeEnvironment.getApplication()
    private val encoder = PhotoEncoder(context)

    /** A picture with its top half red and its bottom half blue, to see which way it is turned. */
    private fun picture(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLUE)
        canvas.drawRect(0f, 0f, width.toFloat(), height / 2f, Paint().apply { color = Color.RED })
        return bitmap
    }

    private fun file(name: String, bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): File =
        File(context.cacheDir, name).apply { outputStream().use { bitmap.compress(format, 90, it) } }

    private fun decoded(bytes: ByteArray): Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    @Test
    fun `a big photo becomes 1600 px and a 400 px thumbnail, under the limits`() = runTest {
        val big = file("big.jpg", picture(4000, 3000))
        val (photo, thumb) = encoder.encodeForUpload(Uri.fromFile(big))
        assertEquals(1600, decoded(photo).width)
        assertEquals(1200, decoded(photo).height)
        assertEquals(400, decoded(thumb).width)
        assertTrue("${photo.size}", photo.size <= PhotoSizing.PHOTO_LIMIT_BYTES)
        assertTrue("${thumb.size}", thumb.size <= PhotoSizing.THUMB_LIMIT_BYTES)
    }

    @Test
    fun `a photo taken sideways is sent upright, as its EXIF says`() = runTest {
        // The phone stored it lying down (red on the top) and wrote « turn it a quarter to the right ».
        val sideways = file("sideways.jpg", picture(800, 400))
        ExifInterface(sideways.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val photo = decoded(encoder.encodeForUpload(Uri.fromFile(sideways)).first)
        assertEquals(400, photo.width)
        assertEquals(800, photo.height)
        // Turned to the right: the red half is now on the right side.
        assertEquals(Color.RED, nearest(photo.getPixel(photo.width - 10, photo.height / 2)))
        assertEquals(Color.BLUE, nearest(photo.getPixel(10, photo.height / 2)))
    }

    @Test
    fun `a screenshot, a tall PNG, becomes a JPEG at most 1600 px high`() = runTest {
        val screenshot = file("screenshot.png", picture(1080, 2400), Bitmap.CompressFormat.PNG)
        val photo = decoded(encoder.encodeForUpload(Uri.fromFile(screenshot)).first)
        assertEquals(1600, photo.height)
        assertEquals(720, photo.width)
    }

    @Test
    fun `a video chosen by mistake is said unreadable, without a crash`() = runTest {
        // The start of an MP4 file: no picture in it.
        val video = File(context.cacheDir, "video.mp4").apply {
            writeBytes(byteArrayOf(0, 0, 0, 0x18) + "ftypmp42".toByteArray() + ByteArray(2_000) { it.toByte() })
        }
        val failure = runCatching { encoder.encodeForUpload(Uri.fromFile(video)) }.exceptionOrNull()
        assertTrue("$failure", failure is UnreadablePhotoException)
    }

    @Test
    fun `a photo with a damaged EXIF still goes, as it was taken`() = runTest {
        val photo = file("damaged.jpg", picture(600, 400))
        // A JPEG with garbage where the EXIF block of the phone would be.
        val bytes = photo.readBytes()
        val damaged = File(context.cacheDir, "damaged-exif.jpg").apply {
            writeBytes(bytes.copyOfRange(0, 2) + byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0x00, 0x10) + "Exif\u0000\u0000garbage!".toByteArray() + bytes.copyOfRange(2, bytes.size))
        }
        val sent = decoded(encoder.encodeForUpload(Uri.fromFile(damaged)).first)
        assertEquals(600, sent.width)
    }

    /** Red or blue, whichever is closer: JPEG blurs the colors a little. */
    private fun nearest(pixel: Int): Int = if (Color.red(pixel) > Color.blue(pixel)) Color.RED else Color.BLUE
}
