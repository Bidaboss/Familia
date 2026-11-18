package fr.familia.app.core.model

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeOfLifeTest {

    private fun member(id: String, first: String, household: String = "h1") =
        Member(id, first, "$id@example.com", null, household, MemberProfile.Adult, false, emptyList(), "uid-$id")

    /** The example family: two grandparents, three children with their partners, five grandchildren, two roots. */
    private fun example(): GenealogyTree {
        val members = listOf(
            member("gp1", "Camille"), member("gp2", "Inès"),
            member("c1", "Noé", "h2"), member("p1", "Sam", "h2"),
            member("c2", "Lou", "h3"), member("p2", "Alix", "h3"),
            member("c3", "Jade", "h4"),
            member("g1", "Tom", "h2"), member("g2", "Zoé", "h2"),
            member("g3", "Léon", "h3"), member("g4", "Mila", "h3"), member("g5", "Rémi", "h4"),
        )
        val kin = listOf(
            Kin("k1", "Adèle", living = false, born = "1921", died = "1999", householdId = "h1"),
            Kin("k2", "Gaston", living = false, born = "1918", householdId = "h1"),
        )
        val links = listOf(
            KinLink.parent("k1", "gp1"), KinLink.parent("k2", "gp1"), KinLink.partners("k1", "k2"),
            KinLink.partners("gp1", "gp2"),
            KinLink.parent("gp1", "c1"), KinLink.parent("gp2", "c1"),
            KinLink.parent("gp1", "c2"), KinLink.parent("gp2", "c2"),
            KinLink.parent("gp1", "c3"), KinLink.parent("gp2", "c3"),
            KinLink.partners("c1", "p1"), KinLink.partners("c2", "p2"),
            KinLink.parent("c1", "g1"), KinLink.parent("p1", "g1"), KinLink.parent("c1", "g2"),
            KinLink.parent("c2", "g3"), KinLink.parent("c2", "g4"), KinLink.parent("c3", "g5"),
        )
        return GenealogyTree(members, kin, links)
    }

    /** No two medallions closer than 4 dp, and no two touch squares of 56 dp closer than 8 dp. */
    private fun assertApart(life: TreeOfLife) {
        val points = life.centers.entries.toList()
        for (i in points.indices) for (j in i + 1 until points.size) {
            val (a, pa) = points[i]
            val (b, pb) = points[j]
            val gap = hypot(pa.first - pb.first, pa.second - pb.second) - life.radiusOf(a) - life.radiusOf(b)
            assertTrue("$a and $b are $gap dp apart", gap >= 4f)
            val apart = maxOf(abs(pa.first - pb.first), abs(pa.second - pb.second)) - TreeOfLife.TOUCH
            assertTrue("the touch squares of $a and $b are $apart dp apart", apart >= TreeOfLife.SPACING)
        }
    }

    @Test
    fun `the grandparents on the trunk, the roots below, the others upward`() {
        val life = TreeOfLife(example())
        val trunk = life.units.single { it.level == 0 }
        assertEquals(listOf("gp1", "gp2"), trunk.ids.sorted())
        assertEquals(listOf("k2", "k1"), life.roots.map { it.id }) // by year of birth
        assertTrue(life.roots.all { it.y > 0 })
        val g1 = life.centers.getValue("g1")
        val c1 = life.centers.getValue("c1")
        assertTrue("a grandchild stands above their parent", g1.second < c1.second)
        assertEquals(12 + 2, life.centers.size)
        assertApart(life)
    }

    @Test
    fun `each household stands above its parents, branches thinner as they climb`() {
        val life = TreeOfLife(example())
        val c1 = life.units.first { "c1" in it.ids }
        val kids = life.units.filter { it.level == 2 && it.ids.any { id -> id in setOf("g1", "g2") } }
        assertTrue(kids.minOf { it.x } <= c1.x && c1.x <= kids.maxOf { it.x })
        val widths = life.branches.groupBy { it.toY }.mapValues { (_, b) -> b.first().width }
        assertEquals(16f, widths.getValue(-TreeOfLife.LEVEL_GAP))
        assertEquals(13f, widths.getValue(-2 * TreeOfLife.LEVEL_GAP))
    }

    @Test
    fun `a family without relatives has no roots`() {
        val members = listOf(member("a", "Camille"), member("b", "Inès"), member("c", "Noé"))
        val links = listOf(KinLink.partners("a", "b"), KinLink.parent("a", "c"), KinLink.parent("b", "c"))
        val life = TreeOfLife(GenealogyTree(members, emptyList(), links))
        assertTrue(life.roots.isEmpty())
        assertApart(life)
    }

    @Test
    fun `four generations of members, and people with no link, keep apart`() {
        val members = (1..4).map { member("m$it", "Gen$it") } + member("x", "Seul", "h9") + member("y", "Autre", "h9")
        val links = (1..3).map { KinLink.parent("m$it", "m${it + 1}") }
        val life = TreeOfLife(GenealogyTree(members, emptyList(), links))
        assertEquals(4, life.units.maxOf { it.level } + 1)
        assertApart(life)
        assertTrue(life.sprinkles(42, 10) == life.sprinkles(42, 10))
    }

    /** A couple of the app whose son-in-law added his parents as relatives (PRODUIT-108). */
    private fun inLaws(father: String, mother: String): GenealogyTree {
        val members = listOf(member("gp1", "Camille"), member("gp2", "Inès"), member("c1", "Lou", "h2"), member("p1", "Alix", "h2"), member("g1", "Tom", "h2"))
        val kin = listOf(Kin("k1", father, living = true, householdId = "h2"), Kin("k2", mother, living = true, householdId = "h2"))
        val links = listOf(
            KinLink.partners("gp1", "gp2"), KinLink.parent("gp1", "c1"), KinLink.parent("gp2", "c1"),
            KinLink.partners("k1", "k2"), KinLink.parent("k1", "p1"), KinLink.parent("k2", "p1"),
            KinLink.partners("c1", "p1"), KinLink.parent("c1", "g1"), KinLink.parent("p1", "g1"),
        )
        return GenealogyTree(members, kin, links)
    }

    @Test
    fun `a couple hangs from the parents of the app, the parents of a son-in-law get a thin branch`() {
        // Their first names before and after those of the app: the order of the row never decides.
        for ((father, mother) in listOf("Aimé" to "Agathe", "Yves" to "Zélie")) {
            val life = TreeOfLife(inLaws(father, mother))
            val trunk = life.trunk!!
            assertEquals(listOf("gp1", "gp2"), trunk.ids.sorted())
            assertEquals(0f, trunk.x)
            val couple = life.units.single { "c1" in it.ids }
            val inLaws = life.units.single { "k1" in it.ids }
            assertEquals(0, inLaws.level)
            val main = life.branches.single { it.toX == couple.x && it.toY == couple.y && it.width == 16f }
            assertEquals(trunk.x, main.fromX)
            val thin = life.branches.single { it.toX == couple.x && it.toY == couple.y && it.width == 4f }
            assertEquals(inLaws.x, thin.fromX)
            assertEquals(TreeOfLife.RADIUS, life.radiusOf("k1"))
            assertEquals(TreeOfLife.TRUNK_RADIUS, life.radiusOf("gp1"))
            assertApart(life)
        }
    }

    @Test
    fun `a big name widens its place, never the medallions into each other`() {
        val life = TreeOfLife(example()) { text -> text.length * 14f } // police à 200 %
        assertApart(life)
    }
}
