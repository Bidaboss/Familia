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
import fr.familia.app.feature.gifts.share.SharedWish

/** Connects the item form to its ViewModel; [onDone] closes it once saved or removed. */
@Composable
fun GiftItemEditRoute(
    container: AppContainer?,
    currentMember: Member,
    listId: String,
    itemId: String?,
    onDone: () -> Unit,
    /** A link shared from another app, to start a new wish with. */
    shared: SharedWish? = null,
) {
    val viewModel: GiftItemEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                GiftItemEditViewModel(
                    container?.giftRepository, container?.memberRepository, currentMember.id, listId, itemId,
                    saved = createSavedStateHandle(),
                    shared = shared?.let { GiftItemForm(title = it.title, url = it.url) },
                    previews = container?.linkPreviewRepository,
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
