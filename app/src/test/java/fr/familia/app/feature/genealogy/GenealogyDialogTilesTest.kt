package fr.familia.app.feature.genealogy

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.TreePerson
import fr.familia.app.core.theme.FamiliaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The choices of the tree's dialogs as tiles (refonte, DESIGN-57). Fictional first names only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xxhdpi")
class GenealogyDialogTilesTest {

    @get:Rule
    val compose = createComposeRule()

    private fun member(id: String, first: String) =
        TreePerson.OfMember(Member(id, first, "$id@example.com", null, "h1", MemberProfile.Adult, false, emptyList(), "uid-$id"))

    @Test
    fun `« Qui veux-tu relier ? » shows one tile per person, a radio for TalkBack`() {
        var picked: String? = null
        compose.setContent {
            FamiliaTheme {
                PickPersonDialog(listOf(member("m1", "Camille"), member("m2", "Lou")), onPick = { picked = it }, onDismiss = {})
            }
        }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(2)
        compose.onNodeWithText("Suivant").assertIsNotEnabled()
        compose.onNodeWithText("Lou").performClick()
        compose.onNodeWithText("Lou").assertIsSelected()
        compose.onNodeWithText("Suivant").assertIsEnabled().performClick()
        assertEquals("m2", picked)
    }
}
