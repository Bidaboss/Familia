package fr.familia.app.feature.members

import fr.familia.app.core.model.Household
import fr.familia.app.core.model.HouseholdGroup
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The pure rules of Famille > Foyers (brief Famille-recettes F1). Fictional names. */
class FamilyHouseholdsTest {

    private val today = LocalDate.of(2026, 11, 25)

    private fun m(id: String, home: String = "h1", profile: MemberProfile = MemberProfile.Adult, born: LocalDate? = null, phone: String? = null) =
        Member(id, id, null, born, home, profile, false, emptyList(), null, phone = phone)

    private val groups = listOf(
        HouseholdGroup(Household("h1", "Foyer Bernard"), listOf(m("ines"), m("lou", profile = MemberProfile.ChildWithApp))),
        HouseholdGroup(Household("h2", "Foyer Martin"), listOf(m("camille", "h2"), m("noe", "h2"))),
        HouseholdGroup(null, listOf(m("jules", ""))),
    )

    @Test
    fun `my household comes first, then the others in their order, Autres last`() {
        val sections = householdSections(groups, "camille")
        assertEquals(listOf("h2", "h1", null), sections.map { it.group.household?.id })
        assertEquals(listOf(true, false, false), sections.map { it.mine })
        // Every household stays whole: nobody is taken out, me included.
        assertEquals(listOf("camille", "noe"), sections.first().group.members.map { it.id })
    }

    @Test
    fun `without a household of mine, nothing is called Mon foyer`() {
        val sections = householdSections(groups, "jules")
        assertEquals(listOf("h1", "h2", null), sections.map { it.group.household?.id })
        assertTrue(sections.none { it.mine })
    }

    @Test
    fun `a birthday shows within two weeks, today included, never after`() {
        assertEquals(0, birthdaySoonIn(m("a", born = LocalDate.of(1990, 11, 25)), today))
        assertEquals(5, birthdaySoonIn(m("a", born = LocalDate.of(1990, 11, 30)), today))
        assertEquals(14, birthdaySoonIn(m("a", born = LocalDate.of(1990, 12, 9)), today))
        assertNull(birthdaySoonIn(m("a", born = LocalDate.of(1990, 12, 10)), today))
        assertNull(birthdaySoonIn(m("a", born = LocalDate.of(1990, 11, 24)), today))
        assertNull(birthdaySoonIn(m("a"), today))
    }

    @Test
    fun `the age of a child`() {
        assertEquals(11, ageOf(m("a", born = LocalDate.of(2015, 7, 19)), today))
        assertEquals(0, ageOf(m("a", born = LocalDate.of(2026, 2, 1)), today))
        assertNull(ageOf(m("a"), today))
    }

    @Test
    fun `Appeler and Message, on an adult with a phone, never on oneself nor a child`() {
        assertTrue(reachable(m("a", phone = "06 00 00 00 01"), "me"))
        assertFalse(reachable(m("me", phone = "0600000001"), "me"))
        assertFalse(reachable(m("a"), "me"))
        assertFalse(reachable(m("a", phone = " "), "me"))
        assertFalse(reachable(m("a", profile = MemberProfile.ChildWithApp, phone = "0600000001"), "me"))
    }

    @Test
    fun `Autres counts its people but not as a household`() {
        assertEquals(5 to 2, familyCounts(groups))
    }
}
