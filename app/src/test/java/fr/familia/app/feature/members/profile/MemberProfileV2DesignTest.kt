package fr.familia.app.feature.members.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.gifts.WishPreview
import fr.familia.app.feature.gifts.profile.ProfileWishesContent
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

    private fun v2(kin: Kinship = none, address: String? = null, hasTastes: Boolean = false, wishCount: Int = 0, hasRecipe: Boolean = false) = ProfileV2State(
        today = june,
        household = TestFamily.family.households.first().copy(address = address),
        kinship = kin,
        moments = emptyList(),
        photos = emptyList(),
        managesHousehold = true,
        hasTastes = hasTastes,
        wishCount = wishCount,
        hasRecipe = hasRecipe,
    )

    private fun show(member: Member, isMe: Boolean, state: ProfileV2State = v2()) {
        compose.setContent {
            FamiliaTheme {
                Column {
                    ContentV2(
                        MemberProfileUiState.Ready(member, "Foyer Un", isMe, canEdit = isMe, souvenirs = emptyList()),
                        ProfileV2Ui(state), onSetShown = { _, _ -> }, onOpenRecipes = null,
                        wishes = null,
                        card = { Text("Carte des goûts") },
                        thinkOfMe = null,
                        gifts = { Text("Carte des cadeaux") },
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
    fun `my page shows the gauge, someone else's never`() {
        show(TestFamily.adult, isMe = true)
        assertEquals(1, count("Ta fiche est remplie à 0 %"))
        show(TestFamily.otherAdult, isMe = false)
        assertEquals(0, count("Ta fiche est remplie"))
    }

    @Test
    fun `a child is reached through the parents who gave a phone`() {
        val parent = TestFamily.adult.copy(phone = "06 12 34 56 78")
        val kin = Kinship(listOf(KinPerson("adult1", "Adult1", parent), KinPerson("other", "Autre", null)), emptyList(), emptyList())
        show(TestFamily.kid, isMe = false, state = v2(kin = kin))
        assertEquals(1, count("Pour joindre Kid1, passe par ses parents."))
        assertEquals(1, count("Appeler Adult1"))
        assertEquals(0, count("Coordonnées"))
    }

    @Test
    fun `no button to reach a child whose parents gave no phone`() {
        show(TestFamily.kid, isMe = false, state = v2(kin = kinOfKid()))
        assertEquals(0, count("Pour joindre"))
    }

    @Test
    fun `soon before someone else's birthday, Cadeaux says Bientôt`() {
        show(TestFamily.otherAdult.copy(birthDate = LocalDate.of(1990, 6, 20)), isMe = false)
        assertEquals(1, count("Bientôt"))
        assertEquals(1, count("Son anniversaire est dans 10 jours"))
        assertEquals(1, count("Anniversaire dans 10 jours"))
    }

    @Test
    fun `never Bientôt on my own page`() {
        show(TestFamily.adult.copy(birthDate = LocalDate.of(1990, 6, 20)), isMe = true)
        assertEquals(0, count("Bientôt"))
    }

    @Test
    fun `my wishes on my page show their titles, never who took them`() {
        compose.setContent {
            FamiliaTheme {
                CompositionLocalProvider(LocalProfileSection provides true) {
                    Column {
                        ProfileWishesContent(WishPreview(2, listOf("Un livre", "Un vélo")), TestFamily.adult, isMe = true, keptByMe = true, onOpen = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(1, count("MES ENVIES (2)"))
        assertEquals(1, count("Un vélo"))
        assertEquals(0, count("Réservé"))
        assertEquals(0, count("Pris"))
    }

    @Test
    fun `at 130 percent nothing is cut, on someone else's page and on mine`() {
        val parent = TestFamily.adult.copy(phone = "06 12 34 56 78", birthDate = LocalDate.of(1985, 6, 20))
        val kin = Kinship(emptyList(), listOf(KinPerson("adult2", "Adult2", TestFamily.otherAdult)), listOf(KinPerson("kid1", "Kid1", TestFamily.kid)))
        show(parent, isMe = false, state = v2(kin = kin, address = "12 chemin des Grands Peupliers, 33000 Bordeaux"))
        assertNothingCut()
        show(parent, isMe = true, state = v2(kin = kin, address = "12 chemin des Grands Peupliers, 33000 Bordeaux"))
        assertNothingCut()
    }

    private fun kinOfKid() = Kinship(listOf(KinPerson("adult1", "Adult1", TestFamily.adult)), emptyList(), emptyList())

    private fun assertNothingCut() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        val cut = nodes.mapNotNull { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            text.takeIf { layouts.any { it.hasVisualOverflow } }
        }
        assertTrue("cut at 130 %: $cut", cut.isEmpty())
    }
}
