package fr.familia.app.feature.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.BuildConfig
import fr.familia.app.core.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/** Connects the backup screen to its ViewModel and to the Android file picker. */
@Composable
fun ExportRoute(container: AppContainer?, onBack: () -> Unit) {
    val viewModel: ExportViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ExportViewModel(container?.exportRepository, BuildConfig.VERSION_NAME) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    // The picker lists the phone's folders and Google Drive, so the admin chooses where it goes.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) {
            viewModel.cancelled()
        } else {
            viewModel.export { json ->
                withContext(Dispatchers.IO) {
                    val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")
                    stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
            }
        }
    }
    ExportScreen(
        state = state,
        onExport = { picker.launch(exportFileName(LocalDate.now())) },
        onBack = onBack,
    )
}
