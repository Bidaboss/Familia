package fr.familia.app.feature.helprequests.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.viewas.LocalViewAs

/** Connects the « Coups de main » tab to its ViewModel. */
@Composable
fun HelpRequestsRoute(
    container: AppContainer?,
    currentMember: Member,
    onAsk: () -> Unit,
    onOpenRequest: (String) -> Unit,
    showTitle: Boolean = true,
) {
    val viewModel: HelpRequestsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HelpRequestsViewModel(container?.helpRequestRepository, container?.memberRepository, currentMember.id)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    // What this tab shows is seen: the badge of the Entraide tab forgets it (retour [BUG] du 18/11).
    // In « Voir comme », nothing is written, even on this phone.
    val viewingAs = LocalViewAs.current.viewed != null
    val shownIds = (state as? HelpRequestsUiState.Success)?.open?.map { it.request.id }.orEmpty()
    LaunchedEffect(shownIds, viewingAs) {
        if (!viewingAs && shownIds.isNotEmpty()) container?.seenHelpRequests(currentMember.id)?.markSeen(shownIds)
    }
    HelpRequestsScreen(
        state = state,
        onAsk = onAsk,
        onOpenRequest = onOpenRequest,
        onRetry = viewModel::retry,
        r1bis = LocalFeatureFlags.current.isOn(Feature.PresentationR1bis),
        showTitle = showTitle,
        error = error,
        onTake = viewModel::take,
        onDecline = viewModel::decline,
        onDismissError = viewModel::dismissError,
    )
}
