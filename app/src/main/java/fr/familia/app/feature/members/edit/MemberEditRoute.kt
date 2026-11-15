package fr.familia.app.feature.members.edit

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.BuildConfig
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.Confirmation

/**
 * Connects the member form to its ViewModel and sends invitations. [onDone] closes it once saved,
 * with the message to show; [onBack] closes it without one.
 */
@Composable
fun MemberEditRoute(
    container: AppContainer?,
    currentMember: Member,
    memberId: String?,
    onDone: (Confirmation) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: MemberEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                MemberEditViewModel(container?.memberRepository, currentMember, memberId, savedState = createSavedStateHandle())
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state) {
        (state as? MemberEditUiState.Saved)?.let { onDone(it.confirmation) }
    }
    MemberEditScreen(
        state = state,
        onChange = viewModel::update,
        onAddHousehold = viewModel::addHousehold,
        onSave = viewModel::save,
        onBack = onBack,
        onRemove = viewModel::remove,
        onInvite = { firstName, email -> shareInvitation(context, firstName, email) },
    )
}

/** Opens the Android share sheet (WhatsApp, SMS…) with the invitation text. */
private fun shareInvitation(context: Context, firstName: String, email: String) {
    val link = BuildConfig.DOWNLOAD_URL
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.invite_message, firstName, link, email))
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.invite_chooser)))
}
