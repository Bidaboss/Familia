package fr.familia.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
            val member = members.findByEmail(email)
            if (member == null) {
                auth.signOut()
                _uiState.value = SessionUiState.NotAMember(email)
                return
            }
            if (member.uid == null) members.linkAccount(member.id, user.uid)
            _uiState.value = SessionUiState.SignedIn(member.copy(uid = user.uid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline on first launch, or Firestore unreachable: stay signed in and let the person retry.
            _uiState.value = SessionUiState.SignedOut(SignInError.Network)
        }
    }
}
