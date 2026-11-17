package fr.familia.app.feature.recipes.book

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
import fr.familia.app.core.print.PRINT_CLAY
import fr.familia.app.core.print.PRINT_CLAY_DEEP
import fr.familia.app.core.print.PRINT_INK
import fr.familia.app.core.print.PRINT_INK_SOFT
import fr.familia.app.core.print.PRINT_PAPER
import fr.familia.app.core.print.PRINT_STEAM
import fr.familia.app.core.print.decodeForPrint
import fr.familia.app.core.print.drawCroppedPhoto
import java.io.File
import java.io.FileOutputStream

/*
 * The PDF of the recipe book (récit 47), made on the phone with android.graphics.pdf.PdfDocument:
 * A5 pages (148 × 210 mm, 420 × 595 points), a cover, the contents by part, then one recipe per page.
 * A long recipe goes on to the next page rather than being cut. Nothing leaves the phone here.
 */

/** The words of the book, read from the resources by the route. */
data class BookLabels(
    val contents: String,
    val otherRecipes: String,
    val ingredients: String,
    val steps: String,
    val from: (String) -> String,
    val writtenBy: (String) -> String,
    val servings: (Int) -> String,
)

private const val PAGE_WIDTH = 420
private const val PAGE_HEIGHT = 595
private const val MARGIN = 40f
private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
private const val FOOTER = 28f
private const val PHOTO_HEIGHT = 190f
private const val COVER_PHOTO_HEIGHT = 250f

// Warm Argile colours, fixed so the printed book looks the same in light and dark theme.
private const val INK = PRINT_INK
private const val INK_SOFT = PRINT_INK_SOFT
private const val PAPER = PRINT_PAPER
private const val CLAY = PRINT_CLAY
private const val CLAY_DEEP = PRINT_CLAY_DEEP
private const val STEAM = PRINT_STEAM

/** Writes the book to [file]. [photo] gives the bytes of a photo, or null when it cannot be read. */
suspend fun writeRecipeBook(content: BookContent, labels: BookLabels, file: File, photo: suspend (path: String) -> ByteArray?) {
    val pages = Pages(content, labels)
    val document = PdfDocument()
    try {
        var number = 1
        fun page(draw: (Canvas) -> Unit) {
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()
            val p = document.startPage(info)
            p.canvas.drawColor(PAPER)
            draw(p.canvas)
            if (number > 1) pages.footer(p.canvas, number)
            document.finishPage(p)
            number++
        }
        val cover = content.coverPhoto?.let { photo(it.photoPath)?.let(::decode) }
        page { pages.cover(it, cover) }
        cover?.recycle()
        pages.contentsPages.forEachIndexed { index, rows -> page { pages.contents(it, rows, first = index == 0) } }
        content.sections.flatMap { it.recipes }.forEach { item ->
            val bitmap = item.photo?.let { photo(it.photoPath)?.let(::decode) }
            pages.recipePages(item).forEachIndexed { index, blocks ->
                page { canvas -> pages.recipe(canvas, blocks, if (index == 0) bitmap else null, first = index == 0) }
            }
            bitmap?.recycle()
        }
        FileOutputStream(file).use { document.writeTo(it) }
    } finally {
        document.close()
    }
}

/** A photo of the app is at most 512 Ko; a quarter of its size is plenty for an A5 page. */
private fun decode(bytes: ByteArray): Bitmap? = decodeForPrint(bytes)

/** A line of the contents: a part's name, or a recipe and its page. */
private sealed interface ContentsRow {
    data class Part(val name: String) : ContentsRow
    data class Entry(val title: String, val page: Int) : ContentsRow
}

private class Pages(private val content: BookContent, private val labels: BookLabels) {
    private val serif = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val italic = Typeface.create(Typeface.SERIF, Typeface.ITALIC)

