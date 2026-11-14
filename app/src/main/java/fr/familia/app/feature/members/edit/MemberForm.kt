package fr.familia.app.feature.members.edit

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.normalizeEmail
import java.time.LocalDate

/** What the admin is typing in the member form. */
data class MemberForm(
    val firstName: String = "",
    val profile: MemberProfile = MemberProfile.Adult,
    val email: String = "",
    val householdId: String? = null,
    val birthDate: LocalDate? = null,
    val parentIds: Set<String> = emptySet(),
    val colorIndex: Int? = null,
    val isAdmin: Boolean = false,
) {
    val needsEmail: Boolean get() = profile != MemberProfile.ChildWithoutApp
    val needsParents: Boolean get() = profile != MemberProfile.Adult

    /** The member as it will be saved: fields that do not apply to the profile are dropped. */
    fun toMember(id: String, uid: String?): Member = Member(
        id = id,
        firstName = firstName.trim(),
        email = if (needsEmail) normalizeEmail(email) else null,
        birthDate = birthDate,
        householdId = householdId.orEmpty(),
        profile = profile,
        isAdmin = isAdmin && profile == MemberProfile.Adult,
        parentIds = if (needsParents) parentIds.sorted() else emptyList(),
        uid = uid,
        colorIndex = colorIndex,
    )

    companion object {
        fun from(member: Member) = MemberForm(
            firstName = member.firstName,
            profile = member.profile,
            email = member.email.orEmpty(),
            householdId = member.householdId,
            birthDate = member.birthDate,
            parentIds = member.parentIds.toSet(),
            colorIndex = member.colorIndex,
            isAdmin = member.isAdmin,
        )
    }
}

/** Why the form cannot be saved yet. Each one is shown next to its field. */
enum class FormError {
    FirstNameMissing,
    FirstNameTooLong,
    EmailInvalid,
    EmailTaken,
    HouseholdMissing,
    ParentsMissing,
    TooManyAdmins,
    BirthDateInFuture,
}

const val MAX_FIRST_NAME = 40
const val MAX_ADMINS = 2

/**
 * The same checks as the security rules, plus the ones only the app can make (unique address,
 * two admins at most, responsible adults that really are adults).
 */
fun validate(form: MemberForm, family: Family, editedId: String, today: LocalDate): Set<FormError> = buildSet {
    val name = form.firstName.trim()
    if (name.isEmpty()) add(FormError.FirstNameMissing)
    if (name.length > MAX_FIRST_NAME) add(FormError.FirstNameTooLong)

    if (form.needsEmail) {
        val email = normalizeEmail(form.email)
        if (!EMAIL.matches(email)) {
            add(FormError.EmailInvalid)
        } else if (family.members.any { it.id != editedId && it.email == email }) {
            add(FormError.EmailTaken)
        }
    }

    if (form.householdId == null || family.households.none { it.id == form.householdId }) {
        add(FormError.HouseholdMissing)
    }

    if (form.needsParents) {
        val adults = family.members.filter { it.profile == MemberProfile.Adult && it.id != editedId }.map { it.id }.toSet()
        if (form.parentIds.none { it in adults }) add(FormError.ParentsMissing)
    }

    if (form.isAdmin && form.profile == MemberProfile.Adult &&
        family.members.count { it.isAdmin && it.id != editedId } >= MAX_ADMINS
    ) {
        add(FormError.TooManyAdmins)
    }

    if (form.birthDate != null && form.birthDate.isAfter(today)) add(FormError.BirthDateInFuture)
}

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
