package fr.familia.app.feature.auth

import fr.familia.app.core.model.Member

/** Why the last sign-in attempt failed, shown under the sign-in button. */
enum class SignInError { NoGoogleAccount, Network, Unknown }

/** Everything the app can show before the family screens (PRD, récit 1). */
sealed interface SessionUiState {
    /** Checking the account kept from a previous session. */
    data object Loading : SessionUiState

    /** No account yet: show the sign-in button, with the last error if any. */
    data class SignedOut(val error: SignInError? = null) : SessionUiState

    /** The Google picker or Firebase is working. */
    data object SigningIn : SessionUiState

    /** Signed in with a Google account that is not in the family list. */
    data class NotAMember(val email: String) : SessionUiState

    /** A family member: the app opens. */
    data class SignedIn(val member: Member) : SessionUiState

    /** The build has no Firebase configuration yet (google-services.json missing). */
    data object FirebaseNotConfigured : SessionUiState
}
