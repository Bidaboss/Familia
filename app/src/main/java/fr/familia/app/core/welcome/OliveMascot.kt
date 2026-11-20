package fr.familia.app.core.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.familia.app.core.ui.LocalMemberPhotos

/**
 * The fruits of the family, my household first (as [welcomeFruits]), for the olive tree mascot.
 * Empty until the family is read: the mascot then shows the colored fruits of the icon.
 */
internal val LocalMascotFruits = staticCompositionLocalOf<List<WelcomeFruit>> { emptyList() }

/** From this size, the fruits are the members' photos (choix de Kévin, 20/11): smaller, a photo cannot be read. */
private val PHOTOS_FROM = 100.dp

/** A fruit with a photo is a little bigger than on the icon, so that the face can be seen. */
private const val PHOTO_FRUIT = 5.3f

private val Grey = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * The olive tree of the icon as the mascot of Familia (DESIGN-58, variante A): the tree in its sun,
 * with its fruits. From [PHOTOS_FROM], each fruit is a member, their profile photo in a white ring
 * and an outline of their color, or their initial on their color (the fruits of the tree of life);
 * smaller (the 56 dp of the Accueil), the fruits keep the colors of the icon. [grey] is the tree of
 * « hors ligne ». Decorative: TalkBack reads the words next to it. Nothing moves.
 */
@Composable
fun OliveMascot(size: Dp, modifier: Modifier = Modifier, grey: Boolean = false) {
    val olive = rememberOlivePaths()
    val measurer = rememberTextMeasurer()
    val members = if (size >= PHOTOS_FROM) LocalMascotFruits.current else emptyList()
    val photos = LocalMemberPhotos.current
    val greyed = if (grey) {
        Modifier.drawWithContent {
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(Offset.Zero, this.size), Paint().apply { colorFilter = Grey; alpha = 0.7f })
                drawContent()
                canvas.restore()
            }
        }
    } else {
        Modifier
    }
    Canvas(modifier.size(size).then(greyed)) {
        // The sun (34.6 units around the middle of the 108 grid) fills the square, with a small margin.
        val unit = this.size.minDimension / 72f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        fun at(point: Offset) = Offset(center.x + (point.x - 54f) * unit, center.y + (point.y - 54f) * unit)
        drawOlive(olive, center, unit, fruitDetails = true)
        TREE_SLOTS.forEachIndexed { index, fruit ->
            val member = members.getOrNull(index)
            if (member == null) {
                drawCircle(fruit.color, radius = fruit.radius * unit, center = at(fruit.center))
            } else {
                val radius = PHOTO_FRUIT * unit
                drawCircle(OLIVE_SUN_COLOR, radius = radius + 0.6f * unit, center = at(fruit.center))
                drawMedallion(measurer, at(fruit.center), radius, member.color, photos[member.memberId], member.initials.take(1))
            }
        }
    }
}

