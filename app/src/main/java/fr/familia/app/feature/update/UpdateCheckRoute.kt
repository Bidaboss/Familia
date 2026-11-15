package fr.familia.app.feature.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.BuildConfig
import fr.familia.app.core.data.AppContainer

/** Connects the update check screen to its ViewModel. */
@Composable
fun UpdateCheckRoute(container: AppContainer?, onBack: () -> Unit) {
    val viewModel: UpdateCheckViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                UpdateCheckViewModel(container?.appConfigRepository, BuildConfig.VERSION_CODE, BuildConfig.DOWNLOAD_URL)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updater = LocalInAppUpdater.current
    val context = LocalContext.current
    UpdateCheckScreen(
        state = state,
        installedVersion = BuildConfig.VERSION_NAME,
        onCheck = viewModel::check,
        onUpdate = { url ->
            val update = (state as? UpdateCheckUiState.Available)?.update
            if (updater != null && update != null) updater.start(update) else openDownload(context, url)
        },
        onBack = onBack,
    )
}
