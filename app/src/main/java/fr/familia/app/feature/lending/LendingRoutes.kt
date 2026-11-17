package fr.familia.app.feature.lending

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.SinglePhotoSaver
import fr.familia.app.feature.lending.detail.LendItemDetailScreen
import fr.familia.app.feature.lending.detail.LendItemDetailUiState
import fr.familia.app.feature.lending.detail.LendItemDetailViewModel
import fr.familia.app.feature.lending.edit.LendItemEditScreen
import fr.familia.app.feature.lending.edit.LendItemEditUiState
import fr.familia.app.feature.lending.edit.LendItemEditViewModel
import fr.familia.app.feature.lending.list.LendItemsScreen
import fr.familia.app.feature.lending.list.LendItemsViewModel
import java.time.LocalDate
import java.time.ZoneId

/** The day in Paris, kept up to date past midnight. */
@Composable
private fun parisToday(): LocalDate {
    val zone = ZoneId.of("Europe/Paris")
    val flow = remember { currentDayFlow({ LocalDate.now(zone) }) }
    val today by flow.collectAsStateWithLifecycle(LocalDate.now(zone))
    return today
}

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

@Composable
fun LendItemDetailRoute(
    container: AppContainer?,
    currentMember: Member,
    itemId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: LendItemDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LendItemDetailViewModel(container?.lendItemRepository, container?.memberRepository, currentMember.id, itemId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Removed here or elsewhere once it was shown: back to the list.
    var wasShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is LendItemDetailUiState.Ready) wasShown = true
        if (state == LendItemDetailUiState.Missing && wasShown) onBack()
    }
    LendItemDetailScreen(state, parisToday(), viewModel::ask, viewModel::accept, viewModel::release, onEdit, viewModel::remove, onBack)
}
