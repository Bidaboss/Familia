package fr.familia.app.feature.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {

    override fun currentUser(): AuthUser? = auth.currentUser?.toAuthUser()

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = auth.signInWithCredential(credential).await().user
            ?: error("Firebase returned no user after Google sign-in")
        return user.toAuthUser()
    }

    override fun signOut() = auth.signOut()

    private fun FirebaseUser.toAuthUser() = AuthUser(uid = uid, email = email, emailVerified = isEmailVerified)
}
