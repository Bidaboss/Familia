package fr.familia.app.feature.members.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.KinLink
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberCard
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.core.model.Souvenir
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
import java.time.Instant
import java.time.LocalDate

/** « Une fiche de membre organisée » (PRD rév. 290, récit 66). Fictional family only (TestFamily). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2000dp-xxhdpi")
class MemberProfileV2Test {

    @get:Rule
    val compose = createComposeRule()

    private val june = LocalDate.of(2026, 6, 10)
    private val family = TestFamily.family

    @Test
    fun `age and days to the birthday`() {
        val birth = LocalDate.of(1990, 6, 22)
        assertEquals(35, ageOn(birth, june))
        assertEquals(12, daysUntilBirthday(birth, june))
        assertEquals(0, daysUntilBirthday(LocalDate.of(1990, 6, 10), june))
        assertEquals(36, ageOn(LocalDate.of(1990, 6, 10), june))
    }

    @Test
    fun `the family of a person, from the tree and the parents of the members, with my words`() {
        val grandma = Kin("k1", "Mamie Rose", living = true, householdId = "h1")
        val links = listOf(KinLink.parent("k1", "adult1"), KinLink.partners("adult1", "admin1"))
        val kin = kinshipOf("adult1", family, listOf(grandma), links, words = mapOf("k1" to "Mamie"))
        assertEquals(listOf("Mamie"), kin.parents.map { it.name })
        assertEquals(listOf("admin1"), kin.partners.map { it.id })
        assertEquals(listOf("kid1"), kin.children.map { it.id })
        assertEquals(null, kin.parents.single().member)
        assertTrue(kinshipOf("adult2", family, emptyList(), emptyList(), emptyMap()).parents.isEmpty())
    }

    private fun event(id: String, day: Int, target: EventTarget = EventTarget.ForFamily) =
        Event(id, "Repas $id", LocalDate.of(2026, 6, day), null, null, null, target, "admin1", "Admin1")

    @Test
    fun `the next events they come to, three at most`() {
        val events = listOf(event("e4", 20), event("e1", 12), event("e2", 14), event("e3", 16), event("e5", 18), event("other", 13, EventTarget.ForHousehold("h2")))
        fun yes(id: String) = id to listOf(Rsvp("adult1", RsvpStatus.Yes, "adult1"))
        val answers = mapOf(yes("e1"), yes("e2"), yes("e4"), yes("other"), "e3" to listOf(Rsvp("adult1", RsvpStatus.No, "adult1")), yes("e5"))
        assertEquals(listOf("e1", "e2", "e5"), nextMomentsOf(events, answers, TestFamily.adult, family).map { it.id })
    }

    @Test
    fun `their photos, never one I hid`() {
        fun news(id: String, forId: String, hidden: List<String> = emptyList(), photos: List<String> = listOf("p")) =
            GoodNews(id, "Bravo", photos, "admin1", "Admin1", forId, "h1", hiddenByIds = hidden)
        val all = listOf(news("n1", "adult1"), news("n2", "adult1", hidden = listOf("adult2")), news("n3", "adult2"), news("n4", "adult1", photos = emptyList()))
        assertEquals(listOf("n1"), photosOf(all, "adult1", "adult2").map { it.id })
    }

    @Test
    fun `a phone as people type it`() {
        assertTrue(isPhone("+33 6 12 34 56 78"))
        assertTrue(isPhone("06.12.34.56.78"))
        assertFalse(isPhone("appelle-moi"))
        assertFalse(isPhone("12"))
    }

    private fun ready(member: Member, isMe: Boolean, souvenirs: List<Souvenir> = emptyList()) =
        MemberProfileUiState.Ready(member, "Foyer Un", isMe, canEdit = isMe, souvenirs = souvenirs)

    private fun v2(
        today: LocalDate = june,
        kin: Kinship = Kinship(emptyList(), emptyList(), emptyList()),
        address: String? = null,
        card: MemberCard? = MemberCard("x"),
        canEditCard: Boolean = false,
        recipeCount: Int = 0,
    ) = ProfileV2State(
        today = today,
        household = family.households.first().copy(address = address),
        kinship = kin,
        moments = emptyList(),
        photos = emptyList(),
        managesHousehold = true,
        card = card,
        canEditCard = canEditCard,
        recipeCount = recipeCount,
    )

    private fun show(
        member: Member,
        isMe: Boolean,
        state: ProfileV2State = v2(),
        souvenirs: List<Souvenir> = emptyList(),
        onPrepareSurprise: (() -> Unit)? = {},
        onAddRecipe: (() -> Unit)? = null,
    ) {
        compose.setContent {
            FamiliaTheme {
                Column {
                    ContentV2(
                        ready(member, isMe, souvenirs),
                        ProfileV2Ui(state, onOpenWishes = {}, onPrepareSurprise = onPrepareSurprise, onOpenRecipes = {}, onAddRecipe = onAddRecipe),
                        wishes = null,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun described(text: String) = compose.onAllNodesWithContentDescription(text, substring = true).fetchSemanticsNodes().size

    private fun topOf(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().first().boundsInRoot.top

    @Test
    fun `on the page of someone else, an empty section does not show`() {
        show(TestFamily.otherAdult, isMe = false)
        assertEquals(0, count("Ses moments"))
        assertEquals(0, count("Coordonnées"))
        assertEquals(0, count("À savoir"))
        assertEquals(0, count("Goûts et passions"))
        assertEquals(0, count("En ce moment"))
        assertEquals(1, count("Cadeaux"))
        assertEquals(1, count("Ses envies (0)"))
    }

    @Test
    fun `on my page, the empty sections invite to fill them`() {
        show(TestFamily.adult, isMe = true, state = v2(canEditCard = true))
        assertEquals(1, count("Mes moments"))
        assertEquals(1, count("Ajouter mon téléphone"))
        assertEquals(1, count("Ajouter l'adresse du foyer"))
        assertEquals(1, count("Dis aux autres ce que tu fais"))
        // « Non précisé », never « Aucune »: nobody reads « no allergy » where nothing was written.
        assertEquals(3, count("Non précisé"))
        assertEquals(0, count("Aucune"))
        assertEquals(1, count("Remplir mes goûts"))
    }

    @Test
    fun `the sections come in the order of the brief`() {
        val card = MemberCard("adult2", allergies = "Arachides", tastes = "Le thé vert")
        show(TestFamily.otherAdult, isMe = false, state = v2(card = card, recipeCount = 2), souvenirs = listOf(Souvenir("firstGoodNews", Instant.parse("2026-05-02T10:00:00Z"), true)))
        val order = listOf("À savoir", "Goûts et passions", "Cadeaux", "Recettes", "Ses réussites").map { topOf(it) }
        assertEquals(order.sorted(), order)
    }

    @Test
    fun `the card of someone else shows what they wrote, with their surnom`() {
        val card = MemberCard("adult2", now = "Je prépare le repas de Noël", allergies = "Arachides", avoid = "Le parfum", top = "M", ring = "52")
        show(TestFamily.otherAdult.copy(nickname = "Mamie"), isMe = false, state = v2(card = card))
        assertEquals(1, count("« Mamie »"))
        assertEquals(1, count("Je prépare le repas de Noël"))
        assertEquals(0, count("Changer"))
        assertEquals(1, count("Arachides"))
        assertEquals(1, count("Ne pas offrir"))
        assertEquals(1, count("Le parfum"))
        assertEquals(1, count("Taille haut"))
        assertEquals(1, count("Tour de doigt"))
        assertEquals(0, count("Pointure"))
    }

    @Test
    fun `En ce moment is changed from my page`() {
        show(TestFamily.adult, isMe = true, state = v2(card = MemberCard("adult1", now = "Je lis"), canEditCard = true))
        assertEquals(1, count("Je lis"))
        assertEquals(1, count("Changer"))
        assertEquals(1, count("Ne m'offrez pas"))
    }

    @Test
    fun `never coordinates for a child, even on their page`() {
        show(TestFamily.kid, isMe = true)
        assertEquals(0, count("Coordonnées"))
        assertEquals(0, count("Ajouter mon téléphone"))
    }

    @Test
    fun `a child is reached through their parents`() {
        val parent = TestFamily.adult.copy(phone = "06 12 34 56 78")
        val kin = Kinship(listOf(KinPerson("adult1", "Adult1", parent), KinPerson("k1", "Mamie Rose", null)), emptyList(), emptyList())
        show(TestFamily.kid, isMe = false, state = v2(kin = kin))
        assertEquals(1, count("Pour joindre Kid1, passe par ses parents : Adult1 et Mamie Rose."))
        assertEquals(1, count("Appeler Adult1"))
    }

    @Test
    fun `someone else's phone and address give Appeler, Message and Itinéraire`() {
        show(TestFamily.adult.copy(phone = "06 12 34 56 78"), isMe = false, state = v2(address = "1 rue des Lilas"))
        assertEquals(1, count("Appeler"))
        assertEquals(1, count("Message"))
        assertEquals(1, described("Appeler"))
        assertEquals(1, described("Itinéraire"))
        assertEquals(1, count("06 12 34 56 78"))
        assertEquals(1, count("1 rue des Lilas"))
    }

    @Test
    fun `Préparer une surprise on someone else's page`() {
        show(TestFamily.otherAdult, isMe = false)
        assertEquals(1, count("Préparer une surprise"))
        assertEquals(1, count("Adult2 ne la verra jamais"))
    }

    @Test
    fun `never Préparer une surprise on my own page`() {
        show(TestFamily.adult, isMe = true)
        assertEquals(0, count("Préparer une surprise"))
        assertEquals(1, count("Mes envies (0)"))
    }

    @Test
    fun `the recipes they wrote, without Ajouter une recette`() {
        show(TestFamily.otherAdult, isMe = false, state = v2(recipeCount = 2), onAddRecipe = {})
        assertEquals(1, count("Voir ses recettes"))
        assertEquals(1, count("2 recettes"))
        assertEquals(0, count("Ajouter une recette"))
    }

    @Test
    fun `Ajouter une recette on my own page`() {
        show(TestFamily.adult, isMe = true, onAddRecipe = {})
        assertEquals(1, count("Pas encore de recette"))
        assertEquals(1, count("Ajouter une recette"))
    }

    @Test
    fun `the réussites are public, with no switch`() {
        val souvenirs = listOf(Souvenir("firstGoodNews", Instant.parse("2026-05-02T10:00:00Z"), true))
        show(TestFamily.otherAdult, isMe = false, souvenirs = souvenirs)
        assertEquals(1, count("Ses réussites"))
        assertEquals(1, count("Première actualité"))
        assertEquals(1, count("le 2 mai 2026"))
        assertEquals(0, count("Montrer sur ma fiche"))
    }
}
