package fr.familia.app.core.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.FamiliaThemeId
import org.junit.Assert.assertTrue

/** The WCAG contrast of two opaque colors, from 1:1 to 21:1. */
fun contrast(a: Color, b: Color): Float {
    val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (hi + 0.05f) / (lo + 0.05f)
}

/** One way the family sees the app: a theme, and the phone's dark mode. Only Classique has a dark version. */
data class Look(val name: String, val theme: FamiliaThemeId, val dark: Boolean = false)

/** Classique in light and in dark mode, then each theme to choose from. */
val allLooks: List<Look> = listOf(Look("Classique clair", FamiliaThemeId.Classic), Look("Classique sombre", FamiliaThemeId.Classic, dark = true)) +
    FamiliaThemeId.entries.filter { it != FamiliaThemeId.Classic }.map { Look(it.key, it) }

/**
 * The contrast pass (avant le 21): draws each of [screens] in [look] and measures, on the picture,
 * every text against what is behind it: 4.5:1 at least, 3:1 for large text (24 sp, or 18.7 sp in
 * bold) and for the small icons that carry a description. A disabled control is not judged (WCAG).
 * Run it with `@GraphicsMode(NATIVE)` and a font scale of 1.
 *
 * How it measures: behind a text is the color found most often in its box; the text is the color,
 * found at least on a few pixels, that stands out most from it (the inside of the letters).
 */
fun ComposeContentTestRule.checkContrast(look: Look, screens: List<Pair<String, @Composable () -> Unit>>) {
    var shown by mutableIntStateOf(0)
    // The clock moves only when told to: a slideshow or a spinner never stops, and would keep the test waiting.
    mainClock.autoAdvance = false
    setContent {
        FamiliaTheme(darkTheme = look.dark, theme = look.theme) { Surface { screens[shown].second() } }
    }
    val problems = mutableListOf<String>()
    screens.forEachIndexed { index, (name, _) ->
        shown = index
        mainClock.advanceTimeBy(SETTLE_MS)
        problems += try {
            lowContrast().map { "${look.name} › $name › $it" }
        } catch (e: ComposeTimeoutException) {
            listOf("${look.name} › $name › l'écran ne se pose pas : ${e.message?.take(120)}")
        }
    }
    mainClock.autoAdvance = true
    assertTrue("contraste trop faible :\n" + problems.joinToString("\n"), problems.isEmpty())
}

private fun ComposeContentTestRule.lowContrast(): List<String> {
    val image = onRoot().captureToImage()
    val width = image.width
    val height = image.height
    val pixels = IntArray(width * height)
    image.readPixels(pixels)
    val root = Rect(0f, 0f, width.toFloat(), height.toFloat())
    val iconMax = with(density) { 48.dp.toPx() }
    val found = mutableListOf<String>()

    val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
    for (node in texts) {
        val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        if (text.none(Char::isLetterOrDigit) || node.isDisabled()) continue
        val m = measure(pixels, width, node.boundsInRoot.intersect(root)) ?: continue
        val need = if (node.isLargeText()) 3f else 4.5f
        if (m.ratio < need) found += "« ${text.take(40)} » $m (il faut $need)"
    }

    val icons = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image), useUnmergedTree = true).fetchSemanticsNodes()
    for (node in icons) {
        val label = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        // Photos and drawings are bigger than an icon: they are not judged.
        if (label.isBlank() || node.size.width > iconMax || node.size.height > iconMax || node.isDisabled()) continue
        val m = measure(pixels, width, node.boundsInRoot.intersect(root)) ?: continue
        if (m.ratio < 3f) found += "icône « ${label.take(40)} » $m (il faut 3)"
    }
    return found
}

private class Measure(val ratio: Float, val front: Int, val back: Int) {
    override fun toString() = "%.2f:1, #%06X sur #%06X".format(ratio, front and 0xFFFFFF, back and 0xFFFFFF)
}

/** The contrast between the most frequent color of [box] and the one that stands out most; null when nothing shows. */
private fun measure(pixels: IntArray, width: Int, box: Rect): Measure? {
    if (box.width < 4f || box.height < 4f) return null
    val counts = HashMap<Int, Int>()
    for (y in box.top.toInt() until box.bottom.toInt()) {
        for (x in box.left.toInt() until box.right.toInt()) {
            val argb = pixels[y * width + x]
            counts[argb] = (counts[argb] ?: 0) + 1
        }
    }
    val background = counts.maxByOrNull { it.value }?.key ?: return null
    val back = Color(background)
    val front = counts.filter { it.key != background && it.value >= MIN_PIXELS }
        .maxByOrNull { contrast(Color(it.key), back) }?.key ?: return null
    return Measure(contrast(Color(front), back), front, background)
}

private const val MIN_PIXELS = 3

/** Time given to a screen to finish its entrance animations before it is measured. */
private const val SETTLE_MS = 1_500L

private fun SemanticsNode.isDisabled(): Boolean {
    var node: SemanticsNode? = this
    while (node != null) {
        if (node.config.getOrNull(SemanticsProperties.Disabled) != null) return true
        node = node.parent
    }
    return false
}

private fun SemanticsNode.isLargeText(): Boolean {
    val layouts = mutableListOf<TextLayoutResult>()
    config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
    val style = layouts.firstOrNull()?.layoutInput?.style ?: return false
    val size = style.fontSize.takeIf { it.isSp }?.value ?: return false
    val bold = (style.fontWeight ?: FontWeight.Normal) >= FontWeight.Bold
    return size >= 24f || (bold && size >= 18.66f)
}
