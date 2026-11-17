package fr.familia.app.core.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Turns a photo chosen on the phone into the two JPEG files sent to Storage: the photo (longest
 * side 1600 px, about 300 KB) and its thumbnail (400 px, about 30 KB). Works for any shape of
 * photo, and for any module: the caller chooses where the files go. The files are encoded again
 * from the pixels, so they carry no EXIF data (no place, no phone model).
 */
class PhotoEncoder(private val context: Context) {

    /** (photo, thumbnail). Throws [IOException] when the file cannot be read as a picture. */
    suspend fun encodeForUpload(uri: Uri): Pair<ByteArray, ByteArray> = withContext(Dispatchers.IO) {
        // Short of memory even at a small size: this photo is skipped, the others go on.
        try {
            encodeNow(uri)
        } catch (e: OutOfMemoryError) {
            throw UnreadablePhotoException("Too big for this phone: $uri")
        }
    }

    private fun encodeNow(uri: Uri): Pair<ByteArray, ByteArray> {
        val source = decode(uri)
        return try {
            val photo = scaled(source, PhotoSizing.PHOTO_MAX_SIDE)
            try {
                val photoBytes = compress(photo, PhotoSizing.PHOTO_TARGET_BYTES, PhotoSizing.PHOTO_LIMIT_BYTES)
                val thumb = scaled(photo, PhotoSizing.THUMB_MAX_SIDE)
                try {
                    photoBytes to compress(thumb, PhotoSizing.THUMB_TARGET_BYTES, PhotoSizing.THUMB_LIMIT_BYTES)
                } finally {
                    if (thumb !== photo) thumb.recycle()
                }
            } finally {
                if (photo !== source) photo.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    /**
     * When the photo was taken, read from its EXIF (DateTimeOriginal, with its offset when the
     * phone wrote one, else the time of France), or null. Only the date is kept, never the place.
     */
    suspend fun takenAt(uri: Uri): Instant? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exifTakenAt(
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
                    exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
                )
            }
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            // A damaged EXIF block: the photo still goes, without its date.
            null
        }
    }

    /**
     * One JPEG, longest side [maxSide], about [target] bytes and never above [limit]: a screenshot
     * of « Une idée ? Un souci ? » (récit 6 sexies). Throws [IOException] when it is not a picture.
     */
    suspend fun encodeSingle(uri: Uri, maxSide: Int, target: Int, limit: Int): ByteArray = withContext(Dispatchers.IO) {
        val source = decode(uri, maxOf(maxSide, 1))
        try {
            val picture = scaled(source, maxSide)
            try {
                compress(picture, target, limit)
            } finally {
                if (picture !== source) picture.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    /**
     * Decodes [uri] with its longest side at most [maxSide], turned the right way up. The picture is
     * made smaller before it is turned, so a 50 Mpx photo never holds more than two small copies in
     * memory; a phone short of memory gets it at half the size again, rather than a crash.
     */
    private fun decode(uri: Uri, maxSide: Int = PhotoSizing.PHOTO_MAX_SIDE): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds the decoder returns null and only fills [bounds].
        // A video or a file the phone cannot read as a picture has no size: another photo is needed.
        val stream = resolver.openInputStream(uri) ?: throw UnreadablePhotoException("Cannot open $uri")
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw UnreadablePhotoException("Not a picture: $uri")

        var sample = PhotoSizing.inSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        var bitmap: Bitmap? = null
        for (attempt in 0 until MEMORY_ATTEMPTS) {
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            try {
                bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                break
            } catch (e: OutOfMemoryError) {
                sample *= 2
            }
        }
        val decoded = bitmap ?: throw UnreadablePhotoException("Not a picture, or too big for this phone: $uri")
        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        } catch (e: RuntimeException) {
            // A damaged EXIF block: the photo still goes, as it was taken.
            ExifInterface.ORIENTATION_NORMAL
        }
        val small = scaled(decoded, maxSide)
        if (small !== decoded) decoded.recycle()
        return rotated(small, orientation)
    }

    private fun rotated(bitmap: Bitmap, orientation: Int): Bitmap {
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
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (turned !== bitmap) bitmap.recycle()
        return turned
    }

    private fun scaled(bitmap: Bitmap, maxSide: Int): Bitmap {
        val (width, height) = PhotoSizing.scaledSize(bitmap.width, bitmap.height, maxSide)
        if (width == bitmap.width && height == bitmap.height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /**
     * The best quality under [target] bytes. A very detailed photo still above [limit] at the
     * lowest quality is made smaller, so the server never refuses it.
     */
    private fun compress(bitmap: Bitmap, target: Int, limit: Int): ByteArray {
        var current = bitmap
        while (true) {
            var bytes = ByteArray(0)
            for (quality in PhotoSizing.QUALITIES) {
                bytes = jpeg(current, quality)
                if (bytes.size <= target) break
            }
            if (bytes.size <= limit || current.width < 64 || current.height < 64) {
                if (current !== bitmap) current.recycle()
                return bytes
            }
            val smaller = Bitmap.createScaledBitmap(current, current.width * 4 / 5, current.height * 4 / 5, true)
            if (current !== bitmap) current.recycle()
            current = smaller
        }
    }

    private fun jpeg(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
}

/** Tries to decode a picture, at half the size each time, before giving up on a phone short of memory. */
private const val MEMORY_ATTEMPTS = 3

private val exifFormat = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

/** A photo taken this recently goes without takenAt. */
private val RECENT: Duration = Duration.ofMinutes(10)

/** « 2025:08:04 18:30:12 » and « +02:00 » as an instant; null when absent, damaged or of the last 10 minutes. */

fun exifTakenAt(dateTime: String?, offset: String?, now: Instant = Instant.now()): Instant? {
    val local = try {
        LocalDateTime.parse(dateTime?.trim() ?: return null, exifFormat)
    } catch (e: DateTimeParseException) {
        return null
    }
    val zone = try {
        offset?.trim()?.let { ZoneOffset.of(it) } ?: ZoneId.of("Europe/Paris")
    } catch (e: DateTimeException) {
        ZoneId.of("Europe/Paris")
    }
    val instant = local.atZone(zone).toInstant()
    // A clock never set (1970, 2000) or a date to come says nothing about the day of the photo.
    // A photo of the last 10 minutes needs no date: createdAt says the same day, and the server's
    // clock may be a little behind the phone's.
    return instant.takeIf { local.year >= 2001 && it.isBefore(now.minus(RECENT)) }
}
