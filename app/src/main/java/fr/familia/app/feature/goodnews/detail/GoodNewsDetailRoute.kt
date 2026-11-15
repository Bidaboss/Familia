package fr.familia.app.feature.goodnews.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects a good news to its ViewModel, and goes back once it is removed. */
@Composable
fun GoodNewsDetailRoute(
    container: AppContainer?,
    currentMember: Member,
    postId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: GoodNewsDetailViewModel = viewModel(
        key = "goodNews-$postId",
        factory = viewModelFactory {
            initializer {
                GoodNewsDetailViewModel(container?.goodNewsRepository, container?.memberRepository, currentMember.id, postId)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var wasShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is GoodNewsDetailUiState.Success) wasShown = true
        if (state == GoodNewsDetailUiState.Missing && wasShown) onBack()
    }
    GoodNewsDetailScreen(
        state = state,
        onReact = viewModel::react,
        onEdit = onEdit,
        onRemove = viewModel::remove,
        onHide = viewModel::setHidden,
        onDismissError = viewModel::dismissError,
        onBack = onBack,
    )
}
