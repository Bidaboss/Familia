package fr.familia.app.core.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.dp
import fr.familia.app.core.theme.FamiliaTheme
import org.junit.Assert.assertTrue

/**
 * The common check of the police 200 % and TalkBack pass (first in the Cadeaux, #304): everything
 * one can touch on [content] says what it is (a text or a description, so TalkBack reads it) and
 * is at least 48 dp high. Run it in a test with `@Config(fontScale = 2f)` for twice the text size.
 *
 * Note: Material 3 hides the text of an ExtendedFloatingActionButton from TalkBack, so its icon
 * must carry the label (contentDescription), or the button is read « bouton » and nothing else.
 */
fun ComposeContentTestRule.checkAccessibility(content: @Composable () -> Unit) {
    setContent { FamiliaTheme { Surface { content() } } }
    waitForIdle()
    val touchable = onAllNodes(hasClickAction()).fetchSemanticsNodes()
    assertTrue("nothing to touch on this screen", touchable.isNotEmpty())
    val silent = touchable.filter { it.a11yLabel().isBlank() }
    assertTrue("touchable without a label for TalkBack: ${silent.map { it.where() }}", silent.isEmpty())
    val minPx = with(density) { 48.dp.toPx() }
    // A node cut by the edge of the screen (a list that scrolls) is not judged by its height.
    val small = touchable.filter { it.size.height < minPx - 1f && it.boundsInRoot.height >= it.size.height }
    assertTrue("touchable under 48 dp: ${small.map { "« ${it.a11yLabel()} » ${it.where()}" }}", small.isEmpty())
}

/** What TalkBack reads of a node: its description, its texts and what is typed in it. */
fun SemanticsNode.a11yLabel(): String =
    (config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
        listOfNotNull(config.getOrNull(SemanticsProperties.EditableText)?.text))
        .joinToString(" ")

private fun SemanticsNode.where(): String = "$boundsInRoot (${size.height} px)"
