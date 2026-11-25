package fr.familia.app.core.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The background of the tabs: the theme's color and its light pattern, under 10 % opacity behind
 * the text (design principles, « Lisibilité »). Argile keeps its plain cream outside the R1 bis.
 */
@Composable
fun Modifier.themeBackground(): Modifier {
    val color = MaterialTheme.colorScheme.background
    val pattern = LocalClayStyle.current.pattern
    return background(color).then(if (pattern != ThemePattern.None) Modifier.drawBehind { drawPattern(pattern) } else Modifier)
}

internal fun DrawScope.drawPattern(pattern: ThemePattern) {
    when (pattern) {
        ThemePattern.None -> Unit
        ThemePattern.Seeds -> {
            // Terracotta, green and violet seeds, 5 to 6 dp across, shifted on a 64 dp grid.
            dots(Color(0xFFB5541C).copy(alpha = 0.16f), 3f, 64f, 64f, startX = 0f, startY = 0f)
            dots(Color(0xFF1F5C3C).copy(alpha = 0.14f), 2.75f, 64f, 64f, startX = 30f, startY = 22f)
            dots(Color(0xFF6B55C9).copy(alpha = 0.13f), 2.5f, 64f, 64f, startX = 14f, startY = 44f)
        }
    }
}

/** A grid of dots, sizes in dp. */
private fun DrawScope.dots(color: Color, radiusDp: Float, stepXDp: Float, stepYDp: Float, startX: Float = 0f, startY: Float = 0f) {
    val r = radiusDp * density
    val stepX = stepXDp * density
    val stepY = stepYDp * density
    var y = startY * density + stepY / 2
    while (y < size.height) {
        var x = startX * density + stepX / 2
        while (x < size.width) {
            drawCircle(color, r, Offset(x, y))
            x += stepX
        }
        y += stepY
    }
}
