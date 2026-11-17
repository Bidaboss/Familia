package fr.familia.app.core.print

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
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

/** [photo] cropped to fill [area], inside [clip] (a rounded rectangle, a circle…). */
fun drawCroppedPhoto(canvas: Canvas, photo: Bitmap, area: RectF, clip: Path) {
    val scale = maxOf(area.width() / photo.width, area.height() / photo.height)
    val w = (area.width() / scale).toInt()
    val h = (area.height() / scale).toInt()
    val left = (photo.width - w) / 2
    val top = (photo.height - h) / 2
    canvas.save()
    canvas.clipPath(clip)
    canvas.drawBitmap(photo, Rect(left, top, left + w, top + h), area, Paint(Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
}
