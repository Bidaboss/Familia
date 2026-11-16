package fr.familia.app.core.share

import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareFamiliaTest {

    private companion object {
        const val LINK = "https://example.org/familia"
    }

    private fun member(profile: MemberProfile) =
        Member("m1", "Camille", null, null, "h1", profile, isAdmin = false, parentIds = emptyList(), uid = null)

    @Test
    fun `every adult may share the app, children may not`() {
        assertTrue(canShareApp(member(MemberProfile.Adult), LINK))
        assertFalse(canShareApp(member(MemberProfile.ChildWithApp), LINK))
        assertFalse(canShareApp(member(MemberProfile.ChildWithoutApp), LINK))
        assertFalse(canShareApp(null, LINK))
    }

    @Test
    fun `without an install link (a local build), nobody is offered the button`() {
        assertFalse(canShareApp(member(MemberProfile.Adult), " "))
        assertFalse(hasInstallLink(""))
    }

    @Test
    fun `the message names the admin read from the family, or a fallback`() {
        assertEquals("Noé", ownerOr(" Noé ", "l'admin"))
        assertEquals("l'admin", ownerOr(null, "l'admin"))
        assertEquals("l'admin", ownerOr("  ", "l'admin"))
    }
}
