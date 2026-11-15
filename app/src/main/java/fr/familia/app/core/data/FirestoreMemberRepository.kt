package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.normalizeEmail
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Members live in `members/{memberId}`. The index `memberEmails/{email}` maps a Google address to
 * its member, so security rules can recognise an invited member before they have a Firebase UID.
 * See docs/data-model.md.
 */
class FirestoreMemberRepository(private val db: FirebaseFirestore) : MemberRepository {

    override suspend fun findByEmail(email: String): Member? {
        val index = db.collection(MEMBER_EMAILS).document(normalizeEmail(email)).get().await()
        val memberId = index.getString("memberId") ?: return null
        val doc = db.collection(MEMBERS).document(memberId).get().await()
        return if (doc.exists()) doc.toMember() else null
    }

    override suspend fun linkAccount(memberId: String, uid: String) {
        db.collection(MEMBERS).document(memberId).update("uid", uid).await()
    }

    override fun observeFamily(): Flow<Family> =
        combine(
            snapshots(HOUSEHOLDS) { Household(id = it.id, name = it.getString("name").orEmpty()) },
            snapshots(MEMBERS) { it.toMember() },
            ownerId(),
        ) { households, members, ownerId -> Family(households, members, ownerId) }

    /**
     * config/family names the admin principal; only the GitHub workflow writes it. If it cannot be
     * read (rules not published yet), nobody is admin principal and the family list still shows.
     */
    private fun ownerId(): Flow<String?> = callbackFlow {
        val registration = db.collection(CONFIG).document(FAMILY).addSnapshotListener { snapshot, error ->
            trySend(if (error != null) null else snapshot?.getString("ownerMemberId"))
        }
        awaitClose { registration.remove() }
    }

    override fun newMemberId(): String = db.collection(MEMBERS).document().id

    override suspend fun saveMember(member: Member, previousEmail: String?) {
        val batch = db.batch()
        batch.set(db.collection(MEMBERS).document(member.id), member.toFirestore())
        // The rules refuse a member whose email entry is missing, or an old entry left behind.
        if (previousEmail != null && previousEmail != member.email) {
            batch.delete(db.collection(MEMBER_EMAILS).document(previousEmail))
        }
        if (member.email != null && member.email != previousEmail) {
            batch.set(db.collection(MEMBER_EMAILS).document(member.email), mapOf("memberId" to member.id))
        }
        batch.commit().await()
    }

    override suspend fun removeMember(member: Member, updatedChildren: List<Member>) {
        val batch = db.batch()
        batch.delete(db.collection(MEMBERS).document(member.id))
        member.email?.let { batch.delete(db.collection(MEMBER_EMAILS).document(it)) }
        updatedChildren.forEach { batch.set(db.collection(MEMBERS).document(it.id), it.toFirestore()) }
        batch.commit().await()
    }

    override fun newHouseholdId(): String = db.collection(HOUSEHOLDS).document().id

    override suspend fun saveHousehold(household: Household) {
        db.collection(HOUSEHOLDS).document(household.id).set(mapOf("name" to household.name)).await()
    }

    override suspend fun deleteHousehold(householdId: String) {
        db.collection(HOUSEHOLDS).document(householdId).delete().await()
    }

    /** Live list of a whole collection; the listener is removed when the screen stops collecting. */
    private fun <T> snapshots(collection: String, map: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
        val registration = db.collection(collection).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot.documents.map(map))
            }
        }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toMember() = Member(
        id = id,
        firstName = getString("firstName").orEmpty(),
        email = getString("email"),
        birthDate = getString("birthDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        householdId = getString("householdId").orEmpty(),
        profile = MemberProfile.fromFirestore(getString("profile")),
        isAdmin = getBoolean("isAdmin") == true,
        parentIds = (get("parentIds") as? List<*>).orEmpty().filterIsInstance<String>(),
        uid = getString("uid"),
        colorIndex = getLong("colorIndex")?.toInt(),
    )

    private fun Member.toFirestore(): Map<String, Any?> = buildMap {
        put("firstName", firstName)
        put("email", email)
        put("birthDate", birthDate?.toString())
        put("householdId", householdId)
        put("profile", profile.firestoreValue)
        put("isAdmin", isAdmin)
        put("parentIds", parentIds)
        put("uid", uid)
        // Optional in the rules: absent rather than null when no color was chosen.
        colorIndex?.let { put("colorIndex", it) }
    }

    private companion object {
        const val MEMBERS = "members"
        const val MEMBER_EMAILS = "memberEmails"
        const val HOUSEHOLDS = "households"
        const val CONFIG = "config"
        const val FAMILY = "family"
    }
}
