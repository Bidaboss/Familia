package fr.familia.app.feature.trips.documents

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.PickGroup
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** « Privé » or « Partagé avec… » as tiles (refonte, DESIGN-57). Fictional first names only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xxhdpi")
class TripDocumentsTilesTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `private or shared are two tiles, each a radio for TalkBack`() {
        var shared by mutableStateOf(false)
        val lou = Member("m2", "Lou", "m2@example.com", null, "h1", MemberProfile.Adult, false, emptyList(), "uid-m2")
        compose.setContent {
            FamiliaTheme {
                Column {
                    SharingChoice(listOf(PickGroup("Foyer Un", listOf(lou))), shared, { shared = it }, emptySet(), {})
                }
            }
        }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(2)
        compose.onNodeWithText("Privé : toi seul").assertIsSelected()
        compose.onNodeWithText("Partagé avec…").performClick()
        assertTrue(shared)
        compose.onNodeWithText("Partagé avec…").assertIsSelected()
    }
}
