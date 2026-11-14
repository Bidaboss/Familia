package fr.familia.app.core.data

import fr.familia.app.core.model.Family
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
}
