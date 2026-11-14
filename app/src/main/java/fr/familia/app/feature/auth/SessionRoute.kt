package fr.familia.app.feature.auth

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import kotlinx.coroutines.launch

/**
 * Gate in front of the app: shows [SignInScreen] until a family member is signed in,
 * then [signedInContent] for that member.
 */
@Composable
fun SessionRoute(
    container: AppContainer,
    signedInContent: @Composable (Member) -> Unit,
) {
    val viewModel: SessionViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SessionViewModel(container.authRepository, container.memberRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    (state as? SessionUiState.SignedIn)?.let {
        signedInContent(it.member)
        return
    }
    Surface {
        SignInScreen(
            state = state,
            onSignInClick = {
                val provider = container.googleCredentialProvider
                if (provider == null) {
                    viewModel.onGoogleTokenResult(GoogleTokenResult.Failed)
                } else {
                    viewModel.onSignInStarted()
                    scope.launch { viewModel.onGoogleTokenResult(provider.requestIdToken(context)) }
                }
            },
            onUseAnotherAccountClick = viewModel::signOut,
        )
    }
}
