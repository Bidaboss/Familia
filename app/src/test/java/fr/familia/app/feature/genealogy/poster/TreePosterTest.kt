package fr.familia.app.feature.genealogy.poster

import fr.familia.app.core.model.GenealogyTree
import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.KinLink
import fr.familia.app.core.model.Member
import fr.familia.app.feature.events.TestFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The tree poster of récit 53, with the fictional test family (see TestFamily). */
class TreePosterTest {

    private fun born(member: Member, year: Int) = member.copy(birthDate = LocalDate.of(year, 6, 14))

    // Grand-mère (a relative without an account) is the mother of admin1 and adult2; adult1 is
    // admin1's partner; kid1 is the child of admin1 and adult1, kid2 the child of adult2.
    private val granny = Kin("kin-granny", "Grand-mère", living = false, born = "1931-03-04", householdId = "h1")
    private val members = listOf(
        born(TestFamily.admin, 1960),
        born(TestFamily.adult, 1962),
        born(TestFamily.otherAdult, 1965),
        born(TestFamily.kid, 2012).copy(parentIds = listOf("adult1", "admin1")),
        born(TestFamily.otherKid, 2016),
    )
    private val tree = GenealogyTree(
        members,
        listOf(granny),
        listOf(
            KinLink.parent("kin-granny", "admin1"),
            KinLink.parent("kin-granny", "adult2"),
            KinLink.partners("admin1", "adult1"),
            KinLink.parent("admin1", "kid1"),
            KinLink.parent("adult1", "kid1"),
            KinLink.parent("adult2", "kid2"),
        ),
    )

    private fun content(start: PosterStart = PosterStart.Everyone, maker: String = "adult1", photos: Boolean = true) =
        posterContent(tree, start, maker, photos, { true }, "Notre arbre", 2026, PosterFormat.A4)

    @Test
    fun `the whole family, by generation, partners side by side and each family under its parents`() {
        val rows = content().rows.map { row -> row.map { it.id } }
        assertEquals(listOf("kin-granny"), rows[0])
        assertEquals(listOf("admin1", "adult1", "adult2"), rows[1])
        assertEquals(listOf("kid1", "kid2"), rows[2])
    }

    @Test
    fun `from a person, them, their descendants and the partners, and the generations start again`() {
        val rows = content(PosterStart.DescendantsOf("admin1")).rows.map { row -> row.map { it.id } }
        assertEquals(listOf(listOf("admin1", "adult1"), listOf("kid1")), rows)
        assertEquals(setOf("kin-granny", "admin1", "adult1", "adult2", "kid1", "kid2"), posterIds(tree, PosterStart.DescendantsOf("kin-granny")))
        assertEquals(emptySet<String>(), posterIds(tree, PosterStart.DescendantsOf("gone")))
    }

    @Test
    fun `only the year of birth, never the full date`() {
        val people = content().rows.flatten().associateBy { it.id }
        assertEquals(1931, people.getValue("kin-granny").year)
        assertEquals(2012, people.getValue("kid1").year)
    }

    @Test
    fun `a child's photo only for one of their adults, otherwise the initial`() {
        val byParent = content(maker = "adult1").rows.flatten().associateBy { it.id }
        assertEquals("kid1", byParent.getValue("kid1").photoOf)
        assertNull(byParent.getValue("kid2").photoOf)
        assertEquals("adult2", byParent.getValue("adult2").photoOf)
        assertEquals("K", byParent.getValue("kid2").initial)

        val byOther = content(maker = "adult2").rows.flatten().associateBy { it.id }
        assertNull(byOther.getValue("kid1").photoOf)
        assertEquals("kid2", byOther.getValue("kid2").photoOf)
    }

    @Test
    fun `without photos, or without a profile photo, the initial`() {
        assertEquals(listOf<String?>(null), content(photos = false).rows.flatten().map { it.photoOf }.distinct())
        val none = posterContent(tree, PosterStart.Everyone, "adult1", true, { false }, "T", 2026, PosterFormat.A3)
        assertEquals(listOf<String?>(null), none.rows.flatten().map { it.photoOf }.distinct())
    }

    @Test
    fun `a relative without an account shows the photo added in the tree (récit 79)`() {
        assertEquals("kin-granny", content().rows.flatten().first { it.id == "kin-granny" }.photoOf)
        assertNull(content(photos = false).rows.flatten().first { it.id == "kin-granny" }.photoOf)
        val others = posterContent(tree, PosterStart.Everyone, "adult1", true, { it != "kin-granny" }, "T", 2026, PosterFormat.A3)
        assertNull(others.rows.flatten().first { it.id == "kin-granny" }.photoOf)
    }

    @Test
    fun `the links kept are those between people on the poster`() {
        val links = content(PosterStart.DescendantsOf("adult2")).links
        assertEquals(listOf(KinLink.parent("adult2", "kid2")), links)
        assertEquals(listOf("adult2"), content().parentsOf("kid2"))
    }

    @Test
    fun `a file name every printing shop accepts`() {
        assertEquals("arbre-de-la-famille-2026.pdf", posterFileName(2026))
    }
}
