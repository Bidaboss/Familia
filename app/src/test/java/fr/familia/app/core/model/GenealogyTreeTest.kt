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
    fun `the children of two sisters stay together, each under their own parent`() {
        // Alphabetical order alone would mix them: Ada (Rose), Bob (Iris), Cid (Rose), Dan (Iris).
        val mum = m("mum", "Maud")
        val iris = m("iris", "Iris")
        val rose = m("rose", "Rose")
        val kids = listOf(m("ada", "Ada"), m("bob", "Bob"), m("cid", "Cid"), m("dan", "Dan"))
        val tree = GenealogyTree(
            listOf(mum, iris, rose) + kids,
            emptyList(),
            listOf(
                KinLink.parent("mum", "iris"),
                KinLink.parent("mum", "rose"),
                KinLink.parent("rose", "ada"),
                KinLink.parent("iris", "bob"),
                KinLink.parent("rose", "cid"),
                KinLink.parent("iris", "dan"),
            ),
        )
        assertEquals(listOf(listOf("mum"), listOf("iris", "rose"), listOf("bob", "dan", "ada", "cid")), tree.rows)
    }

    @Test
    fun `a couple sends one line down from between them, and each family has its own bar`() {
        val links = listOf(
            KinLink.partners("camille", "ines"),
            KinLink.parent("camille", "noe"),
            KinLink.parent("ines", "noe"),
            KinLink.parent("camille", "lou"),
            KinLink.parent("ines", "lou"),
            KinLink.parent("jeanne", "zoe"),
        )
        val boxes = mapOf(
            "camille" to TreeBox(0f, 0f, 100f, 100f),
            "ines" to TreeBox(120f, 0f, 220f, 100f),
            "jeanne" to TreeBox(240f, 0f, 340f, 100f),
            "lou" to TreeBox(0f, 160f, 100f, 260f),
            "noe" to TreeBox(120f, 160f, 220f, 260f),
            "zoe" to TreeBox(240f, 160f, 340f, 260f),
        )
        assertEquals(listOf(listOf("camille", "ines"), listOf("jeanne")), treeFamilies(links).map { it.parents })
        val lines = treeParentLines(links, boxes)
        // The couple: one drop from the middle of the line between them, at mid height.
        assertEquals(TreeSegment(110f, 50f, 110f, 120f), lines[0])
        assertEquals(TreeSegment(50f, 120f, 170f, 120f), lines[1])
        // Jeanne alone: from under her card, on a lower bar of its own.
        assertEquals(TreeSegment(290f, 100f, 290f, 140f), lines[4])
        assertEquals(4 + 3, lines.size)
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

    @Test
    fun `an old date is typed as a year or a French day, and stored as AAAA or AAAA-MM-JJ`() {
        assertEquals("1921", Kin.parseDate(" 1921 "))
        assertEquals("1921-03-04", Kin.parseDate("4/3/1921"))
        assertEquals("1921-03-04", Kin.parseDate("1921-03-04"))
        assertEquals("", Kin.parseDate("  "))
        assertEquals(null, Kin.parseDate("31/02/1921"))
        assertEquals(null, Kin.parseDate("mars 1921"))
    }
}
