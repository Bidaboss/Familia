package fr.familia.app.feature.gifts.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects the item form to its ViewModel; [onDone] closes it once saved or removed. */
@Composable
fun GiftItemEditRoute(
    container: AppContainer?,
    currentMember: Member,
    listId: String,
    itemId: String?,
    onDone: () -> Unit,
) {
    val viewModel: GiftItemEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                GiftItemEditViewModel(
                    container?.giftRepository, container?.memberRepository, currentMember.id, listId, itemId,
                    saved = createSavedStateHandle(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == GiftItemEditUiState.Done) onDone()
    }
    GiftItemEditScreen(
        state = state,
        onChange = viewModel::update,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onBack = onDone,
    )
}
