package fr.familia.app.feature.members.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberCard
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.events.TestFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The member page redrawn after the maquettes P1 à P5 (PRODUIT-114 à 116), read at 130 % as the
 * family may set it. Fictional family only (TestFamily).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xxhdpi", fontScale = 1.3f)
class MemberProfileV2DesignTest {

    @get:Rule
    val compose = createComposeRule()

    private val june = LocalDate.of(2026, 6, 10)
    private val none = Kinship(emptyList(), emptyList(), emptyList())

    private fun v2(kin: Kinship = none, address: String? = null, hasTastes: Boolean = false, wishCount: Int = 0, recipeCount: Int = 0) = ProfileV2State(
        today = june,
        household = TestFamily.family.households.first().copy(address = address),
        kinship = kin,
        moments = emptyList(),
        photos = emptyList(),
        managesHousehold = true,
        hasTastes = hasTastes,
        wishCount = wishCount,
        recipeCount = recipeCount,
        card = MemberCard("x", now = "Je prépare le repas de Noël pour toute la famille", allergies = "Arachides", tastes = "Le chocolat noir, les polars"),
        canEditCard = true,
    )

    private fun show(member: Member, isMe: Boolean, state: ProfileV2State = v2()) {
        compose.setContent {
            FamiliaTheme {
                Column {
                    ContentV2(
                        MemberProfileUiState.Ready(member, "Foyer Un", isMe, canEdit = isMe, souvenirs = emptyList()),
                        ProfileV2Ui(state, onOpenWishes = {}, onPrepareSurprise = {}, onOpenRecipes = {}, onAddRecipe = {}),
                        wishes = null,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun `the gauge counts six parts for an adult, five for a child, rounded down to the ten`() {
        val adult = TestFamily.adult
        val empty = fillParts(adult, hasPhoto = false, data = v2(), souvenirShown = false)
        assertEquals(FillPart.entries.toList(), empty.map { it.first })
        assertEquals(0, fillPercent(empty))
        val some = fillParts(adult.copy(phone = "06 12 34 56 78"), hasPhoto = true, data = v2(hasTastes = true, wishCount = 2), souvenirShown = false)
        assertEquals(60, fillPercent(some))
        val kid = fillParts(TestFamily.kid, hasPhoto = true, data = v2(hasTastes = true), souvenirShown = true)
        assertFalse(kid.any { it.first == FillPart.Phone })
        assertEquals(60, fillPercent(kid))
        val full = fillParts(TestFamily.kid, hasPhoto = true, data = v2(kin = kinOfKid(), hasTastes = true, wishCount = 1), souvenirShown = true)
        assertEquals(100, fillPercent(full))
    }

    @Test
    fun `a module that is off never counts against me`() {
        val data = v2().copy(fillable = setOf(FillPart.RecipeOrSouvenir, FillPart.Phone))
        val parts = fillParts(TestFamily.adult, hasPhoto = false, data = data, souvenirShown = true)
        assertEquals(listOf(FillPart.RecipeOrSouvenir, FillPart.Phone), parts.map { it.first })
        assertEquals(50, fillPercent(parts))
    }

    @Test
    fun `the birthday is soon in the 30 days before`() {
        assertTrue(birthdaySoon(LocalDate.of(1990, 6, 10), june))
        assertTrue(birthdaySoon(LocalDate.of(1990, 7, 10), june))
        assertFalse(birthdaySoon(LocalDate.of(1990, 7, 11), june))
        assertFalse(birthdaySoon(null, june))
    }

    @Test
    fun `my page shows the gauge`() {
        show(TestFamily.adult, isMe = true)
        assertEquals(1, count("Ta fiche est remplie à 0 %"))
    }

    @Test
    fun `someone else's page never shows the gauge`() {
        show(TestFamily.otherAdult, isMe = false)
        assertEquals(0, count("Ta fiche est remplie"))
    }

    @Test
    fun `soon before someone else's birthday, a dark row in Cadeaux and the pill under the name`() {
        show(TestFamily.otherAdult.copy(birthDate = LocalDate.of(1990, 6, 20)), isMe = false)
        assertEquals(1, count("Son anniversaire est dans 10 jours"))
        assertEquals(1, count("Anniversaire dans 10 jours"))
    }

    @Test
    fun `on my own page, the pill but never the dark row`() {
        show(TestFamily.adult.copy(birthDate = LocalDate.of(1990, 6, 20)), isMe = true)
        assertEquals(0, count("Son anniversaire"))
        assertEquals(1, count("Anniversaire dans 10 jours"))
    }

    @Test
    fun `an adult's age is not written`() {
        show(TestFamily.otherAdult.copy(birthDate = LocalDate.of(1985, 2, 3)), isMe = false)
        assertEquals(1, count("anniversaire le 3 février"))
        assertEquals(0, count("41 ans"))
    }

    @Test
    fun `at 130 percent nothing is cut on someone else's page`() {
        show(parent, isMe = false, state = v2(kin = family, address = ADDRESS))
        assertNothingCut()
    }

    @Test
    fun `at 130 percent nothing is cut on my page`() {
        show(parent, isMe = true, state = v2(kin = family, address = ADDRESS))
        assertNothingCut()
    }

    private val parent = TestFamily.adult.copy(phone = "06 12 34 56 78", birthDate = LocalDate.of(1985, 6, 20))
    private val family = Kinship(emptyList(), listOf(KinPerson("adult2", "Adult2", TestFamily.otherAdult)), listOf(KinPerson("kid1", "Kid1", TestFamily.kid)))

    private fun kinOfKid() = Kinship(listOf(KinPerson("adult1", "Adult1", TestFamily.adult)), emptyList(), emptyList())

    private fun assertNothingCut() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        val cut: List<String> = nodes.mapNotNull { node: SemanticsNode ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val text: String = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { annotated -> annotated.text }
            // Cut means « … » at the end of a line: a text that wraps is fine.
            text.takeIf { layouts.any { layout -> (0 until layout.lineCount).any { line -> layout.isLineEllipsized(line) } } }
        }
        assertTrue("cut at 130 %: $cut", cut.isEmpty())
    }

    private companion object {
        const val ADDRESS = "12 chemin des Grands Peupliers, 33000 Bordeaux"
    }
}
