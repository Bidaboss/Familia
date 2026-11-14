package fr.familia.app.feature.auth

import android.content.Context
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
            GoogleTokenResult.NoAccount
        } catch (e: GetCredentialException) {
            GoogleTokenResult.Failed
        }
    }
}
