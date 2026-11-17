package fr.familia.app.feature.events.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.SinglePhotoSaver
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.ui.Confirmation

/**
 * Connects the event form to its ViewModel. [onDone] closes it once saved or deleted, with the
 * message to show; [onBack] closes it without a change.
 */
@Composable
fun EventEditRoute(
    container: AppContainer?,
    currentMember: Member,
    eventId: String?,
    onDone: (Confirmation) -> Unit,
    onBack: () -> Unit,
    prefillTitle: String? = null,
) {
    val morePhotos = LocalFeatureFlags.current.isOn(Feature.MorePhotos)
    val recurring = LocalFeatureFlags.current.isOn(Feature.RecurringEvents)
    val viewModel: EventEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                EventEditViewModel(
                    container?.eventRepository,
                    container?.memberRepository,
                    currentMember.id,
                    eventId,
                    savedState = createSavedStateHandle(),
                    photos = if (morePhotos) photoSaver(container) else null,
                    series = container?.eventSeriesRepository,
                    recurring = recurring,
                    prefillTitle = prefillTitle,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        (state as? EventEditUiState.Done)?.let { onDone(it.confirmation) }
    }
    EventEditScreen(
        state = state,
        onChange = viewModel::update,
        onSave = { scope -> viewModel.save(scope) },
        onDelete = { scope -> viewModel.delete(scope) },
        onBack = onBack,
    )
}

private fun photoSaver(container: AppContainer?): SinglePhotoSaver? {
    val uploader = container?.photoUploader ?: return null
    val storage = container.photoStorage ?: return null
    return SinglePhotoSaver(uploader, storage)
}