    private fun paint(size: Float, color: Int = INK, typeface: Typeface = Typeface.DEFAULT) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        this.typeface = typeface
    }

    private val titlePaint = paint(19f, typeface = serif)
    private val headingPaint = paint(12.5f, CLAY_DEEP, Typeface.DEFAULT_BOLD)
    private val bodyPaint = paint(10.5f)
    private val softPaint = paint(9.5f, INK_SOFT)
    private val storyPaint = paint(10.5f, INK_SOFT, italic)

    private fun layout(text: String, paint: TextPaint, width: Float = CONTENT_WIDTH, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(align)
            .setLineSpacing(2f, 1f)
            .build()

    // --- Cover --------------------------------------------------------------------------------

    fun cover(canvas: Canvas, photo: Bitmap?) {
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), Paint().apply { color = CLAY })
        val area = RectF(MARGIN, 90f, PAGE_WIDTH - MARGIN, 90f + COVER_PHOTO_HEIGHT)
        if (photo != null) drawPhoto(canvas, photo, area) else drawIllustration(canvas, area)
        var y = area.bottom + 40f
        val title = layout(content.title, paint(24f, typeface = serif), align = Layout.Alignment.ALIGN_CENTER)
        y = draw(canvas, title, y) + 10f
        draw(canvas, layout(content.year.toString(), paint(14f, INK_SOFT), align = Layout.Alignment.ALIGN_CENTER), y)
    }

    // --- Contents -----------------------------------------------------------------------------

    private val rowHeight = 17f
    private val partHeight = 26f
    private val contentsTop = MARGIN + 40f

    /** The rows of the contents, cut into pages, each with the page its recipe starts on. */
    val contentsPages: List<List<ContentsRow>> by lazy {
        val rows = mutableListOf<ContentsRow>()
        val counts = content.sections.flatMap { it.recipes }.map { recipePages(it).size }
        val pagesOfContents = paginate(rowsWithoutPages()).size
        var next = 2 + pagesOfContents
        var index = 0
        content.sections.forEach { section ->
            rows += ContentsRow.Part(section.name ?: labels.otherRecipes)
            section.recipes.forEach { item ->
                rows += ContentsRow.Entry(item.recipe.title, next)
                next += counts[index++]
            }
        }
        paginate(rows)
    }

    private fun rowsWithoutPages(): List<ContentsRow> = content.sections.flatMap { section ->
        listOf<ContentsRow>(ContentsRow.Part(section.name.orEmpty())) + section.recipes.map { ContentsRow.Entry(it.recipe.title, 0) }
    }

    /** The first page starts under the title « Sommaire », the next ones at the top. */
    private fun paginate(rows: List<ContentsRow>): List<List<ContentsRow>> {
        val bottom = PAGE_HEIGHT - MARGIN - FOOTER
        val pages = mutableListOf(mutableListOf<ContentsRow>())
        var y = contentsTop
        rows.forEach { row ->
            val h = if (row is ContentsRow.Part) partHeight else rowHeight
            // A part never ends a page alone.
            val needed = if (row is ContentsRow.Part) h + rowHeight else h
            if (y + needed > bottom && pages.last().isNotEmpty()) {
                pages += mutableListOf<ContentsRow>()
                y = MARGIN
            }
            pages.last() += row
            y += h
        }
        return pages
    }

    fun contents(canvas: Canvas, rows: List<ContentsRow>, first: Boolean) {
        var y = MARGIN
        if (first) {
            draw(canvas, layout(labels.contents, titlePaint), y)
            y = contentsTop
        }
        val number = paint(10.5f, INK_SOFT)
        rows.forEach { row ->
            when (row) {
                is ContentsRow.Part -> {
                    canvas.drawText(row.name, MARGIN, y + partHeight - 8f, headingPaint)
                    y += partHeight
                }
                is ContentsRow.Entry -> {
                    val page = row.page.toString()
                    val room = CONTENT_WIDTH - number.measureText(page) - 12f
                    val title = android.text.TextUtils.ellipsize(row.title, bodyPaint, room, android.text.TextUtils.TruncateAt.END).toString()
                    canvas.drawText(title, MARGIN + 8f, y + rowHeight - 5f, bodyPaint)
                    canvas.drawText(page, PAGE_WIDTH - MARGIN - number.measureText(page), y + rowHeight - 5f, number)
                    y += rowHeight
                }
            }
        }
    }

    // --- Recipes ------------------------------------------------------------------------------

    /** A piece of a recipe page: a text, or the place of its photo. */
    sealed interface Block {
        data class Text(val layout: StaticLayout, val space: Float) : Block
        data object Photo : Block
    }

    private val cache = HashMap<String, List<List<Block>>>()

    /** The blocks of [item], cut into pages: the photo is always on the first one. */
    fun recipePages(item: BookRecipe): List<List<Block>> = cache.getOrPut(item.recipe.id) {
        val recipe = item.recipe
        val blocks = mutableListOf<Block>()
        blocks += Block.Text(layout(recipe.title, titlePaint), 4f)
        val about = listOfNotNull(item.origin?.let(labels.from), labels.writtenBy(recipe.authorName), recipe.servings?.let(labels.servings))
        blocks += Block.Text(layout(about.joinToString(" · "), softPaint), 10f)
        if (recipe.story.isNotBlank()) blocks += Block.Text(layout(recipe.story.trim(), storyPaint), 12f)
        blocks += Block.Photo
        blocks += Block.Text(layout(labels.ingredients, headingPaint), 4f)
        recipe.ingredientLines.forEach { blocks += Block.Text(layout("•  $it", bodyPaint), 2f) }
        blocks += Block.Text(layout(" ", bodyPaint), 6f)
        blocks += Block.Text(layout(labels.steps, headingPaint), 4f)
        recipe.stepLines.forEachIndexed { i, step -> blocks += Block.Text(layout("${i + 1}.  $step", bodyPaint), 5f) }

        val bottom = PAGE_HEIGHT - MARGIN - FOOTER
        val pages = mutableListOf(mutableListOf<Block>())
        var y = MARGIN
        blocks.forEach { block ->
            val h = when (block) {
                is Block.Text -> block.layout.height + block.space
                Block.Photo -> PHOTO_HEIGHT + 16f
            }
            if (y + h > bottom && pages.last().isNotEmpty()) {
                pages += mutableListOf<Block>()
                y = MARGIN
            }
            pages.last() += block
            y += h
        }
        pages
    }

    fun recipe(canvas: Canvas, blocks: List<Block>, photo: Bitmap?, first: Boolean) {
        var y = MARGIN
        blocks.forEach { block ->
            when (block) {
                is Block.Text -> y = draw(canvas, block.layout, y) + block.space
                Block.Photo -> {
                    val area = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + PHOTO_HEIGHT)
                    // No photo, or one carte EXPERT-2 keeps out of the book: the Argile drawing.
                    if (first && photo != null) drawPhoto(canvas, photo, area) else drawIllustration(canvas, area)
                    y = area.bottom + 16f
                }
            }
        }
    }

    fun footer(canvas: Canvas, number: Int) {
        val text = number.toString()
        canvas.drawText(text, (PAGE_WIDTH - softPaint.measureText(text)) / 2, PAGE_HEIGHT - MARGIN / 2 - 4f, softPaint)
    }

    // --- Drawing ------------------------------------------------------------------------------

    private fun draw(canvas: Canvas, layout: StaticLayout, y: Float): Float {
        canvas.save()
        canvas.translate(MARGIN, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height
    }

    /** The photo, cropped to fill [area], with rounded corners. */
    private fun drawPhoto(canvas: Canvas, photo: Bitmap, area: RectF) =
        drawCroppedPhoto(canvas, photo, area, Path().apply { addRoundRect(area, 14f, 14f, Path.Direction.CW) })

    /** A bowl on a clay background with a little steam: in place of a photo. */
    private fun drawIllustration(canvas: Canvas, area: RectF) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = CLAY
        canvas.drawRoundRect(area, 14f, 14f, fill)
        val cx = area.centerX()
        val cy = area.centerY() + area.height() * 0.12f
        val r = area.height() * 0.26f
        fill.color = CLAY_DEEP
        canvas.drawArc(RectF(cx - r * 1.3f, cy - r, cx + r * 1.3f, cy + r), 0f, 180f, true, fill)
        canvas.drawRoundRect(RectF(cx - r * 1.45f, cy - 4f, cx + r * 1.45f, cy + 4f), 4f, 4f, fill)
        val steam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STEAM
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
        }
        listOf(-0.45f, 0f, 0.45f).forEach { dx ->
            val x = cx + dx * r
            canvas.drawPath(
                Path().apply {
                    moveTo(x, cy - 12f)
                    cubicTo(x - 10f, cy - 28f, x + 10f, cy - 40f, x, cy - 56f)
                },
                steam,
            )
        }
    }
}
