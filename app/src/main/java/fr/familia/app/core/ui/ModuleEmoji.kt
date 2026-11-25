package fr.familia.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.TextUnit

/**
 * The picto of a module (Tout Familia et barre du bas, récit 120): an emoji in color, decorative (the
 * label beside it names the module). [double]: the same emoji twice, a small one tilted -10° behind,
 * a big one tilted 8° in front (Voyages 🧳🧳, Cadeaux 🎁🎁, maquette tout-familia.html, « .dv »).
 */
@Composable
fun ModuleEmoji(emoji: String, size: TextUnit, modifier: Modifier = Modifier, double: Boolean = false) {
    val style = LocalTextStyle.current.copy(platformStyle = PlatformTextStyle(includeFontPadding = false))
    if (!double) {
        Text(emoji, style = style.copy(fontSize = size, lineHeight = size), modifier = modifier.clearAndSetSemantics { })
        return
    }
    val density = LocalDensity.current
    val width = with(density) { (size * 1.75f).toDp() }
    val height = with(density) { (size * 1.2f).toDp() }
    Box(modifier.size(width, height).clearAndSetSemantics { }) {
        val back = size * 0.8f
        val front = size * 1.05f
        Text(emoji, style = style.copy(fontSize = back, lineHeight = back), modifier = Modifier.align(Alignment.TopStart).rotate(-10f))
        Text(emoji, style = style.copy(fontSize = front, lineHeight = front), modifier = Modifier.align(Alignment.BottomEnd).rotate(8f))
    }
}
