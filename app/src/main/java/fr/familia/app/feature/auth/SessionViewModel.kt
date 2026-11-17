package fr.familia.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Member
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Decides whether the person holding the phone may enter Familia.
 * A Google account is accepted only if its verified address is in the family list; the security
 * rules enforce the same thing on the server.
 */
class SessionViewModel(
    private val auth: AuthRepository?,
    private val members: MemberRepository?,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        if (auth == null || members == null) {
            _uiState.value = SessionUiState.FirebaseNotConfigured
        } else {
            val user = auth.currentUser()
            if (user == null) {
                _uiState.value = SessionUiState.SignedOut()
            } else {
                viewModelScope.launch { resolveMember(user, keptAccount = true) }
            }
        }
    }

    /** The screen is about to open the Google account picker. */
    fun onSignInStarted() {
        _uiState.value = SessionUiState.SigningIn
    }

    /**
     * « Réessayer » after a cut while the Google account was already accepted: the family list is
     * read again without opening the picker, which would need the network once more. False when no
     * account is kept: the screen then opens the picker.
     */
    fun retryKeptAccount(): Boolean {
        val user = auth?.currentUser() ?: return false
        _uiState.value = SessionUiState.SigningIn
        viewModelScope.launch { resolveMember(user) }
        return true
    }

    /** Result of the Google account picker. */
    fun onGoogleTokenResult(result: GoogleTokenResult) {
        val auth = auth ?: return
        when (result) {
            is GoogleTokenResult.Success -> viewModelScope.launch {
                try {
                    resolveMember(auth.signInWithGoogleIdToken(result.idToken))
                } catch (e: CancellationException) {
                    throw e // Never swallow coroutine cancellation.
                } catch (e: IOException) {
                    _uiState.value = SessionUiState.SignedOut(SignInError.Network)
                } catch (e: Exception) {
                    _uiState.value = SessionUiState.SignedOut(SignInError.Unknown)
                }
            }
            GoogleTokenResult.Cancelled -> _uiState.value = SessionUiState.SignedOut()
            GoogleTokenResult.NoAccount -> _uiState.value = SessionUiState.SignedOut(SignInError.NoGoogleAccount)
            GoogleTokenResult.Offline -> _uiState.value = SessionUiState.SignedOut(SignInError.Network)
            GoogleTokenResult.Failed -> _uiState.value = SessionUiState.SignedOut(SignInError.Unknown)
        }
    }

    /** Leaves the current account, for example to try another Google address. */
    fun signOut() {
        membership?.cancel()
        auth?.signOut()
        _uiState.value = SessionUiState.SignedOut()
    }

    private suspend fun resolveMember(user: AuthUser, keptAccount: Boolean = false) {
        val auth = auth ?: return
        val members = members ?: return
        val email = user.email
        if (email == null || !user.emailVerified) {
            auth.signOut()
            _uiState.value = SessionUiState.NotAMember(email.orEmpty())
            return
        }
        if (keptAccount && openFromCache(members, auth, user, email)) return
        try {
            // A network that drags never leaves a spinner for good: after a while, « Réessayer ».
            val member = withTimeout(READ_TIMEOUT_MS) { members.findByEmail(email) }
            if (member == null) {
                auth.signOut()
                _uiState.value = SessionUiState.NotAMember(email)
                return
            }
            // The link is written once: the rules refuse a second one, and a retry reads the uid
            // already set (from the server, or from the write still waiting on the phone). Its
            // answer can wait for the network: the write stays queued and leaves by itself, and
            // the rules know a member by their address, so the app opens meanwhile.
            if (member.uid == null) withTimeoutOrNull(LINK_WAIT_MS) { link(members, member, email, user.uid) }
            _uiState.value = SessionUiState.SignedIn(member.copy(uid = user.uid))
            watchMembership(members, auth, member, email)
        } catch (e: TimeoutCancellationException) {
            // Before CancellationException, which it is: here it means a network that drags.
            _uiState.value = SessionUiState.SignedOut(SignInError.Network)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline on first launch, or Firestore unreachable: stay signed in and let the person retry.
            _uiState.value = SessionUiState.SignedOut(SignInError.Network)
        }
    }

    /**
     * An account kept from a previous session, already linked on this phone: the app opens at once
     * from the phone's copy of the family list, without waiting for a slow network. The server copy
     * is read just after and replaces it if it changed; a removal still signs out through
     * [watchMembership]. False when the phone has no such copy: the server is read as before.
     */
    private suspend fun openFromCache(members: MemberRepository, auth: AuthRepository, user: AuthUser, email: String): Boolean {
        val cached = try {
            members.findCachedByEmail(email)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (cached == null || cached.uid != user.uid) return false
        _uiState.value = SessionUiState.SignedIn(cached)
        watchMembership(members, auth, cached, email)
        viewModelScope.launch { refreshFromServer(members, cached, email) }
        return true
    }

    private suspend fun refreshFromServer(members: MemberRepository, cached: Member, email: String) {
        val fresh = try {
            withTimeoutOrNull(READ_TIMEOUT_MS) { members.findByEmail(email) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return
        val shown = _uiState.value
        if (fresh.id == cached.id && fresh != cached && shown is SessionUiState.SignedIn && shown.member.id == cached.id) {
            _uiState.value = SessionUiState.SignedIn(fresh.copy(uid = cached.uid))
        }
    }

    /**
     * The same account opened on two phones at the same minute (the 21st): both link it, the rules
     * keep the first and refuse the second. The second then reads the link already there and goes on.
     */
    private suspend fun link(members: MemberRepository, member: Member, email: String, uid: String) {
        try {
            members.linkAccount(member.id, uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (members.findByEmail(email)?.uid == null) throw e
        }
    }

    private var membership: Job? = null

    /**
     * A member removed while signed in (a separation, for example) goes back to the sign-in screen
     * with the message for an address outside the family, rather than to screens that fail one by one.
     * The rules refuse the family list as soon as the address leaves it: the address is then read again.
     */
    private fun watchMembership(members: MemberRepository, auth: AuthRepository, member: Member, email: String) {
        membership?.cancel()
        membership = viewModelScope.launch {
            while (true) {
                try {
                    // An empty list is a cache not filled yet, never a removal.
                    members.observeFamily().firstOrNull { family ->
                        family.members.isNotEmpty() && family.members.none { it.id == member.id }
                    } ?: return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Refused: the address may have left the family.
                }
                // A list kept on a shared phone may predate the member: the address decides.
                val gone = stillOut(members, email)
                if (gone) {
                    auth.signOut()
                    _uiState.value = SessionUiState.NotAMember(email)
                    return@launch
                }
                delay(MEMBERSHIP_RETRY_MS)
            }
        }
    }

    /** True only when the server says the address is no longer in the family; a cut is not a removal. */
    private suspend fun stillOut(members: MemberRepository, email: String): Boolean = try {
        members.findByEmail(email) == null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    companion object {
        const val READ_TIMEOUT_MS = 20_000L
        const val LINK_WAIT_MS = 8_000L
        const val MEMBERSHIP_RETRY_MS = 60_000L
    }
}
