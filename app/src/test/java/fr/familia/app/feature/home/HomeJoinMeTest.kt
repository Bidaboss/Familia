package fr.familia.app.feature.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.feature.joinme.JoinMeHomeContent
import fr.familia.app.feature.joinme.previewJoinMe
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** « Rejoignez-moi » back on the Accueil (Kévin, 20/11): a place I was sent, above the frise. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1600dp-xxhdpi")
class HomeJoinMeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(withShares: Boolean) = compose.setContent {
        FamiliaTheme {
            HomeR1bisScreen(
                previewHome(), HomeActions(), HomeR1bisActions(),
                joinMe = if (withShares) {
                    {
                        ClayCard {
                            ClayCardTitle("Rejoignez-moi")
                            JoinMeHomeContent(previewJoinMe(Instant.parse("2026-11-21T16:00:00Z")), false, {})
                        }
                    }
                } else {
                    null
                },
            )
        }
    }

    @Test
    fun `a place I was sent is at the top, with Itinéraire, and my own share with Ne plus partager`() {
        show(withShares = true)
        compose.onAllNodesWithText("Plage des Sables", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("Itinéraire").assertCountEquals(1)
        compose.onAllNodesWithText("Ne plus partager").assertCountEquals(1)
        val block = compose.onNodeWithText("Rejoignez-moi").getBoundsInRoot()
        // The first box of the frise: today, Saturday 21.
        val frise = compose.onAllNodes(hasContentDescription("Samedi 21", substring = true)).onFirst().getBoundsInRoot()
        assertTrue(block.top < frise.top)
    }

    @Test
    fun `without a share the Accueil has no Rejoignez-moi`() {
        show(withShares = false)
        compose.onAllNodesWithText("Rejoignez-moi").assertCountEquals(0)
    }
}
