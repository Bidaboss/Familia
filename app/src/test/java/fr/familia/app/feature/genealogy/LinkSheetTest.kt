package fr.familia.app.feature.genealogy

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import fr.familia.app.core.model.GenealogyTree
import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.KinLink
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.checkAccessibility
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sheet of links of the tree (brief Famille-recettes F3). Fictional family. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class LinkSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 25)
    private fun m(id: String, profile: MemberProfile = MemberProfile.Adult, born: LocalDate? = null) =
        Member(id, id.replaceFirstChar { it.uppercase() }, null, born, "h1", profile, false, emptyList(), null)

    private val camille = m("camille")
    private val ines = m("ines")
    private val lou = m("lou", MemberProfile.ChildWithApp, LocalDate.of(2015, 7, 1))
    private val rose = Kin("rose", "Rose", living = true, householdId = "h1")
    private val tree = GenealogyTree(
        listOf(camille, ines, lou),
        listOf(rose),
        listOf(KinLink.partners("camille", "ines"), KinLink.parent("camille", "lou"), KinLink.parent("rose", "camille")),
    )

    @Test
    fun `a child's sheet shows who they are, their parent with Retirer, then the actions in order`() {
        var removed: KinLink? = null
        var opened = false
        compose.setContent {
            FamiliaTheme {
                PersonCard(
                    person = tree.people.getValue("lou"),
                    tree = tree,
                    words = emptyMap(),
                    onOpenProfile = { opened = true },
                    onClose = {},
                    onWord = {},
                    onAddLink = {},
                    onRemoveLink = { removed = it },
                    canEditLink = { true },
                    profileEditable = true,
                    today = today,
                )
            }
        }
        compose.onNodeWithText("Enfant · 11 ans").assertExists()
        compose.onNodeWithText("SES PARENTS").assertExists()
        compose.onNodeWithText("Camille").assertExists()
        compose.onNodeWithText("Choisir ton mot pour Lou").assertExists()
        compose.onNodeWithText("Ajouter un lien").assertExists()
        compose.onNodeWithText("Fermer").assertExists()
        compose.onAllNodesWithText("Retirer de l'arbre").assertCountEquals(0)
        compose.onNodeWithText("Modifier sa fiche").performScrollTo().performClick()
        assertTrue(opened)
        compose.onNodeWithContentDescription("Retirer le lien Parent : Camille").performScrollTo().performClick()
        // Asked once more, in a dialog.
        compose.onNodeWithText("Retirer le lien entre Lou et Camille ?").assertExists()
        compose.onAllNodesWithText("Retirer").onLast().performClick()
        assertEquals(KinLink.parent("camille", "lou"), removed)
    }

    @Test
    fun `a parent's sheet groups the partner and the children, without Retirer for who may not`() {
        compose.setContent {
            FamiliaTheme {
                PersonCard(tree.people.getValue("camille"), tree, emptyMap(), onOpenProfile = {}, onClose = {}, onRemoveLink = {}, today = today)
            }
        }
        compose.onNodeWithText("SES PARENTS").assertExists()
        compose.onNodeWithText("CONJOINT OU CONJOINTE").assertExists()
        compose.onNodeWithText("SES ENFANTS").assertExists()
        compose.onAllNodesWithText("Retirer").assertCountEquals(0)
        // Not mine to change: « Voir sa page ».
        compose.onNodeWithText("Voir sa page").assertExists()
    }

    @Test
    fun `a relative without an account gets Modifier sa fiche and Retirer de l'arbre`() {
        var edited = false
        var removed = false
        compose.setContent {
            FamiliaTheme {
                PersonCard(
                    tree.people.getValue("rose"),
                    tree,
                    emptyMap(),
                    onOpenProfile = null,
                    onClose = {},
                    onEditKin = { edited = true },
                    onRemoveKin = { removed = true },
                    today = today,
                )
            }
        }
        compose.onNodeWithText("Proche sans compte dans Familia").assertExists()
        compose.onNodeWithText("Modifier sa fiche").performScrollTo().performClick()
        compose.onNodeWithText("Retirer de l'arbre").performScrollTo().performClick()
        assertTrue(edited && removed)
    }

    @Test
    fun `the sheet has big enough named targets`() = compose.checkAccessibility {
        FamiliaTheme {
            PersonCard(
                tree.people.getValue("lou"), tree, emptyMap(), onOpenProfile = {}, onClose = {}, onWord = {}, onAddLink = {},
                onRemoveLink = {}, canEditLink = { true }, profileEditable = true, today = today,
            )
        }
    }

    @Test
    fun `the tree offers « Ajouter un proche » to who may edit, and the poster`() {
        var added = false
        var poster = false
        compose.setContent {
            FamiliaTheme {
                TreeOfLifeView(GenealogyUiState.Ready(tree, emptyMap(), "camille", isOwner = true), onOpen = {}, onAddKin = { added = true }, onMakePoster = { poster = true })
            }
        }
        // Over the drawing: reached by their action, the button by the text it holds.
        fun press(text: String) = compose.onNode(hasClickAction() and (hasText(text) or hasAnyDescendant(hasText(text))), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        press("Ajouter un proche")
        press("Faire l'affiche de l'arbre")
        assertTrue(added && poster)
    }

    @Test
    fun `who may not edit gets no « Ajouter un proche »`() {
        compose.setContent {
            FamiliaTheme { TreeOfLifeView(GenealogyUiState.Ready(tree, emptyMap(), "lou"), onOpen = {}, onAddKin = null) }
        }
        compose.onAllNodesWithText("Ajouter un proche", useUnmergedTree = true).assertCountEquals(0)
    }
}
