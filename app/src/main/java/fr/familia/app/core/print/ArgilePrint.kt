package fr.familia.app.core.print

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF

/*
 * What the printed pages of Familia share (récits 47 and 53): the warm Argile colours, fixed so a
 * PDF looks the same in light and dark theme, and the way a photo is decoded and cropped.
 */

const val PRINT_INK = 0xFF2B2420.toInt()
const val PRINT_INK_SOFT = 0xFF6B5A4E.toInt()
const val PRINT_PAPER = 0xFFFDF8F2.toInt()
const val PRINT_CLAY = 0xFFF3E1CF.toInt()
const val PRINT_CLAY_DEEP = 0xFFC9764B.toInt()
const val PRINT_STEAM = 0xFF8A5A3B.toInt()

/** A photo of the app is at most 512 Ko; it is shrunk by halves while wider than [maxWidth] pixels. */
fun decodeForPrint(bytes: ByteArray, maxWidth: Int = 900): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/**
 * Pixels kept for each point of the page: about 110 dpi, clean on a home print. The PDF of Android
 * keeps each photo without loss and in memory until the end, so a book of 200 photos at their full
 * 1600 px would need close to 1 GB; at this size, about 100 MB.
 */
const val PRINT_PIXELS_PER_POINT = 1.5f

/**
 * [photo] cropped to fill [area], inside [clip] (a rounded rectangle, a circle…). Only the pixels
 * the page needs go into the PDF: a photo of 1600 px in a quarter of a page is first made smaller,
 * else each photo would weigh its full size and a long book would not fit in memory.
 */
fun drawCroppedPhoto(canvas: Canvas, photo: Bitmap, area: RectF, clip: Path) {
    val scale = maxOf(area.width() / photo.width, area.height() / photo.height)
    val w = (area.width() / scale).toInt().coerceIn(1, photo.width)
    val h = (area.height() / scale).toInt().coerceIn(1, photo.height)
    val left = (photo.width - w) / 2
    val top = (photo.height - h) / 2
    val wanted = printSize(w, h, area.width(), area.height())
    // Freed by the system once the page holds its copy: never recycled by hand while the PDF is open.
    val small = if (wanted.first < w) {
        val shrink = Matrix().apply { setScale(wanted.first.toFloat() / w, wanted.second.toFloat() / h) }
        Bitmap.createBitmap(photo, left, top, w, h, shrink, true)
    } else {
        null
    }
    canvas.save()
    canvas.clipPath(clip)
    if (small != null) {
        canvas.drawBitmap(small, null, area, Paint(Paint.FILTER_BITMAP_FLAG))
    } else {
        canvas.drawBitmap(photo, Rect(left, top, left + w, top + h), area, Paint(Paint.FILTER_BITMAP_FLAG))
    }
    canvas.restore()
}

/** The size, in pixels, of a crop of [w] × [h] shown on [areaWidth] × [areaHeight] points; never bigger than the crop. */
fun printSize(w: Int, h: Int, areaWidth: Float, areaHeight: Float): Pair<Int, Int> {
    val width = (areaWidth * PRINT_PIXELS_PER_POINT).toInt().coerceIn(1, w)
    val height = (areaHeight * PRINT_PIXELS_PER_POINT).toInt().coerceIn(1, h)
    return width to height
}
