package fr.familia.app.feature.auth

import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.normalizeEmail
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val member = Member(
        id = "m1",
        firstName = "Alex",
        email = "alex@example.com",
        birthDate = null,
        householdId = "h1",
        profile = MemberProfile.Adult,
        isAdmin = false,
        parentIds = emptyList(),
        uid = null,
    )

    @Test
    fun `without Firebase the screen says the app is not configured`() {
        val vm = SessionViewModel(auth = null, members = null)
        assertEquals(SessionUiState.FirebaseNotConfigured, vm.uiState.value)
    }

    @Test
    fun `first launch shows the sign-in button`() {
        val vm = SessionViewModel(FakeAuth(current = null), FakeMembers(member))
        assertEquals(SessionUiState.SignedOut(), vm.uiState.value)
    }

    @Test
    fun `a member stays signed in from the previous session`() {
        val vm = SessionViewModel(FakeAuth(current = user("alex@example.com")), FakeMembers(member))
        assertTrue(vm.uiState.value is SessionUiState.SignedIn)
    }

    @Test
    fun `a Google account outside the family is refused and signed out`() {
        val auth = FakeAuth(current = null, next = user("stranger@example.com"))
        val vm = SessionViewModel(auth, FakeMembers(member))
        vm.onGoogleTokenResult(GoogleTokenResult.Success("token"))
        assertEquals(SessionUiState.NotAMember("stranger@example.com"), vm.uiState.value)
        assertNull(auth.currentUser())
    }

    @Test
    fun `an unverified address is refused`() {
        val auth = FakeAuth(current = null, next = user("alex@example.com", verified = false))
        val vm = SessionViewModel(auth, FakeMembers(member))
        vm.onGoogleTokenResult(GoogleTokenResult.Success("token"))
        assertTrue(vm.uiState.value is SessionUiState.NotAMember)
    }

    @Test
    fun `the address is matched whatever its casing`() {
        val auth = FakeAuth(current = null, next = user("Alex@Example.com"))
        val vm = SessionViewModel(auth, FakeMembers(member))
        vm.onGoogleTokenResult(GoogleTokenResult.Success("token"))
        assertTrue(vm.uiState.value is SessionUiState.SignedIn)
    }

    @Test
    fun `first sign-in links the account to the member profile`() {
        val members = FakeMembers(member)
        val vm = SessionViewModel(FakeAuth(current = null, next = user("alex@example.com")), members)
        vm.onGoogleTokenResult(GoogleTokenResult.Success("token"))
        assertEquals("m1" to "uid-1", members.linked)
        assertEquals("uid-1", (vm.uiState.value as SessionUiState.SignedIn).member.uid)
    }

    @Test
    fun `an already linked member is not linked again`() {
        val members = FakeMembers(member.copy(uid = "uid-1"))
        SessionViewModel(FakeAuth(current = user("alex@example.com")), members)
        assertNull(members.linked)
    }

    @Test
    fun `closing the Google picker goes back to the sign-in button without error`() {
        val vm = SessionViewModel(FakeAuth(current = null), FakeMembers(member))
        vm.onSignInStarted()
        assertEquals(SessionUiState.SigningIn, vm.uiState.value)
        vm.onGoogleTokenResult(GoogleTokenResult.Cancelled)
        assertEquals(SessionUiState.SignedOut(), vm.uiState.value)
    }

    @Test
    fun `no Google account on the phone gets its own message`() {
        val vm = SessionViewModel(FakeAuth(current = null), FakeMembers(member))
        vm.onGoogleTokenResult(GoogleTokenResult.NoAccount)
        assertEquals(SessionUiState.SignedOut(SignInError.NoGoogleAccount), vm.uiState.value)
    }

    @Test
    fun `a network failure is reported as such`() {
        val auth = FakeAuth(current = null, failWith = IOException("offline"))
        val vm = SessionViewModel(auth, FakeMembers(member))
        vm.onGoogleTokenResult(GoogleTokenResult.Success("token"))
        assertEquals(SessionUiState.SignedOut(SignInError.Network), vm.uiState.value)
    }

    @Test
    fun `a kept account that cannot reach the family list asks to retry without signing out`() {
        val auth = FakeAuth(current = user("alex@example.com"))
        val vm = SessionViewModel(auth, FakeMembers(member, failWith = IOException("offline")))
        assertEquals(SessionUiState.SignedOut(SignInError.Network), vm.uiState.value)
        // The Firebase session is kept, so the next attempt works once the network is back.
        assertEquals("alex@example.com", auth.currentUser()?.email)
    }

    @Test
    fun `without network the Google picker says so, never that there is no account`() {
        val vm = SessionViewModel(FakeAuth(current = null), FakeMembers(member))
        vm.onGoogleTokenResult(GoogleTokenResult.Offline)
        assertEquals(SessionUiState.SignedOut(SignInError.Network), vm.uiState.value)
    }

    @Test
    fun `retrying a kept account reads the family list again without the picker`() {
        val auth = FakeAuth(current = user("alex@example.com"))
        val members = FakeMembers(member, failWith = IOException("offline"))
        val vm = SessionViewModel(auth, members)
        members.failWith = null
        assertTrue(vm.retryKeptAccount())
        assertTrue(vm.uiState.value is SessionUiState.SignedIn)
        assertEquals("m1" to "uid-1", members.linked)
    }

    @Test
    fun `without a kept account retrying opens the picker`() {
        val vm = SessionViewModel(FakeAuth(current = null), FakeMembers(member))
        assertFalse(vm.retryKeptAccount())
        assertEquals(SessionUiState.SignedOut(), vm.uiState.value)
    }

    @Test
    fun `a family list that never answers ends on Réessayer, not on a spinner`() {
        val members = FakeMembers(member, hangOnFind = true)
        val vm = SessionViewModel(FakeAuth(current = user("alex@example.com")), members)
        assertEquals(SessionUiState.Loading, vm.uiState.value)
        dispatcher.scheduler.advanceTimeBy(SessionViewModel.READ_TIMEOUT_MS + 1)
        assertEquals(SessionUiState.SignedOut(SignInError.Network), vm.uiState.value)
    }

    @Test
    fun `a link waiting for the network does not hold the door`() {
        val members = FakeMembers(member, hangOnLink = true)
        val vm = SessionViewModel(FakeAuth(current = null, next = user("alex@example.com")), members)
        vm.onGoogleTokenResult(GoogleTokenResult.Success("token"))
        assertFalse(vm.uiState.value is SessionUiState.SignedIn)
        dispatcher.scheduler.advanceTimeBy(SessionViewModel.LINK_WAIT_MS + 1)
        assertTrue(vm.uiState.value is SessionUiState.SignedIn)
    }

    @Test
    fun `the same account opened on two phones at the same minute, the second goes in too`() {
        val members = FakeMembers(member, linkedElsewhere = true)
        val vm = SessionViewModel(FakeAuth(current = null, next = user("alex@example.com")), members)
        vm.onGoogleTokenResult(GoogleTokenResult.Success("token"))
        assertEquals("uid-1", (vm.uiState.value as SessionUiState.SignedIn).member.uid)
    }

    @Test
    fun `a link refused for another reason still asks to retry`() {
        val members = FakeMembers(member, hangOnLink = false)
        val failing = object : MemberRepository by members {
            override suspend fun linkAccount(memberId: String, uid: String) = throw IllegalStateException("PERMISSION_DENIED")
        }
        val vm = SessionViewModel(FakeAuth(current = null, next = user("alex@example.com")), failing)
        vm.onGoogleTokenResult(GoogleTokenResult.Success("token"))
        assertEquals(SessionUiState.SignedOut(SignInError.Network), vm.uiState.value)
    }

    @Test
    fun `a member removed while signed in goes back to the sign-in screen with its message`() {
        val members = FakeMembers(member.copy(uid = "uid-1"))
        val auth = FakeAuth(current = user("alex@example.com"))
        val vm = SessionViewModel(auth, members)
        val other = member.copy(id = "m2", email = "sam@example.com")
        members.family.value = Family(emptyList(), listOf(member, other), null)
        assertTrue(vm.uiState.value is SessionUiState.SignedIn)
        members.remove()
        members.family.value = Family(emptyList(), listOf(other), null)
        assertEquals(SessionUiState.NotAMember("alex@example.com"), vm.uiState.value)
        assertNull(auth.currentUser())
    }

    @Test
    fun `refused the family list after a removal, the address is read again`() {
        val members = FakeMembers(member.copy(uid = "uid-1"))
        members.remove()
        members.refuseFamily = true
        val auth = FakeAuth(current = user("alex@example.com"))
        // Signed in from the cache of a previous session: the member is still found once.
        val kept = object : MemberRepository by members {
            var reads = 0
            override suspend fun findByEmail(email: String): Member? =
                if (reads++ == 0) member.copy(uid = "uid-1") else members.findByEmail(email)
        }
        val vm = SessionViewModel(auth, kept)
        assertEquals(SessionUiState.NotAMember("alex@example.com"), vm.uiState.value)
    }

    @Test
    fun `an empty family list from a cold cache, or a cut, never signs out`() {
        val members = FakeMembers(member.copy(uid = "uid-1"))
        val vm = SessionViewModel(FakeAuth(current = user("alex@example.com")), members)
        members.family.value = Family(emptyList(), emptyList(), null)
        assertTrue(vm.uiState.value is SessionUiState.SignedIn)
        val cut = FakeMembers(member.copy(uid = "uid-1"))
        cut.refuseFamily = true
        val offline = object : MemberRepository by cut {
            var reads = 0
            override suspend fun findByEmail(email: String): Member? =
                if (reads++ == 0) member.copy(uid = "uid-1") else throw IOException("hors ligne")
        }
        val vm2 = SessionViewModel(FakeAuth(current = user("alex@example.com")), offline)
        assertTrue(vm2.uiState.value is SessionUiState.SignedIn)
    }

    @Test
    fun `using another account signs out`() {
        val auth = FakeAuth(current = user("alex@example.com"))
        val vm = SessionViewModel(auth, FakeMembers(member))
        vm.signOut()
        assertNull(auth.currentUser())
        assertEquals(SessionUiState.SignedOut(), vm.uiState.value)
    }

    private fun user(email: String, verified: Boolean = true) =
        AuthUser(uid = "uid-1", email = email, emailVerified = verified)

    private class FakeAuth(
        private var current: AuthUser?,
        private val next: AuthUser? = null,
        private val failWith: Exception? = null,
    ) : AuthRepository {
        override fun currentUser() = current
        override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser {
            failWith?.let { throw it }
            return checkNotNull(next).also { current = it }
        }
        override fun signOut() {
            current = null
        }
    }

    private class FakeMembers(
        private var member: Member?,
        var failWith: Exception? = null,
        private val hangOnFind: Boolean = false,
        private val hangOnLink: Boolean = false,
        /** Another phone of the same account links it first: this one is refused. */
        private val linkedElsewhere: Boolean = false,
    ) : MemberRepository {
        var linked: Pair<String, String>? = null
        /** The family list as the server sends it; null: nothing yet. */
        val family = MutableStateFlow<Family?>(null)
        /** The rules refuse the family list (the address left it). */
        var refuseFamily = false
        override suspend fun findByEmail(email: String): Member? {
            failWith?.let { throw it }
            if (hangOnFind) awaitCancellation()
            return member?.takeIf { normalizeEmail(email) == it.email }
        }
        override suspend fun linkAccount(memberId: String, uid: String) {
            if (hangOnLink) awaitCancellation()
            if (linkedElsewhere) {
                member = member?.copy(uid = uid)
                throw IllegalStateException("PERMISSION_DENIED")
            }
            linked = memberId to uid
        }
        /** The member is removed from the family: the address entry goes with them. */
        fun remove() {
            member = null
        }
        override fun newMemberId(): String = "new"
        override suspend fun saveMember(member: Member, previousEmail: String?) = Unit
        override suspend fun removeMember(member: Member, updatedChildren: List<Member>) = Unit
        override fun newHouseholdId(): String = "h-new"
        override suspend fun saveHousehold(household: Household) = Unit
        override suspend fun deleteHousehold(householdId: String) = Unit
        override fun observeFamily(): Flow<Family> =
            if (refuseFamily) flow { throw IllegalStateException("PERMISSION_DENIED") } else family.filterNotNull()
    }
}
