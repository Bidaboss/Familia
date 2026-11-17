package fr.familia.app.feature.genealogy

import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.TreePerson
import org.junit.Assert.assertEquals
import org.junit.Test

class PickLabelTest {

    private fun member(id: String, first: String, household: String) =
        TreePerson.OfMember(Member(id, first, "$id@example.com", null, household, MemberProfile.Adult, false, emptyList(), "uid-$id"))

    private val houses = mapOf("h1" to "Foyer Un", "h2" to "Foyer Deux")
    private fun label(p: TreePerson, among: List<TreePerson>) = pickLabel(p, among, houses, "En souvenir", "sans compte")

    @Test
    fun `a first name nobody else has stays short`() {
        val camille = member("m1", "Camille", "h1")
        assertEquals("Camille", label(camille, listOf(camille, member("m2", "Sam", "h2"))))
    }

    @Test
    fun `two people of the same first name are told apart`() {
        val member = member("m1", "Noé", "h1")
        val remembered = TreePerson.OfKin(Kin("k1", "Noé", living = false, householdId = "h1"))
        val withYears = TreePerson.OfKin(Kin("k2", "noé", living = false, born = "1921-03-04", died = "1999", householdId = "h1"))
        val withName = TreePerson.OfKin(Kin("k3", "Noé", lastName = "Martin", living = false, born = "1930", householdId = "h2"))
        val alive = TreePerson.OfKin(Kin("k4", "Noé", living = true, householdId = "h2"))
        val all = listOf(member, remembered, withYears, withName, alive)
        assertEquals("Noé · Foyer Un", label(member, all))
        assertEquals("Noé · En souvenir", label(remembered, all))
        assertEquals("noé · 1921–1999", label(withYears, all))
        assertEquals("Noé · Martin", label(withName, all))
        assertEquals("Noé · sans compte", label(alive, all))
    }
}
