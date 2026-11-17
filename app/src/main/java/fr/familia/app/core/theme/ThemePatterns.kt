package fr.familia.app.core.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * The background of the tabs: the theme's color and its light pattern, under 10 % opacity behind
 * the text (design principles, « Lisibilité »). Classique keeps its plain cream.
 */
@Composable
fun Modifier.themeBackground(withTopBand: Boolean = true): Modifier {
    val color = MaterialTheme.colorScheme.background
    val pattern = LocalClayStyle.current.pattern
    val behind = pattern != ThemePattern.None && (withTopBand || !pattern.isTopBand)
    return background(color).then(if (behind) Modifier.drawBehind { drawPattern(pattern) } else Modifier)
}

/** The bands drawn across the top of the screen (Rétro 70, Espagne), rather than a light motif. */
val ThemePattern.isTopBand: Boolean get() = this == ThemePattern.Stripes70 || this == ThemePattern.FlagBand

/**
 * The band of Rétro 70 or Espagne drawn over the content, at the very top of the screen. Behind the
 * content, the title bar hid it as soon as it took the surface color, once the content scrolled under
 * it (retour d'essai, 17/11). Use with themeBackground(withTopBand = false).
 */
@Composable
fun Modifier.themeTopBand(): Modifier {
    val pattern = LocalClayStyle.current.pattern
    return if (pattern.isTopBand) drawWithContent { drawContent(); drawPattern(pattern) } else this
}

internal fun DrawScope.drawPattern(pattern: ThemePattern) {
    when (pattern) {
        ThemePattern.None -> Unit
        ThemePattern.PixelDots -> dots(Color.White.copy(alpha = 0.08f), 1.5f, 18f, 18f)
        ThemePattern.Stars -> {
            dots(Color.White.copy(alpha = 0.55f), 1f, 47f, 53f)
            dots(Color.White.copy(alpha = 0.3f), 1f, 31f, 37f, startX = 23f, startY = 19f)
        }
        ThemePattern.Snow -> {
            dots(Color.White, 2f, 38f, 42f)
            dots(Color.White.copy(alpha = 0.8f), 1.5f, 38f, 42f, startX = 19f, startY = 21f)
        }
        ThemePattern.FlagBand -> {
            // Red 8 dp, yellow 16 dp, red 8 dp across the top, nothing else on the background.
            val red = Color(0xFFAA151B)
            val edge = 8.dp.toPx()
            drawRect(red, topLeft = Offset.Zero, size = Size(size.width, edge))
            drawRect(Color(0xFFF1BF00), topLeft = Offset(0f, edge), size = Size(size.width, 2 * edge))
            drawRect(red, topLeft = Offset(0f, 3 * edge), size = Size(size.width, edge))
        }
        ThemePattern.Leaves -> {
            val leaf = Color(0x2E3F6B2E)
            drawOval(leaf, topLeft = Offset(size.width * 0.92f - 60.dp.toPx(), size.height * 0.06f - 30.dp.toPx()), size = Size(120.dp.toPx(), 60.dp.toPx()))
            drawOval(leaf.copy(alpha = 0.14f), topLeft = Offset(size.width * 0.08f - 40.dp.toPx(), size.height * 0.03f - 20.dp.toPx()), size = Size(80.dp.toPx(), 40.dp.toPx()))
        }
        ThemePattern.Stripes70 -> {
            val band = 6.dp.toPx()
            listOf(Color(0xFFE8A33D), Color(0xFFC8552B), Color(0xFF8A3A1A)).forEachIndexed { i, c ->
                drawRect(c, topLeft = Offset(0f, i * band), size = Size(size.width, band))
            }
        }
        ThemePattern.Seeds -> {
            // Terracotta, green and violet seeds, 5 to 6 dp across, shifted on a 64 dp grid.
            dots(Color(0xFFB5541C).copy(alpha = 0.16f), 3f, 64f, 64f, startX = 0f, startY = 0f)
            dots(Color(0xFF1F5C3C).copy(alpha = 0.14f), 2.75f, 64f, 64f, startX = 30f, startY = 22f)
            dots(Color(0xFF6B55C9).copy(alpha = 0.13f), 2.5f, 64f, 64f, startX = 14f, startY = 44f)
        }
        ThemePattern.NotebookLines -> {
            val step = 32.dp.toPx()
            val line = 1.dp.toPx()
            var y = step - line
            while (y < size.height) {
                drawRect(Color(0x121F3A5F), topLeft = Offset(0f, y), size = Size(size.width, line))
                y += step
            }
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
