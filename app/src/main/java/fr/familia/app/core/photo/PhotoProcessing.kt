package fr.familia.app.core.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/**
 * Turns a picture from the gallery or the camera into a profile photo (PRD, récit 3 bis): upright,
 * cropped to the chosen square, [PHOTO_SIDE] pixels wide and at most [MAX_PHOTO_BYTES]. Call it off
 * the main thread.
 */
object PhotoProcessing {
    const val PHOTO_SIDE = 512
    const val MAX_PHOTO_BYTES = 100_000

    /** Longest side kept on screen while framing: enough for a sharp 512 px result. */
    private const val MAX_DECODED_SIDE = 2048

    /** The picture at [uri], turned upright and reduced to [MAX_DECODED_SIDE]; null if unreadable. */
    fun decode(resolver: ContentResolver, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODED_SIDE)
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        upright(bitmap, orientation)
    }.getOrNull()

    /** Largest power of two that keeps the longest side at [maxSide] or more. */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    private fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** The chosen square of [bitmap], as a JPEG of [PHOTO_SIDE] pixels and at most [MAX_PHOTO_BYTES]. */
    fun encode(bitmap: Bitmap, crop: SquareCrop): ByteArray {
        val square = crop.sourceSquare()
        val side = minOf(PHOTO_SIDE, square.side)
        val matrix = Matrix().apply { val s = side.toFloat() / square.side; setScale(s, s) }
        val cropped = Bitmap.createBitmap(bitmap, square.left, square.top, square.side, square.side, matrix, true)
        var quality = 85
        while (true) {
            val bytes = ByteArrayOutputStream().use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            if (bytes.size <= MAX_PHOTO_BYTES || quality <= 25) return bytes
            quality -= 15
        }
    }

    /** A stored photo, read at half size: plenty for an avatar and four times less memory. */
    fun decodeAvatar(jpeg: ByteArray): Bitmap? = runCatching {
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = 2 })
    }.getOrNull()
}
