package fr.familia.app.feature.goodnews.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.LocalFeatureFlags

/** Connects the feed of the « Bonnes nouvelles » to its ViewModel. */
@Composable
fun GoodNewsFeedRoute(
    container: AppContainer?,
    currentMember: Member,
    onPublish: () -> Unit,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: GoodNewsFeedViewModel = viewModel(
        factory = viewModelFactory {
            initializer { GoodNewsFeedViewModel(container?.goodNewsRepository, container?.memberRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GoodNewsFeedScreen(
        state = state,
        onPublish = onPublish,
        onOpen = onOpen,
        onEdit = onEdit,
        onReact = viewModel::react,
        onRemove = viewModel::remove,
        onHide = viewModel::hide,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        onDismissError = viewModel::dismissError,
        onBack = onBack,
        littleOnesOn = LocalFeatureFlags.current.isOn(Feature.LittleOnes),
        onLittleOnesOnly = viewModel::showLittleOnesOnly,
    )
}
