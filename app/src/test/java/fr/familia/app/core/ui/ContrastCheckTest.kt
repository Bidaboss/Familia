package fr.familia.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.sp
import fr.familia.app.core.theme.FamiliaThemeId
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The contrast pass itself: a pale text is caught, a dark one passes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class ContrastCheckTest {

    @get:Rule
    val compose = createComposeRule()

    private fun screen(name: String, content: @Composable () -> Unit) = name to content

    private val light = Look("Classique clair", FamiliaThemeId.Classic)

    @Test
    fun `a pale grey text on white is caught`() {
        val error = assertThrows(AssertionError::class.java) {
            compose.checkContrast(
                light,
                listOf(screen("pâle") { Box(Modifier.background(Color.White)) { Text("Bonjour", color = Color(0xFFBBBBBB), fontSize = 16.sp) } }),
            )
        }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("Bonjour"))
    }

    @Test
    fun `a dark text on white passes`() = compose.checkContrast(
        light,
        listOf(screen("foncé") { Box(Modifier.background(Color.White)) { Text("Bonjour", color = Color(0xFF222222), fontSize = 16.sp) } }),
    )

    @Test
    fun `the WCAG ratio of black on white is 21`() = assertTrue(contrast(Color.Black, Color.White) in 20.9f..21.1f)
}
