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

/**
 * Turns a photo chosen on the phone into the two JPEG files sent to Storage: the photo (longest
 * side 1600 px, about 300 KB) and its thumbnail (400 px, about 30 KB). Works for any shape of
 * photo, and for any module: the caller chooses where the files go. The files are encoded again
 * from the pixels, so they carry no EXIF data (no place, no phone model).
 */
class PhotoEncoder(private val context: Context) {

    /** (photo, thumbnail). Throws [IOException] when the file cannot be read as a picture. */
    suspend fun encodeForUpload(uri: Uri): Pair<ByteArray, ByteArray> = withContext(Dispatchers.IO) {
        val source = decode(uri)
        try {
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
     * One JPEG, longest side [maxSide], about [target] bytes and never above [limit]: a screenshot
     * of « Une idée ? Un souci ? » (récit 6 sexies). Throws [IOException] when it is not a picture.
     */
    suspend fun encodeSingle(uri: Uri, maxSide: Int, target: Int, limit: Int): ByteArray = withContext(Dispatchers.IO) {
        val source = decode(uri)
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

    /** Decodes [uri] a little above 1600 px, turned the right way up. */
    private fun decode(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds the decoder returns null and only fills [bounds].
        val stream = resolver.openInputStream(uri) ?: throw UnreadablePhotoException("Cannot open $uri")
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw UnreadablePhotoException("Not a picture: $uri")

        val options = BitmapFactory.Options().apply {
            inSampleSize = PhotoSizing.inSampleSize(bounds.outWidth, bounds.outHeight, PhotoSizing.PHOTO_MAX_SIDE)
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw UnreadablePhotoException("Not a picture: $uri")
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        return rotated(bitmap, orientation)
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
