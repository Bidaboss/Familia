package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.normalizeEmail
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
    )

    private companion object {
        const val MEMBERS = "members"
        const val MEMBER_EMAILS = "memberEmails"
    }
}
