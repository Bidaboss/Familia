package fr.familia.app.feature.help

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.LocalFeatureFlags

/** Connects the help page to its ViewModel. */
@Composable
fun HelpRoute(container: AppContainer?, currentMember: Member, onReplayOnboarding: () -> Unit, onBack: () -> Unit) {
    val viewModel: HelpViewModel = viewModel(
        factory = viewModelFactory { initializer { HelpViewModel(container?.memberRepository, currentMember) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val flags = LocalFeatureFlags.current
    val shown = state.copy(topics = state.topics.filter { topic -> topic.feature?.let(flags::isOn) ?: true })
    HelpScreen(state = shown, onReplayOnboarding = onReplayOnboarding, onBack = onBack)
}
