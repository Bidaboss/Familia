package fr.familia.app.feature.gifts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.checkAccessibility
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.presentation.LocalPresenting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The new Cadeaux tab (PRODUIT-119 and 120, maquettes C1 à C5). Fictional family only (TestFamily):
 * admin1 and adult1 in h1 with kid1, adult2 in h2 with kid2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xxhdpi", fontScale = 1.3f)
class GiftsTabV2Test {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 18)

    /** adult2 celebrates in nine days, kid2 in June. */
    private val family = TestFamily.family.copy(
        members = TestFamily.family.members.map {
            when (it.id) {
                "adult2" -> it.copy(birthDate = LocalDate.of(1988, 11, 27))
                "kid2" -> it.copy(birthDate = LocalDate.of(2016, 6, 3))
                else -> it
            }
        },
    )

    private fun stateFor(reader: String, day: LocalDate = today, tabV2: Boolean = true, singleList: Boolean = true): GiftsUiState.Success =
        (giftsState(family, emptyList(), reader, day, singleList = singleList, surpriseButton = true, tabV2 = tabV2) as GiftsUiState.Success)

    private fun show(state: GiftsUiState.Success, presenting: Boolean = false, someListsHidden: Boolean = false) {
        compose.setContent {
            FamiliaTheme {
                CompositionLocalProvider(LocalPresenting provides presenting) {
                    GiftsScreen(state, onOpenList = {}, onNewList = {}, onRetry = {}, someListsHidden = someListsHidden, onOpenSurprisePicker = {}, onOpenMyPage = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun button(label: String) = compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().size

    private fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun `the new tab needs its switch and the single list`() {
        assertTrue(stateFor("adult1").tabV2)
        assertFalse(stateFor("adult1", tabV2 = false).tabV2)
        assertFalse(stateFor("adult1", singleList = false).tabV2)
    }

    @Test
    fun `my household first, the others after`() {
        val state = stateFor("adult2")
        val blocks = householdBlocks(state.sections.filter { !it.isMe }, "h2")
        assertEquals(listOf("h2", "h1"), blocks.map { it.key })
        assertTrue(blocks.first().mine)
        assertEquals(listOf("kid2"), blocks.first().sections.map { it.member.id })
    }

    @Test
    fun `a birthday in relief in the 30 days before, none out of season`() {
        assertEquals("adult2", birthdayInRelief(stateFor("adult1"))?.member?.id)
        assertNull(birthdayInRelief(stateFor("adult1", day = LocalDate.of(2026, 7, 1))))
    }

    @Test
    fun `an adult sees the next birthday in relief, mine open, the others folded`() {
        val state = stateFor("adult1")
        show(state)
        assertEquals(1, count("Adult2 fête ses 38 ans"))
        assertEquals(1, count("PROCHAIN ANNIVERSAIRE"))
        // My household open: admin1 and kid1 each have a card; Foyer Deux folded.
        assertEquals(1, count("Admin1"))
        assertEquals(1, count("Foyer Deux"))
        assertEquals(0, count("Anniversaire le 3 juin"))
        compose.onNodeWithText("Foyer Deux").performClick()
        compose.waitForIdle()
        assertEquals(1, count("Anniversaire le 3 juin"))
        assertTrue(compose.onAllNodesWithContentDescription("Idées pour Kid2").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `the card says how many wishes, never ideas nor reservations`() {
        val base = stateFor("adult1")
        val wishes = fr.familia.app.core.model.GiftList(
            "wishes-admin1", fr.familia.app.core.model.GiftListKind.Wishes, "Mes envies", null, null, "admin1", emptyList(), "admin1", "Admin1",
        )
        val state = base.copy(
            sections = base.sections.map { if (it.member.id == "admin1") it.copy(wishes = wishes) else it },
            wishPreviews = mapOf("wishes-admin1" to WishPreview(6, listOf("Un livre"))),
        )
        show(state)
        assertEquals(1, count("6 envies"))
        assertEquals(0, count("idée"))
        assertEquals(0, count("réserv"))
        assertEquals(0, count("Réserv"))
    }

    @Test
    fun `a surprise in progress leads the tab, never while presenting nor in « Voir comme »`() {
        val state = stateFor("adult1").copy(surprisePreparedFor = setOf("adult2"))
        show(state)
        assertEquals(1, count("SURPRISE EN PRÉPARATION"))
        assertEquals(1, count("Ouvrir la surprise"))
        assertEquals(1, button("Préparer une surprise"))
        assertTrue(top("SURPRISE EN PRÉPARATION") < top("À venir"))
    }

    @Test
    fun `in presentation mode, no surprise and no button to prepare one`() {
        show(stateFor("adult1").copy(surprisePreparedFor = setOf("adult2")), presenting = true)
        assertEquals(0, count("Surprise"))
        assertEquals(0, count("SURPRISE"))
        assertEquals(0, button("Préparer une surprise"))
        assertEquals(1, count("Adult2 fête ses 38 ans"))
    }

    @Test
    fun `in « Voir comme », no surprise either`() {
        show(stateFor("adult1").copy(surprisePreparedFor = setOf("adult2")), someListsHidden = true)
        assertEquals(0, count("SURPRISE"))
        assertEquals(0, button("Préparer une surprise"))
    }

    @Test
    fun `in « Voir comme » the person celebrated, nothing reserved, prepared nor given shows`() {
        // adult2 celebrates in nine days; the admin looks as them, and someone prepares something for adult1.
        show(stateFor("adult2").copy(surprisePreparedFor = setOf("adult1")), someListsHidden = true)
        assertEquals(1, count("Tu fêtes tes 38 ans"))
        assertEquals(0, count("SURPRISE"))
        assertEquals(0, count("Surprise"))
        assertEquals(0, button("Préparer une surprise"))
        assertEquals(0, count("réserv"))
        assertEquals(0, count("Réserv"))
        assertEquals(0, count("Ce que j"))
        assertEquals(0, count("idée"))
    }

    @Test
    fun `in presentation mode on the phone of a giver, the same`() {
        show(stateFor("adult1").copy(surprisePreparedFor = setOf("adult2")), presenting = true)
        assertEquals(0, count("Surprise"))
        assertEquals(0, count("réserv"))
        assertEquals(0, count("Ce que j"))
        assertEquals(0, button("Préparer une surprise"))
    }

    @Test
    fun `a child finds their wishes first, and never « Idées »`() {
        show(stateFor("kid1"))
        assertTrue(top("Mes envies") < top("À venir"))
        assertEquals(0, count("Idées"))
        assertEquals(0, button("Préparer une surprise"))
        assertTrue(count("Ses envies") > 0)
    }

    @Test
    fun `out of season, compact lines only`() {
        show(stateFor("adult1", day = LocalDate.of(2026, 3, 1)))
        assertEquals(0, count("PROCHAIN ANNIVERSAIRE"))
        // A compact line (the chips come from the ViewModel, not from this state).
        assertEquals(1, count("Anniversaire de Kid2"))
    }

    @Test
    fun `Christmas shows from the 1st of November`() {
        show(stateFor("adult1"))
        assertTrue(count("Noël") > 0)
    }

    @Test
    fun `at 130 percent every target is big enough and named`() = compose.checkAccessibility {
        GiftsScreen(stateFor("adult1").copy(surprisePreparedFor = setOf("adult2")), onOpenList = {}, onNewList = {}, onRetry = {}, onOpenSurprisePicker = {}, onOpenMyPage = {})
    }
}
