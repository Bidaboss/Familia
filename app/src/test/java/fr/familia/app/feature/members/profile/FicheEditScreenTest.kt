package fr.familia.app.feature.members.profile

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberCard
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.events.TestFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** « Modifier la fiche » on my own page (règle 3, F10, F12). Fictional family only (TestFamily). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2000dp-xxhdpi")
class FicheEditScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val me = TestFamily.adult.copy(birthDate = LocalDate.of(1990, 5, 4), nickname = "Doudou")

    private fun data(member: Member) = ProfileV2State(
        today = LocalDate.of(2026, 6, 10),
        household = TestFamily.family.households.first().copy(address = "1 rue des Lilas"),
        kinship = Kinship(emptyList(), emptyList(), emptyList()),
        moments = emptyList(),
        photos = emptyList(),
        managesHousehold = false,
        card = MemberCard(member.id, allergies = "Arachides"),
        canEditCard = true,
        canRenameHousehold = true,
        householdSize = 3,
        member = member,
    )

    private fun show(
        edit: FicheEdit?,
        member: Member = me,
        drafts: MutableList<FicheDraft> = mutableListOf(),
        cancels: MutableList<Unit> = mutableListOf(),
        backs: MutableList<Unit> = mutableListOf(),
    ) {
        compose.setContent {
            FamiliaTheme {
                MemberProfileScreen(
                    state = MemberProfileUiState.Ready(member, "Foyer Un", isMe = true, canEdit = true, souvenirs = emptyList()),
                    onBack = { backs += Unit },
                    onEdit = {},
                    v2 = ProfileV2Ui(
                        data(member),
                        edit = edit,
                        onStartEdit = {},
                        onDraft = { drafts += it },
                        onCancelEdit = { cancels += Unit },
                    ),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun draft(member: Member = me) = FicheDraft(member.nickname.orEmpty(), "", MemberCard(member.id, allergies = "Arachides"))

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun `the same page with its fields open, the first name and birth date locked`() {
        show(FicheEdit(draft(), changed = false, phoneValid = true))
        assertEquals(1, count("Modifier la fiche"))
        assertEquals(1, count("Seul l'administrateur peut changer le prénom."))
        assertEquals(1, count("Seul l'administrateur peut changer la date de naissance."))
        assertEquals(1, count("4 mai 1990"))
        assertEquals(1, count("Seul l'administrateur peut changer l'adresse du foyer."))
        // Nor « Renommer le foyer » nor « Changer » while « Modifier » is open.
        assertEquals(0, count("Renommer le foyer"))
        assertEquals(0, count("Dis aux autres ce que tu fais"))
        compose.onNodeWithText("Enregistrer").assertIsNotEnabled()
    }

    @Test
    fun `what I type goes to the draft`() {
        val drafts = mutableListOf<FicheDraft>()
        show(FicheEdit(draft(), changed = false, phoneValid = true), drafts = drafts)
        compose.onNodeWithText("Mon téléphone").performScrollTo().performTextInput("0612")
        assertTrue(drafts.last().phone.contains("0612"))
    }

    @Test
    fun `a wrong phone says how to write it`() {
        show(FicheEdit(draft().copy(phone = "appelle-moi"), changed = true, phoneValid = false))
        assertEquals(1, count("Des chiffres seulement"))
        compose.onNodeWithText("Enregistrer").assertIsNotEnabled()
    }

    @Test
    fun `leaving with changes asks first, and Quitter drops them`() {
        val cancels = mutableListOf<Unit>()
        val backs = mutableListOf<Unit>()
        show(FicheEdit(draft().copy(nickname = "Titi"), changed = true, phoneValid = true), cancels = cancels, backs = backs)
        compose.onNodeWithText("Enregistrer").assertIsEnabled()
        compose.onNodeWithContentDescription("Retour").performClick()
        assertEquals(1, count("Quitter sans enregistrer ?"))
        compose.onNodeWithText("Quitter").performClick()
        assertEquals(1, cancels.size)
        assertTrue(backs.isEmpty())
    }

    @Test
    fun `leaving without changes needs no question`() {
        val cancels = mutableListOf<Unit>()
        show(FicheEdit(draft(), changed = false, phoneValid = true), cancels = cancels)
        compose.onNodeWithText("Annuler").performClick()
        assertEquals(0, count("Quitter sans enregistrer ?"))
        assertEquals(1, cancels.size)
    }
}
