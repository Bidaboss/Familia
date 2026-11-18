package fr.familia.app.feature.members.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.core.viewas.ViewAsControl
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.gifts.profile.ProfileGivingSection
import fr.familia.app.feature.gifts.profile.ProfileNotebookSection
import fr.familia.app.feature.gifts.profile.ProfileWishesSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The new member page in front of the person celebrated (essais avant le 21, AVIS-22): in « Voir
 * comme » them, nothing of what is given to them shows, nor my notebook,
 * nor a reservation. The real sections of the Cadeaux module are drawn, as the page draws them.
 * Fictional family only (TestFamily): adult1 celebrates in ten days, adult2 gives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xxhdpi")
class MemberProfileV2SafetyTest {

    @get:Rule
    val compose = createComposeRule()

    private val june = LocalDate.of(2026, 6, 10)
    private val celebrated = TestFamily.adult.copy(birthDate = LocalDate.of(1990, 6, 20), phone = "06 12 34 56 78")
    private val giver = TestFamily.otherAdult

    private fun v2() = ProfileV2State(
        today = june,
        household = TestFamily.family.households.first(),
        kinship = Kinship(emptyList(), emptyList(), emptyList()),
        moments = emptyList(),
        photos = emptyList(),
        managesHousehold = false,
    )

    /** The page of [member] read by [reader], with the gifts slot the route gives it. */
    private fun show(reader: Member, member: Member, viewing: Member? = null) {
        val gifts: @Composable (Member) -> Unit = { person ->
            ProfileWishesSection(null, reader, person, onOpenList = {})
            ProfileNotebookSection(reader, person, notebookOn = true, onOpenNotebook = {})
            ProfileGivingSection(null, reader, person, singleList = true, notebookOn = true, {}, {}, {}, {}, {})
        }
        compose.setContent {
            FamiliaTheme {
                CompositionLocalProvider(LocalViewAs provides ViewAsControl(viewed = viewing)) {
                    Column {
                        ContentV2(
                            MemberProfileUiState.Ready(member, "Foyer Un", reader.id == member.id, canEdit = reader.id == member.id, souvenirs = emptyList()),
                            ProfileV2Ui(v2(), onSavePhoto = {}),
                            onSetShown = { _, _ -> }, onOpenRecipes = null,
                            wishes = null, card = null, gifts = gifts,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun count(text: String, ignoreCase: Boolean = true) =
        compose.onAllNodesWithText(text, substring = true, ignoreCase = ignoreCase).fetchSemanticsNodes().size

    private fun described(text: String) = compose.onAllNodesWithContentDescription(text, substring = true, ignoreCase = true).fetchSemanticsNodes().size

    private fun assertNothingHidden() {
        assertEquals(0, count("Ce que je lui offre"))
        assertEquals(0, count("ne le voit pas"))
        assertEquals(0, count("Mes cadeaux à offrir"))
        assertEquals(0, count("réserv"))
        assertEquals(0, count("Pris", ignoreCase = false))
        assertEquals(0, count("surprise"))
    }

    @Test
    fun `the giver sees what they give, kept private`() {
        // The control: without « Voir comme », the box is there.
        show(giver, celebrated)
        assertTrue(count("Ce que je lui offre") > 0)
        assertTrue(count("Adult1 ne le voit pas") > 0)
    }

    @Test
    fun `in « Voir comme » the person celebrated, their page shows nothing given to them`() {
        show(celebrated, celebrated, viewing = celebrated)
        assertNothingHidden()
        // Nor the gauge nor the photo button: « Voir comme » never writes.
        assertEquals(0, count("Ta fiche est remplie"))
        assertEquals(0, described("Changer ma photo"))
    }

    @Test
    fun `in « Voir comme » someone else, the page of the person celebrated shows nothing given either`() {
        show(giver, celebrated, viewing = giver)
        assertNothingHidden()
    }

    @Test
    fun `my notebook is on my page outside « Voir comme »`() {
        // The control of the « Voir comme » tests above.
        show(celebrated, celebrated)
        assertTrue(count("Mes cadeaux à offrir") > 0)
    }
}
