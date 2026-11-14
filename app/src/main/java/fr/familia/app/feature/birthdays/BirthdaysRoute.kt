package fr.familia.app.feature.birthdays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer

/** Connects the Birthdays tab to its ViewModel. */
@Composable
fun BirthdaysRoute(container: AppContainer?) {
    val viewModel: BirthdaysViewModel = viewModel(
        factory = viewModelFactory { initializer { BirthdaysViewModel(container?.memberRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BirthdaysScreen(state = state, onRetry = viewModel::retry)
}
