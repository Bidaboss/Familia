package fr.familia.app.core.album.book

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.print.PRINT_CLAY
import fr.familia.app.core.print.PRINT_CLAY_DEEP
import fr.familia.app.core.print.PRINT_INK
import fr.familia.app.core.print.PRINT_INK_SOFT
import fr.familia.app.core.print.PRINT_PAPER
import fr.familia.app.core.print.decodeForPrint
import fr.familia.app.core.print.drawCroppedPhoto
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The PDF of the photo book (récit 59), made on the phone with android.graphics.pdf.PdfDocument:
 * a cover (the photo chosen, the title, the dates), then the pages of photos with their captions,
 * then the steps of the trip when there are some. One page is drawn at a time and its photos are
 * freed after it, so a long album fits in the memory of a small phone. Nothing leaves the phone.
 */

/** Wide enough for a print of 30 cm at 170 dpi: the photos of the app are not bigger anyway. */
private const val BOOK_PHOTO_WIDTH = 2048

/**
 * Writes the book to [file]. [photo] gives the full photo, downloaded once by the caller, or null:
 * then the place stays in clay, with its caption.
 */
suspend fun writePhotoBook(content: PhotoBookContent, file: File, photo: suspend (AlbumPhoto) -> ByteArray?) {
    val drawing = BookDrawing(content)
    val document = PdfDocument()
    var number = 1
    suspend fun page(photos: List<AlbumPhoto>, draw: (Canvas, Map<String, Bitmap>) -> Unit) {
        val bitmaps = photos.mapNotNull { p -> photo(p)?.let { decodeForPrint(it, BOOK_PHOTO_WIDTH) }?.let { p.id to it } }.toMap()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(content.format.width, content.format.height, number++).create())
            page.canvas.drawColor(PRINT_PAPER)
            draw(page.canvas, bitmaps)
            document.finishPage(page)
        } finally {
            bitmaps.values.forEach { it.recycle() }
        }
    }
    try {
        page(listOfNotNull(content.cover)) { canvas, bitmaps -> drawing.cover(canvas, content.cover?.let { bitmaps[it.id] }) }
        content.pages.forEachIndexed { i, photos ->
            page(photos.map { it.photo }) { canvas, bitmaps -> drawing.photos(canvas, photos, bitmaps, i + 2) }
        }
        if (content.steps.isNotEmpty()) page(emptyList()) { canvas, _ -> drawing.steps(canvas) }
        FileOutputStream(file).use { document.writeTo(it) }
    } finally {
        document.close()
    }
}

private class BookDrawing(private val content: PhotoBookContent) {
    private val width = content.format.width.toFloat()
    private val height = content.format.height.toFloat()
    private val margin = 32f
    private val gap = 12f
    private val captionHeight = 18f
    private val serif = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val corner = 10f

    private fun paint(size: Float, color: Int = PRINT_INK, typeface: Typeface = Typeface.DEFAULT) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        this.typeface = typeface
    }

    private fun rounded(area: RectF) = Path().apply { addRoundRect(area, corner, corner, Path.Direction.CW) }

    fun cover(canvas: Canvas, photo: Bitmap?) {
        canvas.drawColor(PRINT_CLAY)
        val textTop = height * 0.70f
        val area = RectF(margin, margin, width - margin, textTop - gap)
        if (photo != null) drawCroppedPhoto(canvas, photo, area, rounded(area))
        val title = paint(30f, typeface = serif)
        val layout = StaticLayout.Builder.obtain(content.title, 0, content.title.length, title, (width - 2 * margin).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(margin, textTop + 8f)
        layout.draw(canvas)
        canvas.restore()
        val dates = paint(14f, PRINT_INK_SOFT)
        canvas.drawText(content.dates, (width - dates.measureText(content.dates)) / 2, textTop + 8f + layout.height + 24f, dates)
    }

    fun photos(canvas: Canvas, photos: List<BookPhoto>, bitmaps: Map<String, Bitmap>, number: Int) {
        val inner = RectF(margin, margin, width - margin, height - margin - 14f)
        slots(inner, photos.size).zip(photos).forEach { (slot, photo) ->
            val area = RectF(slot.left, slot.top, slot.right, slot.bottom - captionHeight)
            val bitmap = bitmaps[photo.photo.id]
            if (bitmap != null) {
                drawCroppedPhoto(canvas, bitmap, area, rounded(area))
            } else {
                canvas.drawRoundRect(area, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PRINT_CLAY })
            }
            val caption = paint(9f, PRINT_INK_SOFT)
            val shown = TextUtils.ellipsize(photo.caption, caption, slot.width(), TextUtils.TruncateAt.END).toString()
            canvas.drawText(shown, slot.left, slot.bottom - 5f, caption)
        }
        val small = paint(8f, PRINT_CLAY_DEEP)
        val text = number.toString()
        canvas.drawText(text, (width - small.measureText(text)) / 2, height - margin / 2, small)
    }

    /** Where the photos of a page go: one alone, two side by side (or one above the other on a square), three, or four. */
    private fun slots(area: RectF, count: Int): List<RectF> {
        val l = area.left
        val t = area.top
        val r = area.right
        val b = area.bottom
        val midX = (l + r) / 2
        val midY = (t + b) / 2
        val h = gap / 2
        return when (count) {
            1 -> listOf(area)
            2 -> if (content.format == PhotoBookFormat.Square) {
                listOf(RectF(l, t, r, midY - h), RectF(l, midY + h, r, b))
            } else {
                listOf(RectF(l, t, midX - h, b), RectF(midX + h, t, r, b))
            }
            3 -> listOf(RectF(l, t, midX - h, b), RectF(midX + h, t, r, midY - h), RectF(midX + h, midY + h, r, b))
            else -> listOf(
                RectF(l, t, midX - h, midY - h),
                RectF(midX + h, t, r, midY - h),
                RectF(l, midY + h, midX - h, b),
                RectF(midX + h, midY + h, r, b),
            )
        }
    }

    fun steps(canvas: Canvas) {
        val title = paint(22f, typeface = serif)
        canvas.drawText(STEPS_TITLE, margin, margin + 22f, title)
        val line = paint(12f)
        val soft = paint(11f, PRINT_INK_SOFT)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PRINT_CLAY_DEEP }
        val step = 30f
        val rows = ((height - 2 * margin - 44f) / step).toInt().coerceAtLeast(1)
        // Two columns on a landscape page when the trip has many steps; what does not fit is left out.
        val columns = if (content.format == PhotoBookFormat.Landscape && content.steps.size > rows) 2 else 1
        val columnWidth = (width - 2 * margin) / columns
        content.steps.take(rows * columns).forEachIndexed { i, s ->
            val x = margin + (i / rows) * columnWidth
            val y = margin + 60f + (i % rows) * step
            canvas.drawCircle(x + 4f, y - 4f, 4f, dot)
            val text = TextUtils.ellipsize("${s.kind} · ${s.place}", line, columnWidth - 24f, TextUtils.TruncateAt.END).toString()
            canvas.drawText(text, x + 16f, y, line)
            canvas.drawText(stepDay.format(s.date), x + 16f, y + 13f, soft)
        }
    }

    companion object {
        const val STEPS_TITLE = "Les étapes du voyage"
        val stepDay: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
    }
}
