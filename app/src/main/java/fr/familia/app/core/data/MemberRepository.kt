package fr.familia.app.core.data

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import kotlinx.coroutines.flow.Flow

/** Access to the family member list. Screens never talk to Firestore directly. */
interface MemberRepository {
    /** The member whose Google address is [email], or null if this address is not in the family. */
    suspend fun findByEmail(email: String): Member?

    /**
     * The same, read from the phone's copy only, without waiting for the network: null when this
     * phone has never read it (or cannot). Used to open the app at once for an account kept from a
     * previous session; the server copy is read just after.
     */
    suspend fun findCachedByEmail(email: String): Member? = null

    /** Remembers which Firebase account belongs to [memberId], on the member's first sign-in. */
    suspend fun linkAccount(memberId: String, uid: String)

    /** Members and households, updated live (and served from the offline cache without network). */
    fun observeFamily(): Flow<Family>

    /** A fresh id for a member that is not saved yet. */
    fun newMemberId(): String

    /**
     * The admin principal, or a chef de famille for their household. Writes [member] and its email entry in one batch; [previousEmail] is the address
     * before the edit (null for a new member), whose entry is removed if it changed.
     */
    suspend fun saveMember(member: Member, previousEmail: String?)

    /**
     * The admin principal, or a chef de famille for their household. Removes [member], its email entry and its photo in one batch, so they lose access at once.
     * [updatedChildren] are the children who listed them as responsible adult, saved without them.
     */
    suspend fun removeMember(member: Member, updatedChildren: List<Member>)

    /** Coordonnées (récit 66): my own phone, or none; an adult only, nothing else changes. */
    suspend fun savePhone(memberId: String, phone: String?) {}

    /** A fresh id for a household that is not saved yet. */
    fun newHouseholdId(): String

    /** The admin principal creates or renames any household; a chef de famille renames their own. */
    suspend fun saveHousehold(household: Household)

    /** The admin principal only, for a household nobody belongs to any more. */
    suspend fun deleteHousehold(householdId: String)
}
