package fr.familia.app.feature.gifts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.viewas.listsSeenBy
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.presentation.LocalPresenting
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * « 1 surprise en préparation » on the birthday card (retour d'essai du 17/11): shown to those who
 * prepare it, opening it, and hidden while the mode présentation is on (the tab may be on screen at
 * the party). Fictional family only (TestFamily).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class GiftsSurpriseLineTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 15)

    /** adult1 has a birthday in five days; adult2 prepares a surprise for it. */
    private val family = TestFamily.family.copy(
        members = TestFamily.family.members.map { if (it.id == "adult1") it.copy(birthDate = LocalDate.of(1990, 11, 20)) else it },
    )
    private val surprise = GiftList(
        "s1", GiftListKind.Surprise, "Surprise s1", GiftOccasion.Birthday, LocalDate.of(2026, 11, 20),
        "adult1", listOf("admin1", "adult2"), "adult2", "Adult2", false,
    )

    private fun show(reader: String, presenting: Boolean = false, opened: MutableList<String> = mutableListOf()) {
        val state = giftsState(family, listsSeenBy(listOf(surprise), reader), reader, today) as GiftsUiState.Success
        compose.setContent {
            FamiliaTheme {
                CompositionLocalProvider(LocalPresenting provides presenting) {
                    GiftsScreen(state, onOpenList = { opened += it }, onNewList = {}, onRetry = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun `the one who prepares it sees the surprise on the birthday and opens it`() {
        val opened = mutableListOf<String>()
        show("adult2", opened = opened)
        compose.onNodeWithText("1 surprise en préparation").performClick()
        assertEquals(listOf("s1"), opened)
        assertEquals(1, count("Préparer une surprise"))
    }

    @Test
    fun `its person sees nothing of it`() {
        show("adult1")
        assertEquals(0, count("en préparation"))
    }

    @Test
    fun `while the mode présentation is on, the line is hidden and the button stays`() {
        show("adult2", presenting = true)
        assertEquals(0, count("en préparation"))
        assertEquals(1, count("Préparer une surprise"))
    }

    @Test
    fun `while the mode présentation is on, a surprise in progress leaves the lists, a revealed one stays`() {
        val own = surprise.copy(id = "o1", kind = GiftListKind.Own, title = "Liste o1")
        val revealed = surprise.copy(id = "s2", revealed = true)
        val lists = listOf(own, surprise, revealed)
        assertEquals(listOf("o1", "s2"), shownLists(lists, presenting = true).map { it.id })
        assertEquals(listOf("o1", "s1", "s2"), shownLists(lists, presenting = false).map { it.id })
    }
}
