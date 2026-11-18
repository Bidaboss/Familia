package fr.familia.app.feature.members.profile

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import fr.familia.app.core.ui.checkAccessibility
import fr.familia.app.feature.events.TestFamily
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Police 200 % and TalkBack on « Nouvelle fiche de membre » (essais avant le 21, AVIS-22): at twice
 * the text size, everything one can touch says what it is and is big enough. Fictional family only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2f)
class MemberProfileV2AccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val june = LocalDate.of(2026, 6, 10)
    private val parent = TestFamily.adult.copy(phone = "06 12 34 56 78", birthDate = LocalDate.of(1985, 6, 20))
    private val kin = Kinship(
        listOf(KinPerson("admin1", "Admin1", TestFamily.admin)),
        listOf(KinPerson("adult2", "Adult2", TestFamily.otherAdult)),
        listOf(KinPerson("kid1", "Kid1", TestFamily.kid)),
    )

    private fun v2(kinship: Kinship = kin) = ProfileV2State(
        today = june,
        household = TestFamily.family.households.first().copy(address = "12 chemin des Grands Peupliers, 33000 Bordeaux"),
        kinship = kinship,
        moments = listOf(TestFamily.event("e1", TestFamily.admin, date = LocalDate.of(2026, 6, 28))),
        photos = emptyList(),
        managesHousehold = true,
    )

    private fun page(member: fr.familia.app.core.model.Member, isMe: Boolean, state: ProfileV2State = v2()) = compose.checkAccessibility {
        MemberProfileScreen(
            state = MemberProfileUiState.Ready(member, "Foyer Un", isMe, canEdit = isMe, souvenirs = emptyList()),
            onBack = {},
            onEdit = {},
            onSetShown = { _, _ -> },
            card = { Text("Goûts") },
            gifts = { Text("Cadeaux") },
            v2 = ProfileV2Ui(state, onSavePhoto = if (isMe) ({ _ -> }) else null),
        )
    }

    @Test
    fun `the page of someone else`() = page(parent, isMe = false)

    @Test
    fun `my own page, with its pencils and its gauge`() = page(parent, isMe = true)

    @Test
    fun `the page of a child, reached through the parents`() =
        page(TestFamily.kid, isMe = false, state = v2(Kinship(listOf(KinPerson("adult1", "Adult1", parent)), emptyList(), emptyList())))
}
