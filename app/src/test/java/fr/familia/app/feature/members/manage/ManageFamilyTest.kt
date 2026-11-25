package fr.familia.app.feature.members.manage

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.HouseholdGroup
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.checkAccessibility
import fr.familia.app.feature.members.MembersUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Gérer la famille (brief Famille-recettes F5). Fictional family. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class ManageFamilyTest {

    @get:Rule
    val compose = createComposeRule()

    private fun m(id: String, home: String, profile: MemberProfile = MemberProfile.Adult, email: String? = "$id@exemple.fr", uid: String? = "u-$id") =
        Member(id, id.replaceFirstChar { it.uppercase() }, email, null, home, profile, false, emptyList(), uid)

    private val groups = listOf(
        HouseholdGroup(
            Household("h1", "Foyer Martin"),
            listOf(m("camille", "h1"), m("ines", "h1", uid = null), m("noe", "h1", MemberProfile.ChildWithoutApp, email = null, uid = null)),
        ),
        HouseholdGroup(Household("h2", "Foyer Bernard"), listOf(m("lou", "h2", MemberProfile.ChildWithApp, email = null, uid = null))),
    )

    private fun owner() = MembersUiState.Success(groups, "camille", ownerId = "camille", isOwner = true)

    @Test
    fun `the invitations are those not yet in the app, never a child without the app`() {
        assertEquals(listOf("ines", "lou"), pendingInvitations(groups).map { it.id })
    }

    @Test
    fun `the admin principal is never offered for removal`() {
        assertEquals(listOf(listOf("ines", "noe"), listOf("lou")), removable(groups, "camille").map { g -> g.members.map { it.id } })
    }

    @Test
    fun `Relancer shares the invitation, a missing address opens the form`() {
        var reminded: String? = null
        var edited: String? = null
        compose.setContent {
            FamiliaTheme {
                ManageFamilyScreen(owner(), onBack = {}, onAdd = {}, onEdit = { edited = it }, onRemind = { reminded = it.id })
            }
        }
        compose.onNodeWithText("Invitations").assertIsDisplayed()
        compose.onNodeWithContentDescription("Relancer Ines").performClick()
        assertEquals("ines", reminded)
        compose.onNodeWithText("Ajouter").performClick()
        assertEquals("lou", edited)
    }

    @Test
    fun `Retirer un membre picks a member by household, then opens the form`() {
        var edited: String? = null
        compose.setContent {
            FamiliaTheme {
                ManageFamilyScreen(owner(), onBack = {}, onAdd = {}, onEdit = { edited = it }, onRemind = {})
            }
        }
        compose.onNodeWithText("Retirer un membre").performClick()
        compose.onNodeWithText("Qui retirer de Familia ?").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("Camille")).fetchSemanticsNodes().size)
        compose.onNodeWithText("Noe").performClick()
        assertEquals("noe", edited)
    }

    @Test
    fun `a member who is not the admin principal sees nothing to manage`() {
        compose.setContent {
            FamiliaTheme {
                ManageFamilyScreen(owner().copy(currentMemberId = "ines", isOwner = false), onBack = {}, onAdd = {}, onEdit = {}, onRemind = {})
            }
        }
        compose.onNodeWithText("Réservé à l'administrateur principal.").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("Ajouter un membre")).fetchSemanticsNodes().size)
    }

    @Test
    @Config(fontScale = 2f)
    fun `at twice the text size, everything is read and tall enough`() = compose.checkAccessibility {
        ManageFamilyScreen(owner(), onBack = {}, onAdd = {}, onEdit = {}, onRemind = {})
    }
}
