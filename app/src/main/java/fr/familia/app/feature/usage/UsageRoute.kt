package fr.familia.app.feature.usage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects « Utilisation » to its ViewModel. */
@Composable
fun UsageRoute(container: AppContainer?, currentMember: Member, onBack: () -> Unit) {
    val viewModel: UsageViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                UsageViewModel(container?.memberRepository, container?.usageRepository, container?.goodNewsRepository, currentMember.id)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UsageReportScreen(state, onRetry = viewModel::retry, onBack = onBack)
}
