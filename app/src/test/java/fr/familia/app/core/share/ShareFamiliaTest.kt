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

    private fun member(id: String, profile: MemberProfile) = member(profile).copy(id = id)

    @Test
    fun `only the admin principal may share the app, not the other adults`() {
        assertTrue(canShareApp(member("noe", MemberProfile.Adult), "noe", LINK))
        assertFalse(canShareApp(member("ines", MemberProfile.Adult), "noe", LINK))
        assertFalse(canShareApp(member("lou", MemberProfile.ChildWithApp), "noe", LINK))
        assertFalse(canShareApp(member("noe", MemberProfile.Adult), null, LINK))
        assertFalse(canShareApp(null, "noe", LINK))
    }

    @Test
    fun `without an install link (a local build), nobody is offered the button`() {
        assertFalse(canShareApp(member("noe", MemberProfile.Adult), "noe", " "))
        assertFalse(hasInstallLink(""))
    }

    @Test
    fun `the message names the admin read from the family, or a fallback`() {
        assertEquals("Noé", ownerOr(" Noé ", "l'admin"))
        assertEquals("l'admin", ownerOr(null, "l'admin"))
        assertEquals("l'admin", ownerOr("  ", "l'admin"))
    }
}
