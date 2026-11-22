package fr.familia.app.feature.trips.word

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** « Le mot de l'étape » of [stepId]: closes itself once the word is published, changed or taken back. */
@Composable
fun StepWordRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
    stepId: String,
    onEditStep: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: StepWordViewModel = viewModel(
        key = "step-word-$tripId-$stepId",
        factory = viewModelFactory {
            initializer {
                StepWordViewModel(
                    container?.tripRepository,
                    container?.tripWordRepository,
                    container?.albumPhotoRepository,
                    container?.memberRepository,
                    currentMember.id,
                    tripId,
                    stepId,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onBack() }
    val actions = remember(viewModel, onEditStep, onBack) {
        StepWordActions(
            onText = viewModel::setText,
            onTogglePhoto = viewModel::togglePhoto,
            onPublish = viewModel::publish,
            onRemove = viewModel::remove,
            onHeart = viewModel::toggleHeart,
            onEditStep = onEditStep,
            onRetry = viewModel::retry,
            onBack = onBack,
        )
    }
    StepWordScreen(state, actions)
}
