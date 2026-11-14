package fr.familia.app.core.data

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import kotlinx.coroutines.flow.Flow

/** Access to the family member list. Screens never talk to Firestore directly. */
interface MemberRepository {
    /** The member whose Google address is [email], or null if this address is not in the family. */
    suspend fun findByEmail(email: String): Member?

    /** Remembers which Firebase account belongs to [memberId], on the member's first sign-in. */
    suspend fun linkAccount(memberId: String, uid: String)

    /** Members and households, updated live (and served from the offline cache without network). */
    fun observeFamily(): Flow<Family>

    /** A fresh id for a member that is not saved yet. */
    fun newMemberId(): String

    /**
     * Admins only. Writes [member] and its email entry in one batch; [previousEmail] is the address
     * before the edit (null for a new member), whose entry is removed if it changed.
     */
    suspend fun saveMember(member: Member, previousEmail: String?)

    /**
     * Admins only. Removes [member] and its email entry in one batch, so they lose access at once.
     * [updatedChildren] are the children who listed them as responsible adult, saved without them.
     */
    suspend fun removeMember(member: Member, updatedChildren: List<Member>)

    /** A fresh id for a household that is not saved yet. */
    fun newHouseholdId(): String

    /** Admins only. Creates or renames a household. */
    suspend fun saveHousehold(household: Household)
}
