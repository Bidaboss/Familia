package fr.familia.app.feature.auth

import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.normalizeEmail
import fr.familia.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class SessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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

    private class FakeMembers(private val member: Member) : MemberRepository {
        var linked: Pair<String, String>? = null
        override suspend fun findByEmail(email: String) =
            member.takeIf { normalizeEmail(email) == member.email }
        override suspend fun linkAccount(memberId: String, uid: String) {
            linked = memberId to uid
        }
    }
}
