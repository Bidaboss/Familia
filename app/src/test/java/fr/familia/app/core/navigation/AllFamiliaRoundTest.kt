package fr.familia.app.core.navigation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import fr.familia.app.core.theme.FamiliaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** « Tout Familia » at the center of the bar (récit 82, DESIGN-55 variante A). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AllFamiliaRoundTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `TalkBack reads Tout Familia with its news, and a touch opens it`() {
        var opened = 0
        compose.setContent { FamiliaTheme { AllFamiliaRound(news = 3, onClick = { opened++ }) } }
        compose.onNodeWithContentDescription("Tout Familia, 3 nouveautés").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `without news it reads only Tout Familia`() {
        compose.setContent { FamiliaTheme { AllFamiliaRound(news = 0, onClick = {}) } }
        compose.onAllNodesWithContentDescription("Tout Familia").assertCountEquals(1)
    }
}
