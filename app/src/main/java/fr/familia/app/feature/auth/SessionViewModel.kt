package fr.familia.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                viewModelScope.launch { resolveMember(user) }
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
        auth?.signOut()
        _uiState.value = SessionUiState.SignedOut()
    }

    private suspend fun resolveMember(user: AuthUser) {
        val auth = auth ?: return
        val members = members ?: return
        val email = user.email
        if (email == null || !user.emailVerified) {
            auth.signOut()
            _uiState.value = SessionUiState.NotAMember(email.orEmpty())
            return
        }
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
            if (member.uid == null) withTimeoutOrNull(LINK_WAIT_MS) { members.linkAccount(member.id, user.uid) }
            _uiState.value = SessionUiState.SignedIn(member.copy(uid = user.uid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline on first launch, or Firestore unreachable: stay signed in and let the person retry.
            _uiState.value = SessionUiState.SignedOut(SignInError.Network)
        }
    }

    companion object {
        const val READ_TIMEOUT_MS = 20_000L
        const val LINK_WAIT_MS = 8_000L
    }
}
