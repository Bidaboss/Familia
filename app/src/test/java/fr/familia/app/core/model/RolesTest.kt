package fr.familia.app.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who may change a profile photo (PRD, récit 3 bis). Fictional members only. */
class RolesTest {

    private fun m(id: String, household: String, profile: MemberProfile = MemberProfile.Adult, chef: Boolean = false) =
        Member(id, id, null, null, household, profile, chef, emptyList(), null)

    private val owner = m("owner", "h2")
    private val adult = m("adult", "h1")
    private val otherAdult = m("other", "h1")
    private val chef = m("chef", "h1", chef = true)
    private val child = m("child", "h1", MemberProfile.ChildWithApp)
    private val small = m("small", "h1", MemberProfile.ChildWithoutApp)
    private val neighbourChild = m("neighbour", "h2", MemberProfile.ChildWithoutApp)
    private val family = Family(emptyList(), listOf(owner, adult, otherAdult, chef, child, small, neighbourChild), ownerId = "owner")

    @Test
    fun `everyone changes their own photo, a child with the app too`() {
        assertTrue(family.canChangePhoto(adult, adult))
        assertTrue(family.canChangePhoto(child, child))
    }

    @Test
    fun `any adult of the household changes a child's photo, with or without the app`() {
        assertTrue(family.canChangePhoto(adult, child))
        assertTrue(family.canChangePhoto(adult, small))
        assertFalse(family.canChangePhoto(adult, neighbourChild))
    }

    @Test
    fun `an adult never changes another adult's photo, even as chef`() {
        assertFalse(family.canChangePhoto(adult, otherAdult))
        assertFalse(family.canChangePhoto(chef, adult))
    }

    @Test
    fun `a child never changes another member's photo`() {
        assertFalse(family.canChangePhoto(child, small))
        assertFalse(family.canChangePhoto(child, adult))
    }

    @Test
    fun `the admin principal changes anyone`() {
        assertTrue(family.canChangePhoto(owner, adult))
        assertTrue(family.canChangePhoto(owner, child))
    }
}
