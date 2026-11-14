package fr.familia.app.feature.auth

/** The signed-in Google account, as seen by Firebase. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val emailVerified: Boolean,
)

interface AuthRepository {
    /** The account kept from a previous session, so the app stays signed in. */
    fun currentUser(): AuthUser?

    suspend fun signInWithGoogleIdToken(idToken: String): AuthUser

    fun signOut()
}
