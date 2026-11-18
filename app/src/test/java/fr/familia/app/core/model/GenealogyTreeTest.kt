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
    fun `two couples of grandparents with the same first names stay couples, each above their child`() {
        // Retour du 18/11: two Paul and two Anna, no « partner » link between them, were drawn
        // Anna, Anna, Paul, Paul.
        fun kin(id: String, first: String, last: String) = Kin(id, first, last, living = false, householdId = "h1")
        val tree = GenealogyTree(
            listOf(m("hugo", "Hugo"), m("elsa", "Elsa"), m("tim", "Tim")),
            listOf(kin("paulM", "Paul", "Martin"), kin("annaM", "Anna", "Martin"), kin("paulD", "Paul", "Durand"), kin("annaD", "Anna", "Durand")),
            listOf(
                KinLink.parent("paulM", "hugo"), KinLink.parent("annaM", "hugo"),
                KinLink.parent("paulD", "elsa"), KinLink.parent("annaD", "elsa"),
                KinLink.partners("hugo", "elsa"),
                KinLink.parent("hugo", "tim"), KinLink.parent("elsa", "tim"),
            ),
        )
        assertEquals(
            listOf(listOf("annaD", "paulD", "annaM", "paulM"), listOf("elsa", "hugo"), listOf("tim")),
            tree.rows,
        )
        assertEquals("Paul Martin", tree.drawnName("paulM"))
        assertEquals("Anna Durand", tree.drawnName("annaD"))
        assertEquals("Hugo", tree.drawnName("hugo"))
        assertEquals(listOf("annaM"), tree.coupleOf("paulM"))
    }

    @Test
    fun `in a shared custody, the other parent never joins the couple`() {
        // Alix lives with Sam; Noé is the child of Alix and Lou, who has no partner.
        val tree = GenealogyTree(
            listOf(m("alix", "Alix"), m("sam", "Sam"), m("noe", "Noé"), m("tom", "Tom")),
            listOf(Kin("lou", "Lou", living = true, householdId = "h1")),
            listOf(
                KinLink.partners("alix", "sam"),
                KinLink.parent("alix", "noe"), KinLink.parent("lou", "noe"),
                KinLink.parent("alix", "tom"), KinLink.parent("sam", "tom"),
            ),
        )
        assertEquals(listOf("sam"), tree.coupleOf("alix"))
        assertEquals(emptyList<String>(), tree.coupleOf("lou"))
        val life = TreeOfLife(tree)
        assertEquals(listOf("alix", "sam"), life.units.single { "alix" in it.ids }.ids.sorted())
        assertEquals(listOf("lou"), life.units.single { "lou" in it.ids }.ids)
    }

    @Test
    fun `the parents of one child without a partner link each send a line to the bar`() {
        val links = listOf(KinLink.parent("a", "c"), KinLink.parent("b", "c"))
        val boxes = mapOf(
            "a" to TreeBox(0f, 0f, 100f, 100f),
            "b" to TreeBox(120f, 0f, 220f, 100f),
            "c" to TreeBox(60f, 160f, 160f, 260f),
        )
        assertEquals(
            listOf(
                TreeSegment(50f, 100f, 50f, 130f),
                TreeSegment(170f, 100f, 170f, 130f),
                TreeSegment(50f, 130f, 170f, 130f),
                TreeSegment(110f, 130f, 110f, 160f),
            ),
            treeParentLines(links, boxes),
        )
    }

    @Test
    fun `a parent alone on their row keeps their name, even with a namesake elsewhere`() {
        val tree = GenealogyTree(listOf(m("x", "Paul")), listOf(Kin("y", "Paul", living = true, householdId = "h1")), emptyList())
        assertEquals("Paul", tree.drawnName("y"))
        assertEquals("Paul", tree.drawnName("x"))
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

    @Test
    fun `the children stand under the middle of their parents, never over their cousins`() {
        // Retour du 18/11: the two children of a couple stood left of them, under an aunt.
        val gran = m("gran", "Gaby")
        val gramps = m("gramps", "Gilles")
        val aunt = m("aunt", "Agathe")
        val uncle = m("uncle", "Hugo")
        val mum = m("mum", "Romane")
        val dad = m("dad", "Victor")
        val cousin = m("cousin", "Elio")
        val kids = listOf(m("kid1", "Mila"), m("kid2", "Tao"))
        val tree = GenealogyTree(
            listOf(gran, gramps, aunt, uncle, mum, dad, cousin) + kids,
            emptyList(),
            listOf(
                KinLink.partners("gran", "gramps"),
                KinLink.parent("gran", "aunt"),
                KinLink.parent("gramps", "aunt"),
                KinLink.parent("gran", "mum"),
                KinLink.parent("gramps", "mum"),
                KinLink.partners("aunt", "uncle"),
                KinLink.partners("mum", "dad"),
                KinLink.parent("aunt", "cousin"),
                KinLink.parent("uncle", "cousin"),
                KinLink.parent("mum", "kid1"),
                KinLink.parent("dad", "kid1"),
                KinLink.parent("mum", "kid2"),
                KinLink.parent("dad", "kid2"),
            ),
        )
        val at = tree.columns
        assertEquals((at.getValue("mum") + at.getValue("dad")) / 2, (at.getValue("kid1") + at.getValue("kid2")) / 2, 0.001)
        assertEquals((at.getValue("aunt") + at.getValue("uncle")) / 2, at.getValue("cousin"), 0.001)
        tree.rows.forEach { row ->
            val columns = row.map { at.getValue(it) }.sorted()
            columns.zipWithNext().forEach { (a, b) -> assertTrue("$row overlaps", b - a >= 1.0 - 1e-9) }
        }
        assertEquals(0.0, at.values.min(), 0.0)
    }
}
