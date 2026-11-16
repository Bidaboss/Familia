package fr.familia.app.core.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import fr.familia.app.R
import java.io.File

/**
 * What a share sends (PRD, récit 31): an image in the Argile style drawn on the phone, a text that
 * reads on its own, and the link. [lines] go under the title on the image (the date, the place);
 * [photo] is drawn only when the caller checked [photoAllowed] (carte EXPERT-2).
 */
data class ShareContent(
    val title: String,
    val lines: List<String>,
    val text: String,
    val link: String,
    val photo: Bitmap? = null,
)

/** The text of the message: the text of the item, then the link on its own line, when there is one. */
fun shareMessage(content: ShareContent): String =
    if (content.link.isBlank()) content.text.trim() else "${content.text.trim()}\n${content.link}"

private const val SIZE = 1080
private const val MARGIN = 72f

// Argile, light theme (core/theme/Color.kt): the image looks the same whatever theme the sharer has.
private const val BACKGROUND = 0xFFFFF6F0.toInt()
private const val CARD = 0xFFFFFFFF.toInt()
private const val SHADOW = 0xFFF5DED5.toInt()
private const val PRIMARY = 0xFFB5541C.toInt()
private const val ON_SURFACE = 0xFF231A16.toInt()
private const val ON_SURFACE_VARIANT = 0xFF53443D.toInt()

/** Draws the square image of [content]. No network, no data beyond what the image shows. */
fun drawShareCard(context: Context, content: ShareContent): Bitmap {
    val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(BACKGROUND)
    val titleFont = ResourcesCompat.getFont(context, R.font.fraunces) ?: Typeface.SERIF
    val bodyFont = ResourcesCompat.getFont(context, R.font.nunito) ?: Typeface.SANS_SERIF

    // The clay card: a soft offset shadow, then the card itself.
    val card = RectF(MARGIN, MARGIN, SIZE - MARGIN, SIZE - MARGIN)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    fill.color = SHADOW
    canvas.drawRoundRect(RectF(card.left + 12, card.top + 16, card.right + 12, card.bottom + 16), 64f, 64f, fill)
    fill.color = CARD
    canvas.drawRoundRect(card, 64f, 64f, fill)

    val inner = card.width() - 2 * 64f
    var y = card.top + 64f
    content.photo?.let { photo ->
        val box = RectF(card.left + 64f, y, card.right - 64f, y + 420f)
        drawCropped(canvas, photo, box)
        y = box.bottom + 48f
    }

    val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = titleFont
        color = ON_SURFACE
        textSize = if (content.photo == null) 84f else 64f
    }
    y = drawText(canvas, content.title, title, card.left + 64f, y, inner, maxLines = 3) + 24f

    val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = bodyFont
        color = ON_SURFACE_VARIANT
        textSize = 44f
    }
    for (line in content.lines.filter { it.isNotBlank() }) {
        if (y > card.bottom - 200f) break
        y = drawText(canvas, line, body, card.left + 64f, y, inner, maxLines = 2) + 12f
    }

    val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(titleFont, Typeface.BOLD)
        color = PRIMARY
        textSize = 48f
    }
    canvas.drawText(context.getString(R.string.app_name), card.left + 64f, card.bottom - 64f, brand)
    return bitmap
}

private fun drawText(canvas: Canvas, text: String, paint: TextPaint, x: Float, y: Float, width: Float, maxLines: Int): Float {
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
    canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restore()
    return y + layout.height
}

/** Fills [box] with [photo], cropped to its centre, with rounded corners. */
private fun drawCropped(canvas: Canvas, photo: Bitmap, box: RectF) {
    val scale = maxOf(box.width() / photo.width, box.height() / photo.height)
    val matrix = Matrix().apply {
        setScale(scale, scale)
        postTranslate(box.left + (box.width() - photo.width * scale) / 2, box.top + (box.height() - photo.height * scale) / 2)
    }
    val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
    canvas.drawRoundRect(box, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
}

/**
 * Opens the share sheet of Android with the image, the text and the link. The image is written
 * to the cache folder shares/ (res/xml/photo_paths.xml), replaced at each share. Returns false
 * when the phone has no app to share with.
 */
fun shareContent(context: Context, content: ShareContent, chooserTitle: String): Boolean {
    val folder = File(context.cacheDir, "shares").apply { mkdirs() }
    val file = File(folder, "familia.png")
    val bitmap = drawShareCard(context, content)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, shareMessage(content))
        putExtra(Intent.EXTRA_TITLE, content.title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(Intent.createChooser(send, chooserTitle))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
