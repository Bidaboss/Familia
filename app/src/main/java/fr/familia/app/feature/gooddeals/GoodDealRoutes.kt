package fr.familia.app.feature.gooddeals

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
import fr.familia.app.feature.gooddeals.detail.GoodDealDetailScreen
import fr.familia.app.feature.gooddeals.detail.GoodDealDetailUiState
import fr.familia.app.feature.gooddeals.detail.GoodDealDetailViewModel
import fr.familia.app.feature.gooddeals.edit.GoodDealEditScreen
import fr.familia.app.feature.gooddeals.edit.GoodDealEditUiState
import fr.familia.app.feature.gooddeals.edit.GoodDealEditViewModel
import fr.familia.app.feature.gooddeals.list.GoodDealsScreen
import fr.familia.app.feature.gooddeals.list.GoodDealsViewModel
import java.time.LocalDate

/** The day in Paris, kept up to date past midnight. */
@Composable
private fun parisToday(): LocalDate {
    val flow = remember { currentDayFlow({ LocalDate.now(PARIS) }) }
    val today by flow.collectAsStateWithLifecycle(LocalDate.now(PARIS))
    return today
}

/** The « Bons plans » of Entraide (récit 45), shown under its tab. */
@Composable
fun GoodDealsRoute(
    container: AppContainer?,
    currentMember: Member,
    onPublish: (() -> Unit)?,
    onOpenDeal: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val viewModel: GoodDealsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { GoodDealsViewModel(container?.goodDealRepository, container?.memberRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GoodDealsScreen(state, parisToday(), onPublish, onOpenDeal, viewModel::retry, modifier)
}

@Composable
fun GoodDealDetailRoute(
    container: AppContainer?,
    currentMember: Member,
    dealId: String,
    onEdit: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val viewModel: GoodDealDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { GoodDealDetailViewModel(container?.goodDealRepository, container?.memberRepository, currentMember.id, dealId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Removed here or elsewhere once it was shown: back to the list.
    var wasShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is GoodDealDetailUiState.Ready) wasShown = true
        if (state == GoodDealDetailUiState.Missing && wasShown) onBack()
    }
    GoodDealDetailScreen(state, parisToday(), viewModel::toggleInterest, onEdit, viewModel::remove, onBack)
}

@Composable
fun GoodDealEditRoute(
    container: AppContainer?,
    currentMember: Member,
    dealId: String?,
    onDone: () -> Unit,
) {
    val viewModel: GoodDealEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val uploader = container?.photoUploader
                val storage = container?.photoStorage
                GoodDealEditViewModel(
                    container?.goodDealRepository,
                    container?.memberRepository,
                    currentMember.id,
                    dealId = dealId,
                    photos = if (uploader != null && storage != null) SinglePhotoSaver(uploader, storage) else null,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == GoodDealEditUiState.Done) onDone()
    }
    GoodDealEditScreen(state, viewModel::update, viewModel::save, onDone)
}
