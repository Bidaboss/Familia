package fr.familia.app.core.model

import java.time.LocalDate

/** What a member can do in the app (PRD, "Profils"). */
enum class MemberProfile(val firestoreValue: String) {
    Adult("adult"),
    ChildWithApp("childWithApp"),
    ChildWithoutApp("childWithoutApp"),
    ;

    companion object {
        fun fromFirestore(value: String?): MemberProfile =
            entries.firstOrNull { it.firestoreValue == value } ?: ChildWithoutApp
    }
}

/**
 * One of the 13 family members. A child lists the adults responsible for them in [parentIds]
 * (no limit to two: a step-parent has the same rights as a parent).
 */
data class Member(
    val id: String,
    val firstName: String,
    val email: String?,
    val birthDate: LocalDate?,
    val householdId: String,
    val profile: MemberProfile,
    /** Chef de famille: manages the members and events of their own household (PRD, carte DEV-8). */
    val isAdmin: Boolean,
    val parentIds: List<String>,
    val uid: String?,
    /** Index in the 13 member colors, chosen by an admin. Null until one is chosen. */
    val colorIndex: Int? = null,
    /** Coordonnées (récit 66): optional, for an adult only, set by the member themselves. */
    val phone: String? = null,
)

/** Firestore stores emails in lower case so a member is recognised whatever the casing typed. */
fun normalizeEmail(email: String): String = email.trim().lowercase()

/** A household of the family (Parents, a sister's family…). */
data class Household(
    val id: String,
    val name: String,
    /** Coordonnées (récit 66): optional, set by who manages the household. */
    val address: String? = null,
)

/**
 * Everything the Famille tab shows. [ownerId] is the admin principal (config/family), who has every
 * right; null while it cannot be read.
 */
data class Family(val households: List<Household>, val members: List<Member>, val ownerId: String? = null)
