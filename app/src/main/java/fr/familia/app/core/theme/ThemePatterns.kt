package fr.familia.app.core.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate

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
        ThemePattern.Snow -> {
            dots(Color.White, 2f, 38f, 42f)
            dots(Color.White.copy(alpha = 0.8f), 1.5f, 38f, 42f, startX = 19f, startY = 21f)
        }
        // Small leaves on the 64 dp grid of the seeds (DESIGN-43, Olivier).
        ThemePattern.Leaves -> {
            leaves(Color(0xFF3F6B2E).copy(alpha = 0.14f), 0f, 0f)
            leaves(Color(0xFF6E8F3A).copy(alpha = 0.12f), 32f, 30f)
        }
        ThemePattern.Seeds -> {
            // Terracotta, green and violet seeds, 5 to 6 dp across, shifted on a 64 dp grid.
            dots(Color(0xFFB5541C).copy(alpha = 0.16f), 3f, 64f, 64f, startX = 0f, startY = 0f)
            dots(Color(0xFF1F5C3C).copy(alpha = 0.14f), 2.75f, 64f, 64f, startX = 30f, startY = 22f)
            dots(Color(0xFF6B55C9).copy(alpha = 0.13f), 2.5f, 64f, 64f, startX = 14f, startY = 44f)
        }
        // The seeds of Argile in other colors (DESIGN-43): red and yellow, lilac, or barely there.
        ThemePattern.SeedsSpain -> seeds(Color(0xFFAA151B).copy(alpha = 0.14f), Color(0xFFF1BF00).copy(alpha = 0.3f), Color(0xFFAA151B).copy(alpha = 0.1f))
        ThemePattern.SeedsLilac -> seeds(Color(0xFF6B55C9).copy(alpha = 0.14f), Color(0xFF9C86E0).copy(alpha = 0.16f), Color(0xFF6B55C9).copy(alpha = 0.1f))
        ThemePattern.SeedsNight -> seeds(Color(0xFFEBDCD3).copy(alpha = 0.05f), Color(0xFFEBDCD3).copy(alpha = 0.04f), Color(0xFFEBDCD3).copy(alpha = 0.04f))
    }
}

/** Three seeds, 5 to 6 dp across, shifted on a 64 dp grid, as on Argile. */
private fun DrawScope.seeds(first: Color, second: Color, third: Color) {
    dots(first, 3f, 64f, 64f, startX = 0f, startY = 0f)
    dots(second, 2.75f, 64f, 64f, startX = 30f, startY = 22f)
    dots(third, 2.5f, 64f, 64f, startX = 14f, startY = 44f)
}

/** A grid of small leaves, 9 by 4 dp, lying a little on their side. */
private fun DrawScope.leaves(color: Color, startX: Float, startY: Float) {
    val step = 64f * density
    val leaf = Size(9f * density, 4f * density)
    var y = startY * density + step / 2
    while (y < size.height) {
        var x = startX * density + step / 2
        while (x < size.width) {
            rotate(-30f, pivot = Offset(x, y)) { drawOval(color, topLeft = Offset(x - leaf.width / 2, y - leaf.height / 2), size = leaf) }
            x += step
        }
        y += step
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
