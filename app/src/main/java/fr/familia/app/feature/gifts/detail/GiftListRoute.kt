package fr.familia.app.feature.gifts.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.viewas.LocalViewAs
import androidx.compose.ui.platform.LocalContext
import fr.familia.app.core.model.Feature
import fr.familia.app.core.share.share
import fr.familia.app.core.share.wishesShare
import fr.familia.app.core.ui.LocalFeatureFlags

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
    /** The occasion of a reservation of « Mes envies » is shown and chosen only with the reveal on. */
    showClaimOccasion: Boolean = false,
    /** The beneficiary's page, for their sizes and tastes (récit 7 bis). */
    onOpenBeneficiary: ((memberId: String) -> Unit)? = null,
) {
    // « Voir comme »: the admin principal still reads with their own account.
    val readerId = LocalViewAs.current.readerId ?: currentMember.id
    val viewModel: GiftListViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                GiftListViewModel(container?.giftRepository, container?.memberRepository, currentMember.id, listId, readerId = readerId)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // « Voir comme »: the admin principal never sends a list out under someone else's name.
    val shareOn = LocalFeatureFlags.current.isOn(Feature.Share) && readerId == currentMember.id
    val shown = state as? GiftListUiState.Success
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
            onSetClaimOccasion = if (showClaimOccasion) viewModel::setClaimOccasion else null,
            onSetShare = viewModel::setShare,
            onLeaveShare = viewModel::leaveShare,
            onMarkReceived = viewModel::markReceived,
            onOpenReceived = onOpenReceived,
            onOpenBeneficiary = onOpenBeneficiary,
            onShareOutside = shown?.takeIf { shareOn && it.canSendOutside }?.let { success ->
                { share(context, wishesShare(context, success.list.id, success.beneficiary.firstName)) }
            },
        ),
    )
}
