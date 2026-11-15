package fr.familia.app.feature.gifts.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects the list form to its ViewModel; [onDone] closes it once saved or deleted. */
@Composable
fun GiftListEditRoute(
    container: AppContainer?,
    currentMember: Member,
    listId: String?,
    forMemberId: String?,
    onDone: (deleted: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: GiftListEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                GiftListEditViewModel(container?.giftRepository, container?.memberRepository, currentMember.id, listId, forMemberId)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == GiftListEditUiState.Done) onDone(viewModel.deleted)
    }
    GiftListEditScreen(
        state = state,
        onChange = viewModel::update,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onBack = onBack,
    )
}
