package fr.familia.app.core.ui

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import org.junit.Assert.assertEquals
import org.junit.Test

/** Members by household (règle de Kévin, 17/11). */
class PickGroupsTest {

    private fun m(id: String, name: String, home: String, profile: MemberProfile = MemberProfile.Adult) =
        Member(id, name, null, null, home, profile, isAdmin = false, parentIds = emptyList(), uid = null)

    private val camille = m("a", "Camille", "h2")
    private val noe = m("n", "Noé", "h2", MemberProfile.ChildWithApp)
    private val ines = m("i", "Inès", "h2")
    private val sam = m("s", "Sam", "h1")
    private val lou = m("l", "Lou", "gone")
    private val family = Family(
        households = listOf(Household("h1", "Les Bernard"), Household("h2", "Les Martin")),
        members = listOf(camille, noe, ines, sam, lou),
        ownerId = "a",
    )

    @Test
    fun `my household first, then the others by name, adults before children`() {
        val groups = pickGroups(family.members, family, firstHouseholdId = "h2", othersName = "Autres")
        assertEquals(listOf("Les Martin", "Les Bernard", "Autres"), groups.map { it.name })
        assertEquals(listOf("Camille", "Inès", "Noé"), groups[0].members.map { it.firstName })
        assertEquals(listOf("Lou"), groups[2].members.map { it.firstName })
    }

    @Test
    fun `a household with nobody to pick does not show`() {
        assertEquals(listOf("Les Bernard"), pickGroups(listOf(sam), family).map { it.name })
    }

    @Test
    fun `a flat list follows the same order`() {
        val sorted = family.members.sortedWith(householdOrder(family.households, "h1"))
        assertEquals(listOf("Sam", "Camille", "Inès", "Noé", "Lou"), sorted.map { it.firstName })
    }

    @Test
    fun `ticking a household picks all its members, ticking it again none`() {
        val ids = listOf("a", "n", "i")
        val all = toggleHousehold(setOf("a"), ids)
        assertEquals(setOf("a", "n", "i"), all)
        assertEquals(emptySet<String>(), toggleHousehold(all, ids))
    }
}
