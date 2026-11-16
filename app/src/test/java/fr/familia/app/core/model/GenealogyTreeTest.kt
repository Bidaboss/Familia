package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The family tree (PRD, récit 40), drawn from the members, the kin and their links. Fictional family. */
class GenealogyTreeTest {

    private fun m(id: String, name: String) = Member(id, name, null, null, "h1", MemberProfile.Adult, false, emptyList(), null)

    private val camille = m("camille", "Camille")
    private val ines = m("ines", "Inès")
    private val noe = m("noe", "Noé")
    private val lou = m("lou", "Lou")
    private val jeanne = Kin("jeanne", "Jeanne", living = false, born = "1921", died = "1999", householdId = "h1")

    @Test
    fun `generations go from the eldest down, partners side by side`() {
        val tree = GenealogyTree(
            listOf(camille, ines, noe, lou),
            listOf(jeanne),
            listOf(
                KinLink.parent("jeanne", "camille"),
                KinLink.partners("ines", "camille"),
                KinLink.parent("camille", "noe"),
                KinLink.parent("ines", "noe"),
                KinLink.parent("camille", "lou"),
            ),
        )
        assertEquals(mapOf("jeanne" to 0, "camille" to 1, "ines" to 1, "noe" to 2, "lou" to 2), tree.generations)
        assertEquals(listOf(listOf("jeanne"), listOf("camille", "ines"), listOf("lou", "noe")), tree.rows)
        assertEquals(listOf("camille", "ines"), tree.parentsOf("noe").sorted())
        assertEquals(listOf("lou", "noe"), tree.childrenOf("camille").sorted())
        assertEquals(listOf("ines"), tree.partnersOf("camille"))
    }

    @Test
    fun `a link to someone no longer there is left out`() {
        val tree = GenealogyTree(listOf(camille), emptyList(), listOf(KinLink.parent("gone", "camille")))
        assertTrue(tree.links.isEmpty())
        assertEquals(mapOf("camille" to 0), tree.generations)
    }

    @Test
    fun `a loop of parents, a mistake, never hangs the tree`() {
        val tree = GenealogyTree(listOf(camille, ines), emptyList(), listOf(KinLink.parent("camille", "ines"), KinLink.parent("ines", "camille")))
        assertTrue(tree.generations.values.all { it <= 2 })
        assertEquals(2, tree.rows.flatten().size)
    }

    @Test
    fun `partners are named in alphabetical order, so a link is never there twice`() {
        assertEquals(KinLink.partners("ines", "camille"), KinLink.partners("camille", "ines"))
        assertEquals("partner_camille_ines", KinLink.partners("ines", "camille").id)
        assertEquals("parent_jeanne_camille", KinLink.parent("jeanne", "camille").id)
    }

    @Test
    fun `an old date is a year or a day`() {
        assertTrue(Kin.isDate("1921"))
        assertTrue(Kin.isDate("1999-03-04"))
        assertTrue(!Kin.isDate("04/03/1921"))
    }
}
