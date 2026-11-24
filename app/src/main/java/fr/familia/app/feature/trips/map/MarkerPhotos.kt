package fr.familia.app.feature.trips.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlin.math.max

/**
 * The photo of a step in its round marker (récit 110, règle 7): the first photo of the destination
 * that the person looking can see, cut in a circle with a white ring (yellow for the next step) and
 * the number of the step in a small dark round. Drawn once per photo, on the phone.
 */
object MarkerPhotos {
    /** The diameter of a marker with a photo, in dp: bigger for the next step, as the plain rounds. */
    const val SIZE_DP = 36
    const val NEXT_SIZE_DP = 48

    /** The picture of [url] as a marker of [sizeDp] dp, or null when it cannot be loaded. */
    suspend fun load(context: Context, url: String, sizeDp: Int, label: String, next: Boolean): Bitmap? {
        val density = context.resources.displayMetrics.density
        val sizePx = max(1, (sizeDp * density).toInt())
        val photo = try {
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context).data(url).size(sizePx).allowHardware(false).build(),
            )
            (result as? SuccessResult)?.drawable?.toBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        return runCatching { draw(photo, sizePx, density, label, next) }.getOrNull()
    }

    private fun draw(photo: Bitmap, sizePx: Int, density: Float, label: String, next: Boolean): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val ring = (if (next) 4f else 3f) * density
        val radius = sizePx / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // The ring: white, or yellow for the next step, as the plain rounds.
        paint.color = if (next) 0xFFFFD84A.toInt() else 0xFFFFFFFF.toInt()
        canvas.drawCircle(radius, radius, radius, paint)
        // The photo, centred and cropped to a circle inside the ring.
        val inner = radius - ring
        val scale = (2 * inner) / minOf(photo.width, photo.height).toFloat()
        val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(
                Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(radius - photo.width * scale / 2f, radius - photo.height * scale / 2f)
                },
            )
        }
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = shader
        canvas.drawCircle(radius, radius, inner, paint)
        paint.shader = null
        // The number of the step (none for the departure, ✓ once arrived) in a small dark round.
        if (label.isNotEmpty()) {
            val badge = 9f * density
            val cx = sizePx - badge
            val cy = sizePx - badge
            paint.color = 0xFF111111.toInt()
            canvas.drawCircle(cx, cy, badge, paint)
            paint.color = 0xFFFFFFFF.toInt()
            paint.textSize = 11f * density
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(label, cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)
        }
        return out
    }
}

/** The thumbnail of the first photo of each placed step, in their order; null for a step without photo. */
fun markerPhotoPaths(stepIds: List<String>, photos: Map<String, List<fr.familia.app.core.album.AlbumPhoto>>): List<String?> =
    stepIds.map { id -> photos[id]?.firstOrNull()?.thumbnailPath }
