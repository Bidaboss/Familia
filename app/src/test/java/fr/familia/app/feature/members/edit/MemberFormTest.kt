package fr.familia.app.feature.members.edit

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MemberFormTest {

    private val today = LocalDate.of(2026, 11, 14)

    private fun m(id: String, profile: MemberProfile = MemberProfile.Adult, admin: Boolean = false, email: String? = "$id@example.com") =
        Member(id, id, email, null, "h1", profile, admin, emptyList(), null)

    private val family = Family(
        households = listOf(Household("h1", "Foyer")),
        members = listOf(m("admin1", admin = true), m("adult1"), m("kid1", MemberProfile.ChildWithApp)),
    )

    private val validAdult = MemberForm(firstName = "Sam", email = "sam@example.com", householdId = "h1")

    @Test
    fun `a complete adult is valid`() {
        assertEquals(emptySet<FormError>(), validate(validAdult, family, "new", today))
    }

    @Test
    fun `first name is required and limited to 40 characters`() {
        assertTrue(FormError.FirstNameMissing in validate(validAdult.copy(firstName = "  "), family, "new", today))
        assertTrue(FormError.FirstNameTooLong in validate(validAdult.copy(firstName = "a".repeat(41)), family, "new", today))
    }

    @Test
    fun `email must look like an address and belong to nobody else`() {
        assertTrue(FormError.EmailInvalid in validate(validAdult.copy(email = "sam"), family, "new", today))
        assertTrue(FormError.EmailTaken in validate(validAdult.copy(email = " Adult1@Example.com "), family, "new", today))
        // Keeping one's own address is fine.
        assertEquals(emptySet<FormError>(), validate(validAdult.copy(email = "adult1@example.com"), family, "adult1", today))
    }

    @Test
    fun `a child without the app needs no email but at least one responsible adult`() {
        val child = MemberForm(firstName = "Noé", profile = MemberProfile.ChildWithoutApp, householdId = "h1")
        assertEquals(setOf(FormError.ParentsMissing), validate(child, family, "new", today))
        assertEquals(emptySet<FormError>(), validate(child.copy(parentIds = setOf("adult1")), family, "new", today))
    }

    @Test
    fun `only adults count as responsible adults`() {
        val child = MemberForm(firstName = "Noé", profile = MemberProfile.ChildWithoutApp, householdId = "h1", parentIds = setOf("kid1"))
        assertTrue(FormError.ParentsMissing in validate(child, family, "new", today))
    }

    @Test
    fun `household must exist`() {
        assertTrue(FormError.HouseholdMissing in validate(validAdult.copy(householdId = null), family, "new", today))
        assertTrue(FormError.HouseholdMissing in validate(validAdult.copy(householdId = "gone"), family, "new", today))
    }

    @Test
    fun `the family has two admins at most`() {
        val twoAdmins = family.copy(members = family.members + m("admin2", admin = true))
        assertTrue(FormError.TooManyAdmins in validate(validAdult.copy(isAdmin = true), twoAdmins, "new", today))
        assertEquals(emptySet<FormError>(), validate(validAdult.copy(isAdmin = true), family, "new", today))
        // An admin editing themselves is not counted twice.
        assertEquals(emptySet<FormError>(), validate(validAdult.copy(isAdmin = true), twoAdmins, "admin2", today))
    }

    @Test
    fun `birth date cannot be in the future`() {
        assertTrue(FormError.BirthDateInFuture in validate(validAdult.copy(birthDate = today.plusDays(1)), family, "new", today))
        assertEquals(emptySet<FormError>(), validate(validAdult.copy(birthDate = today), family, "new", today))
    }

    @Test
    fun `saving drops what does not apply to the profile`() {
        val child = MemberForm(
            firstName = " Noé ",
            profile = MemberProfile.ChildWithoutApp,
            email = "left@example.com",
            householdId = "h1",
            parentIds = setOf("b", "a"),
            isAdmin = true,
        ).toMember("kid2", uid = null)
        assertEquals("Noé", child.firstName)
        assertNull(child.email)
        assertEquals(false, child.isAdmin)
        assertEquals(listOf("a", "b"), child.parentIds)

        val adult = validAdult.copy(email = " Sam@Example.com", parentIds = setOf("a")).toMember("x", uid = "u")
        assertEquals("sam@example.com", adult.email)
        assertEquals(emptyList<String>(), adult.parentIds)
        assertEquals("u", adult.uid)
    }
}
