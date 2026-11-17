package fr.familia.app.feature.genealogy.poster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import fr.familia.app.core.model.KinLinkKind
import fr.familia.app.core.print.PRINT_CLAY
import fr.familia.app.core.print.PRINT_CLAY_DEEP
import fr.familia.app.core.print.PRINT_INK
import fr.familia.app.core.print.PRINT_INK_SOFT
import fr.familia.app.core.print.PRINT_PAPER
import fr.familia.app.core.print.decodeForPrint
import fr.familia.app.core.print.drawCroppedPhoto
import java.io.File
import java.io.FileOutputStream

/*
 * The PDF of the tree poster (récit 53): one landscape page, A4 or A3, made on the phone with
 * android.graphics.pdf.PdfDocument. The title on top, then one row per generation, eldest first,
 * each person in a round (their photo or their initial) above their first name and year of birth.
 * Thin clay lines join partners side by side and go down from the parents to their children.
 * Nothing leaves the phone here.
 */

/** Writes the poster to [file]. [photo] gives the profile photo of a member, or null. */
suspend fun writeTreePoster(content: PosterContent, file: File, photo: suspend (memberId: String) -> ByteArray?) {
    val bitmaps = HashMap<String, Bitmap>()
    content.rows.flatten().forEach { person ->
        person.photoOf?.let { id -> photo(id)?.let { decodeForPrint(it, maxWidth = 300) }?.let { bitmaps[person.id] = it } }
    }
    val document = PdfDocument()
    try {
        val info = PdfDocument.PageInfo.Builder(content.format.width, content.format.height, 1).create()
        val page = document.startPage(info)
        page.canvas.drawColor(PRINT_PAPER)
        PosterDrawing(content).draw(page.canvas, bitmaps)
        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
    } finally {
        document.close()
        bitmaps.values.forEach { it.recycle() }
    }
}

private class PosterDrawing(private val content: PosterContent) {
    private val width = content.format.width.toFloat()
    private val height = content.format.height.toFloat()

    /** Everything is drawn for A4, then grown for A3. */
    private val k = width / PosterFormat.A4.width
    private val margin = 36f * k
    private val serif = Typeface.create(Typeface.SERIF, Typeface.BOLD)

    private fun paint(size: Float, color: Int = PRINT_INK, typeface: Typeface = Typeface.DEFAULT) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        this.typeface = typeface
    }

    /** Where each person is drawn: the centre of their round, its radius, and the bottom of their text. */
    private class Spot(val x: Float, val y: Float, val r: Float, val bottom: Float)

    fun draw(canvas: Canvas, photos: Map<String, Bitmap>) {
        // A clay band on top with the title and the year.
        val band = 74f * k
        canvas.drawRect(0f, 0f, width, band, Paint().apply { color = PRINT_CLAY })
        val title = paint(26f * k, typeface = serif)
        val shown = TextUtils.ellipsize(content.title, title, width - 2 * margin, TextUtils.TruncateAt.END).toString()
        canvas.drawText(shown, (width - title.measureText(shown)) / 2, band * 0.55f, title)
        val sub = paint(12f * k, PRINT_INK_SOFT)
        val year = content.year.toString()
        canvas.drawText(year, (width - sub.measureText(year)) / 2, band * 0.55f + 20f * k, sub)

        if (content.rows.isEmpty()) return
        val top = band + 24f * k
        val bottom = height - margin
        val rowHeight = (bottom - top) / content.rows.size
        val widest = content.rows.maxOf { it.size }
        val column = (width - 2 * margin) / widest
        // The round fills about half the row, and never more than the column allows.
        val r = minOf(rowHeight * 0.26f, column * 0.34f, 34f * k)
        val nameSize = (r * 0.42f).coerceIn(6f * k, 12f * k)
        val name = paint(nameSize, typeface = Typeface.DEFAULT_BOLD)
        val small = paint(nameSize * 0.85f, PRINT_INK_SOFT)

        val spots = HashMap<String, Spot>()
        content.rows.forEachIndexed { i, row ->
            val step = (width - 2 * margin) / row.size
            val y = top + i * rowHeight + r + 4f * k
            row.forEachIndexed { j, person ->
                val x = margin + (j + 0.5f) * step
                spots[person.id] = Spot(x, y, r, y + r + nameSize * 2.4f)
            }
        }

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRINT_CLAY_DEEP
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * k
            alpha = 160
        }
        // Partners: a short line between their rounds.
        content.links.filter { it.kind == KinLinkKind.Partner }.forEach { link ->
            val a = spots[link.aId] ?: return@forEach
            val b = spots[link.bId] ?: return@forEach
            if (a.y != b.y) return@forEach
            val (left, right) = if (a.x < b.x) a to b else b to a
            canvas.drawLine(left.x + left.r, left.y, right.x - right.r, right.y, line)
        }
        // Parents to child: down from the middle of the parents, across, then down to the round.
        spots.keys.forEach { childId ->
            val child = spots.getValue(childId)
            val parents = content.parentsOf(childId).mapNotNull { spots[it] }
            if (parents.isEmpty()) return@forEach
            val fromX = parents.map { it.x }.average().toFloat()
            val fromY = parents.maxOf { it.bottom } + 2f * k
            val toY = child.y - child.r
            val midY = (fromY + toY) / 2
            canvas.drawPath(
                Path().apply {
                    moveTo(fromX, fromY)
                    lineTo(fromX, midY)
                    lineTo(child.x, midY)
                    lineTo(child.x, toY)
                },
                line,
            )
        }

        content.rows.flatten().forEach { person ->
            val spot = spots.getValue(person.id)
            val bitmap = photos[person.id]
            val area = RectF(spot.x - r, spot.y - r, spot.x + r, spot.y + r)
            if (bitmap != null) {
                drawCroppedPhoto(canvas, bitmap, area, Path().apply { addCircle(spot.x, spot.y, r, Path.Direction.CW) })
            } else {
                canvas.drawCircle(spot.x, spot.y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PRINT_CLAY })
                val initial = paint(r * 0.9f, PRINT_CLAY_DEEP, serif)
                val bounds = Rect()
                initial.getTextBounds(person.initial, 0, person.initial.length, bounds)
                canvas.drawText(person.initial, spot.x - initial.measureText(person.initial) / 2, spot.y + bounds.height() / 2f, initial)
            }
            val room = column * 0.96f
            val first = TextUtils.ellipsize(person.firstName, name, room, TextUtils.TruncateAt.END).toString()
            val nameY = spot.y + r + nameSize * 1.2f
            canvas.drawText(first, spot.x - name.measureText(first) / 2, nameY, name)
            person.year?.toString()?.let { y ->
                canvas.drawText(y, spot.x - small.measureText(y) / 2, nameY + nameSize * 1.15f, small)
            }
        }
    }
}
