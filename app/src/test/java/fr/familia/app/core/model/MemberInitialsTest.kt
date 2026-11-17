package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MemberInitialsTest {

    private fun member(id: String, firstName: String) =
        Member(id, firstName, null, null, "h1", MemberProfile.Adult, isAdmin = false, parentIds = emptyList(), uid = null)

    @Test
    fun `one letter while nobody shares it`() {
        assertEquals(mapOf("c" to "C", "n" to "N"), memberInitials(listOf(member("c", "Camille"), member("n", "Noé"))))
    }

    @Test
    fun `two letters when the initial is shared`() {
        val initials = memberInitials(listOf(member("lea", "Léo"), member("lou", "Lou"), member("s", "Sam")))
        assertEquals("Lé", initials["lea"])
        assertEquals("Lo", initials["lou"])
        assertEquals("S", initials["s"])
    }

    @Test
    fun `a compound first name takes both initials, and the simple one beside it keeps its letter`() {
        val initials = memberInitials(listOf(member("as", "Anne-Sophie"), member("a", "Arthur")))
        assertEquals("AS", initials["as"])
        assertEquals("A", initials["a"])
    }

    @Test
    fun `accents do not hide a shared initial`() {
        val initials = memberInitials(listOf(member("e", "Élise"), member("f", "Emma")))
        assertEquals("Él", initials["e"])
        assertEquals("Em", initials["f"])
    }
}
