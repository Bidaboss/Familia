package fr.familia.app.feature.gifts.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects a gift list to its ViewModel. */
@Composable
fun GiftListRoute(
    container: AppContainer?,
    currentMember: Member,
    listId: String,
    onEditList: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (itemId: String) -> Unit,
    onBack: () -> Unit,
    onOpenReceived: (() -> Unit)? = null,
) {
    val viewModel: GiftListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { GiftListViewModel(container?.giftRepository, container?.memberRepository, currentMember.id, listId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GiftListScreen(
        state = state,
        actions = GiftListActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onEditList = onEditList,
            onAddItem = onAddItem,
            onEditItem = onEditItem,
            onTake = viewModel::take,
            onOpenJointGift = viewModel::openJointGift,
            onRelease = viewModel::release,
            onSetClaimOccasion = viewModel::setClaimOccasion,
            onSetShare = viewModel::setShare,
            onLeaveShare = viewModel::leaveShare,
            onMarkReceived = viewModel::markReceived,
            onOpenReceived = onOpenReceived,
        ),
    )
}
