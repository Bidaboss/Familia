package fr.familia.app.feature.weeklyquestion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer

/** Connects « Nos réponses » to its ViewModel. */
@Composable
fun WeeklyArchiveRoute(container: AppContainer?, onOpen: (String) -> Unit, onBack: () -> Unit, onShareGoodNews: (() -> Unit)? = null) {
    val viewModel: WeeklyArchiveViewModel = viewModel(
        factory = viewModelFactory { initializer { WeeklyArchiveViewModel(container?.weeklyQuestionRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WeeklyArchiveScreen(state = state, onOpen = onOpen, onBack = onBack, onShareGoodNews = onShareGoodNews)
}
