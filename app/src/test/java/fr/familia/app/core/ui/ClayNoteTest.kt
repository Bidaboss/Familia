package fr.familia.app.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.familia.app.core.theme.FamiliaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Le « Carnet post-it » de Ma journée (brief §8): a card becomes a note, a title is handwritten. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class ClayNoteTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a card on a note keeps its content and its tap`() {
        var taps = 0
        compose.setContent {
            FamiliaTheme {
                CompositionLocalProvider(LocalClayNote provides ClayNote.Yellow) {
                    ClayCard(onClick = { taps++ }) { Text("Piscine à 10 h") }
                }
            }
        }
        compose.onNodeWithText("Piscine à 10 h").assertHasClickAction().performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `a title of the carnet is written as given and stays a heading`() {
        compose.setContent {
            FamiliaTheme {
                CompositionLocalProvider(LocalCarnetTitles provides true) { SectionLabel("À faire") }
            }
        }
        compose.onNodeWithText("À faire").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `elsewhere a title stays in capitals`() {
        compose.setContent { FamiliaTheme { SectionLabel("À faire") } }
        compose.onNodeWithText("À FAIRE").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }
}
