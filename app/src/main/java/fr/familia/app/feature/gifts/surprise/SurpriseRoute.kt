package fr.familia.app.feature.gifts.surprise

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects a surprise to its ViewModel. [onAddIdea] opens a new idea of the notebook for the person. */
@Composable
fun SurpriseRoute(
    container: AppContainer?,
    currentMember: Member,
    surpriseId: String,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onAddIdea: (beneficiaryId: String) -> Unit,
) {
    val viewModel: SurpriseViewModel = viewModel(
        key = "surprise-$surpriseId",
        factory = viewModelFactory {
            initializer {
                SurpriseViewModel(
                    container?.surpriseRepository, container?.memberRepository, container?.giftRepository,
                    container?.notebookRepository, currentMember.id, surpriseId,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    SurpriseScreen(
        state,
        SurpriseActions(
            onBack = onBack,
            onSettings = onSettings,
            onRate = viewModel::rate,
            onToggleIdea = { viewModel.toggleIdea(it, currentMember.firstName) },
            onAddIdea = { (state as? SurpriseUiState.Ready)?.let { onAddIdea(it.beneficiary.id) } },
            onRemove = viewModel::setRemoved,
            onTakeAlone = viewModel::takeAlone,
            onTakeTogether = viewModel::takeTogether,
            onRelease = viewModel::release,
            onShare = viewModel::setShare,
            onValidate = viewModel::setValidated,
            onDecline = { (state as? SurpriseUiState.Ready)?.let(viewModel::decline) },
            // Offered, the surprise is over for everyone: back to Cadeaux, where its tile is gone.
            onOffered = { viewModel.markOffered(); onBack() },
        ),
    )
}
