package fr.familia.app.feature.gifts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects the Cadeaux tab to its ViewModel. */
@Composable
fun GiftsRoute(
    container: AppContainer?,
    currentMember: Member,
    onOpenList: (listId: String) -> Unit,
    onNewList: () -> Unit,
    onAllBirthdays: () -> Unit,
    onPrepareSurprise: (memberId: String) -> Unit,
) {
    val viewModel: GiftsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { GiftsViewModel(container?.giftRepository, container?.memberRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GiftsScreen(
        state = state,
        onOpenList = onOpenList,
        onNewList = onNewList,
        onRetry = viewModel::retry,
        onAllBirthdays = onAllBirthdays,
        onPrepareSurprise = onPrepareSurprise,
    )
}
