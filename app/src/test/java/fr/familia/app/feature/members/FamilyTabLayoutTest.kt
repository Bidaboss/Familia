package fr.familia.app.feature.members

import fr.familia.app.core.model.Household
import fr.familia.app.core.model.HouseholdGroup
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Famille tab « par foyer » (récit 64): me, my household, then the others. Fictional family. */
class FamilyTabLayoutTest {

    private fun m(id: String, home: String, profile: MemberProfile = MemberProfile.Adult) =
        Member(id, id.replaceFirstChar { it.uppercase() }, null, null, home, profile, false, emptyList(), null)

    private val nord = Household("nord", "Foyer Nord")
    private val sud = Household("sud", "Foyer Sud")
    private val groups = listOf(
        HouseholdGroup(nord, listOf(m("ines", "nord"), m("noe", "nord", MemberProfile.ChildWithoutApp))),
        HouseholdGroup(sud, listOf(m("camille", "sud"), m("lou", "sud"))),
        HouseholdGroup(null, listOf(m("jeanne", ""))),
    )

    @Test
    fun `me first, then my household without me, then the other households in their order`() {
        val layout = familyTabLayout(groups, "lou")
        assertEquals("lou", layout.me?.id)
        assertEquals(listOf("camille"), layout.mine?.members?.map { it.id })
        assertEquals(listOf("nord", null), layout.others.map { it.household?.id })
        assertEquals(listOf("ines", "noe"), layout.others.first().members.map { it.id })
    }

    @Test
    fun `without a household, my card then every household, « Autres » last and without me`() {
        val layout = familyTabLayout(groups, "jeanne")
        assertEquals("jeanne", layout.me?.id)
        assertNull(layout.mine)
        // « Autres » held only me: it is left out.
        assertEquals(listOf("nord", "sud"), layout.others.map { it.household?.id })
    }

    @Test
    fun `someone not in the family sees every household, and no card of their own`() {
        val layout = familyTabLayout(groups, "personne")
        assertNull(layout.me)
        assertNull(layout.mine)
        assertEquals(3, layout.others.size)
    }
}
