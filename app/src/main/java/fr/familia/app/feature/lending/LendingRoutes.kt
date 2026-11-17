package fr.familia.app.feature.lending

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.SinglePhotoSaver
import fr.familia.app.feature.lending.edit.LendItemEditScreen
import fr.familia.app.feature.lending.edit.LendItemEditUiState
import fr.familia.app.feature.lending.edit.LendItemEditViewModel
import fr.familia.app.feature.lending.list.LendItemsScreen
import fr.familia.app.feature.lending.list.LendItemsViewModel

/** « On se prête » in Entraide (récit 51), shown under its tab. */
@Composable
fun LendItemsRoute(
    container: AppContainer?,
    currentMember: Member,
    onAdd: (() -> Unit)?,
    onOpen: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val viewModel: LendItemsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LendItemsViewModel(container?.lendItemRepository, container?.memberRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LendItemsScreen(state, onAdd, onOpen, viewModel::retry, modifier, viewModel::search, viewModel::pickCategory, viewModel::toggleAvailableOnly)
}

@Composable
fun LendItemEditRoute(
    container: AppContainer?,
    currentMember: Member,
    itemId: String?,
    onDone: () -> Unit,
) {
    val viewModel: LendItemEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val uploader = container?.photoUploader
                val storage = container?.photoStorage
                LendItemEditViewModel(
                    container?.lendItemRepository,
                    container?.memberRepository,
                    currentMember.id,
                    itemId = itemId,
                    photos = if (uploader != null && storage != null) SinglePhotoSaver(uploader, storage) else null,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == LendItemEditUiState.Done) onDone()
    }
    LendItemEditScreen(state, viewModel::update, viewModel::save, onDone)
}
