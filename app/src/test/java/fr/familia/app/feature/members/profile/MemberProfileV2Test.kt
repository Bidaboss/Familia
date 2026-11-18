package fr.familia.app.feature.members.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.KinLink
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
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

/** « Une fiche de membre organisée » (PRD rév. 290, récit 66). Fictional family only (TestFamily). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2000dp-xxhdpi")
class MemberProfileV2Test {

    @get:Rule
    val compose = createComposeRule()

    private val june = LocalDate.of(2026, 6, 10)
    private val family = TestFamily.family

    @Test
    fun `gifts come first near the birthday and in December before Christmas`() {
        val birth = LocalDate.of(1990, 7, 5)
        assertTrue(giftsFirst(birth, june))
        assertFalse(giftsFirst(birth, LocalDate.of(2026, 3, 1)))
        assertTrue(giftsFirst(null, LocalDate.of(2026, 12, 24)))
        assertFalse(giftsFirst(null, LocalDate.of(2026, 12, 25)))
        assertFalse(giftsFirst(LocalDate.of(1990, 7, 11), june))
    }

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

    private fun ready(member: Member, isMe: Boolean) = MemberProfileUiState.Ready(member, "Foyer Un", isMe, canEdit = isMe, souvenirs = emptyList())

    private fun v2(today: LocalDate = june, kin: Kinship = Kinship(emptyList(), emptyList(), emptyList()), address: String? = null) = ProfileV2State(
        today = today,
        household = family.households.first().copy(address = address),
        kinship = kin,
        moments = emptyList(),
        photos = emptyList(),
        managesHousehold = true,
    )

    private fun show(member: Member, isMe: Boolean, state: ProfileV2State = v2()) {
        compose.setContent {
            FamiliaTheme {
                Column {
                    ContentV2(
                        ready(member, isMe), ProfileV2Ui(state), onSetShown = { _, _ -> }, onOpenRecipes = null,
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

    private fun topOf(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun `on the page of someone else, an empty section does not show`() {
        show(TestFamily.otherAdult, isMe = false)
        assertEquals(0, count("Sa famille"))
        assertEquals(0, count("Ses moments"))
        assertEquals(0, count("Coordonnées"))
        assertEquals(1, count("Cadeaux"))
    }

    @Test
    fun `on my page, the empty sections invite to fill them`() {
        show(TestFamily.adult, isMe = true)
        assertEquals(1, count("Ma famille"))
        assertEquals(1, count("Mes moments"))
        assertEquals(1, count("Ajouter mon téléphone"))
        assertEquals(1, count("Ajouter l'adresse du foyer"))
    }

    @Test
    fun `never coordinates for a child, even on their page`() {
        show(TestFamily.kid, isMe = true)
        assertEquals(0, count("Coordonnées"))
        assertEquals(0, count("Ajouter mon téléphone"))
    }

    @Test
    fun `someone else's phone and address give Appeler and Itinéraire`() {
        show(TestFamily.adult.copy(phone = "06 12 34 56 78"), isMe = false, state = v2(address = "1 rue des Lilas"))
        // One round button under the name (P1), and one in « Coordonnées ».
        assertEquals(2, count("Appeler"))
        assertEquals(2, count("Itinéraire"))
        assertEquals(1, count("06 12 34 56 78"))
        assertEquals(1, count("Téléphone de Adult1"))
        assertEquals(1, count("1 rue des Lilas"))
    }

    @Test
    fun `gifts before tastes near the birthday, tastes first the rest of the year`() {
        val member = TestFamily.otherAdult.copy(birthDate = LocalDate.of(1990, 6, 22))
        show(member, isMe = false)
        assertTrue(topOf("Carte des cadeaux") < topOf("Carte des goûts"))
    }

    @Test
    fun `tastes first far from the birthday`() {
        val member = TestFamily.otherAdult.copy(birthDate = LocalDate.of(1990, 2, 3))
        show(member, isMe = false)
        assertTrue(topOf("Carte des goûts") < topOf("Carte des cadeaux"))
    }

    @Test
    fun `the links of the tree in « Sa famille »`() {
        val kin = Kinship(listOf(KinPerson("adult1", "Adult1", TestFamily.adult)), emptyList(), emptyList())
        show(TestFamily.kid, isMe = false, state = v2(kin = kin))
        assertEquals(1, count("Enfant de Adult1"))
        assertEquals(1, count("Sa famille"))
    }
}
