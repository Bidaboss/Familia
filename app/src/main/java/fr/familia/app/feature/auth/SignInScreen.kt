package fr.familia.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews

/** Sign-in screen (PRD, récit 1). Stateless: it shows [state] and reports clicks. */
@Composable
fun SignInScreen(
    state: SessionUiState,
    onSignInClick: () -> Unit,
    onUseAnotherAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is SessionUiState.Loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Home,
            contentDescription = null, // Decorative: the app name is written below.
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.sign_in_intro),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        when (state) {
            is SessionUiState.NotAMember -> {
                Message(stringResource(R.string.sign_in_not_a_member, state.email), isError = true)
                OutlinedButton(
                    onClick = onUseAnotherAccountClick,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.sign_in_use_another_account)) }
            }
            SessionUiState.FirebaseNotConfigured ->
                Message(stringResource(R.string.sign_in_firebase_missing), isError = true)
            else -> {
                val error = (state as? SessionUiState.SignedOut)?.error
                if (error != null) Message(stringResource(error.messageRes()), isError = true)
                val busy = state is SessionUiState.SigningIn
                Button(
                    onClick = onSignInClick,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    if (busy) {
                        val signingIn = stringResource(R.string.sign_in_in_progress)
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .semantics { contentDescription = signingIn },
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(if (error != null) R.string.sign_in_retry else R.string.sign_in_with_google))
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, isError: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private fun SignInError.messageRes(): Int = when (this) {
    SignInError.NoGoogleAccount -> R.string.sign_in_error_no_account
    SignInError.Network -> R.string.sign_in_error_network
    SignInError.Unknown -> R.string.sign_in_error_unknown
}

@FamiliaPreviews
@Composable
private fun SignInScreenPreview() {
    FamiliaTheme { Surface { SignInScreen(SessionUiState.SignedOut(), {}, {}) } }
}

@FamiliaPreviews
@Composable
private fun SignInScreenNotAMemberPreview() {
    FamiliaTheme {
        Surface { SignInScreen(SessionUiState.NotAMember("prenom.nom@gmail.com"), {}, {}) }
    }
}
