package fr.familia.app.feature.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Outcome of the Google account picker. */
sealed interface GoogleTokenResult {
    data class Success(val idToken: String) : GoogleTokenResult
    data object Cancelled : GoogleTokenResult
    data object NoAccount : GoogleTokenResult

    /** No network: the picker could not reach Google. */
    data object Offline : GoogleTokenResult
    data object Failed : GoogleTokenResult
}

/**
 * Shows Android's Google account picker (Credential Manager) and returns an ID token for Firebase.
 * It needs the Activity context of the screen, so it is called from the screen, not the ViewModel.
 */
class GoogleCredentialProvider(private val webClientId: String) {

    suspend fun requestIdToken(activityContext: Context): GoogleTokenResult {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
                .credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleTokenResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleTokenResult.Failed
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleTokenResult.Cancelled
        } catch (e: NoCredentialException) {
            // Without network, Android also says « no credential »: never tell then to add an account.
            if (isOnline(activityContext)) GoogleTokenResult.NoAccount else GoogleTokenResult.Offline
        } catch (e: GetCredentialException) {
            if (isOnline(activityContext)) GoogleTokenResult.Failed else GoogleTokenResult.Offline
        }
    }

    private fun isOnline(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
