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
import fr.familia.app.core.model.Feature
import fr.familia.app.core.photo.SinglePhotoSaver
import fr.familia.app.core.ui.LocalFeatureFlags
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
    /** Récit 50: from « Ajouter à mes envies »: « Une idée pour… », and nothing read from the shop. */
    fromShare: Boolean = false,
) {
    val singleList = LocalFeatureFlags.current.isOn(Feature.SingleList)
    val morePhotos = LocalFeatureFlags.current.isOn(Feature.MorePhotos)
    // « Déjà acheté » (récit 68): for the whole family or nobody.
    val alreadyBought = LocalFeatureFlags.current.isOnForFamily(Feature.AlreadyBought)
    val viewModel: GiftItemEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                GiftItemEditViewModel(
                    container?.giftRepository, container?.memberRepository, currentMember.id, listId, itemId,
                    saved = createSavedStateHandle(),
                    shared = shared?.let { GiftItemForm(title = it.title, url = it.url) },
                    previews = container?.linkPreviewRepository,
                    singleList = singleList,
                    photos = if (morePhotos) photoSaver(container) else null,
                    fromShare = fromShare,
                    alreadyBought = alreadyBought,
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
        onChooseTarget = viewModel::chooseTarget,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onBack = onDone,
    )
}

private fun photoSaver(container: AppContainer?): SinglePhotoSaver? {
    val uploader = container?.photoUploader ?: return null
    val storage = container.photoStorage ?: return null
    return SinglePhotoSaver(uploader, storage)
}
