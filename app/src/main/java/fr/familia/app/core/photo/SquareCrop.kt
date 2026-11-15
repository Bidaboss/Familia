package fr.familia.app.core.photo

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The square part of a picture that becomes a profile photo. The picture first covers the square
 * frame (the whole frame is filled), then [zoom] (1 to [MAX_ZOOM]) and the offsets move it. Offsets
 * are in frame units (1 = the frame side), measured from the centered position, and always kept so
 * the picture still covers the frame. Pure maths, so the unit tests check it without a screen.
 */
data class SquareCrop(
    val imageWidth: Int,
    val imageHeight: Int,
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    init {
        require(imageWidth > 0 && imageHeight > 0) { "Empty picture" }
    }

    /** Frame units per picture pixel. */
    val scale: Float get() = max(1f / imageWidth, 1f / imageHeight) * zoom

    /** Size of the drawn picture, in frame units (at least 1 on each side). */
    val drawnWidth: Float get() = imageWidth * scale
    val drawnHeight: Float get() = imageHeight * scale

    /** Top left corner of the drawn picture, in frame units from the top left of the frame. */
    val drawnLeft: Float get() = (1f - drawnWidth) / 2f + offsetX
    val drawnTop: Float get() = (1f - drawnHeight) / 2f + offsetY

    /** Applies a drag of ([panX], [panY]) frame units and a pinch of [zoomChange]. */
    fun transform(panX: Float, panY: Float, zoomChange: Float = 1f): SquareCrop {
        val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        return copy(zoom = newZoom, offsetX = offsetX + panX, offsetY = offsetY + panY).clamped()
    }

    private fun clamped(): SquareCrop {
        val maxX = (drawnWidth - 1f) / 2f
        val maxY = (drawnHeight - 1f) / 2f
        return copy(offsetX = offsetX.coerceIn(-maxX, maxX), offsetY = offsetY.coerceIn(-maxY, maxY))
    }

    /** The chosen square in picture pixels: left, top and side, always inside the picture. */
    fun sourceSquare(): PixelSquare {
        val side = (1f / scale).roundToInt().coerceIn(1, minOf(imageWidth, imageHeight))
        val left = (-drawnLeft / scale).roundToInt().coerceIn(0, imageWidth - side)
        val top = (-drawnTop / scale).roundToInt().coerceIn(0, imageHeight - side)
        return PixelSquare(left, top, side)
    }

    companion object {
        const val MAX_ZOOM = 4f
    }
}

data class PixelSquare(val left: Int, val top: Int, val side: Int)
