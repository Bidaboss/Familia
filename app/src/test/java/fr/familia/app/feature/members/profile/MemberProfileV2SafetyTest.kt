package fr.familia.app.feature.members.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberCard
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.core.viewas.ViewAsControl
import fr.familia.app.feature.events.TestFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The member page in front of the person celebrated (essais avant le 21, AVIS-22): in « Voir
 * comme », nothing of what is prepared for them shows, and the page never writes. Fictional family
 * only (TestFamily): adult1 celebrates in ten days, adult2 gives.
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
        wishCount = 3,
        card = MemberCard("adult1", now = "Je lis"),
        canEditCard = true,
    )

    /** The page of [member] read by [reader], with what the route gives it. */
    private fun show(reader: Member, member: Member, viewing: Member? = null) {
        compose.setContent {
            FamiliaTheme {
                CompositionLocalProvider(LocalViewAs provides ViewAsControl(viewed = viewing)) {
                    Column {
                        ContentV2(
                            MemberProfileUiState.Ready(member, "Foyer Un", reader.id == member.id, canEdit = reader.id == member.id, souvenirs = emptyList()),
                            ProfileV2Ui(v2(), onSavePhoto = {}, onOpenWishes = {}, onPrepareSurprise = {}),
                            wishes = null,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().size

    private fun described(text: String) = compose.onAllNodesWithContentDescription(text, substring = true, ignoreCase = true).fetchSemanticsNodes().size

    @Test
    fun `the giver sees Préparer une surprise`() {
        // The control of the « Voir comme » tests below.
        show(giver, celebrated)
        assertTrue(count("Préparer une surprise") > 0)
    }

    @Test
    fun `in « Voir comme » the person celebrated, their page shows no surprise and never writes`() {
        show(celebrated, celebrated, viewing = celebrated)
        assertEquals(0, count("surprise"))
        assertEquals(0, count("réserv"))
        assertEquals(1, count("Mes envies (3)"))
        // Nor the gauge, nor the photo button, nor « Changer », nor a pencil: « Voir comme » never writes.
        assertEquals(0, count("Ta fiche est remplie"))
        assertEquals(0, described("Changer ma photo"))
        assertEquals(0, count("Changer"))
        assertEquals(0, described("Modifier"))
    }

    @Test
    fun `in « Voir comme » someone else, the page of the person celebrated shows no surprise either`() {
        show(giver, celebrated, viewing = giver)
        assertEquals(0, count("surprise"))
        assertEquals(0, described("Modifier"))
    }
}
