package fr.familia.app.feature.gifts.received

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects « On m'a offert » to its ViewModel. */
@Composable
fun ReceivedGiftsRoute(container: AppContainer?, currentMember: Member, onBack: () -> Unit) {
    val viewModel: ReceivedGiftsViewModel = viewModel(
        factory = viewModelFactory { initializer { ReceivedGiftsViewModel(container?.giftRepository, currentMember.id) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReceivedGiftsScreen(state, onBack = onBack, onThank = viewModel::thank)
}
