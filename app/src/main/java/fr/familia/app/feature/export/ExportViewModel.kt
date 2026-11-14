package fr.familia.app.feature.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.ExportOfflineException
import fr.familia.app.core.data.ExportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

sealed interface ExportUiState {
    data object Idle : ExportUiState

    /** Reading the data from the server, or writing the file. */
    data object Exporting : ExportUiState

    /** The data is ready: the screen opens the file picker with [fileName]. */
    data class AwaitingDestination(val fileName: String) : ExportUiState

    /** The file picker is open. Kept apart so that a recreated screen does not open it twice. */
    data object Picking : ExportUiState

    data object Done : ExportUiState

    /** The server could not be reached: no backup at all rather than an incomplete one. */
    data object Offline : ExportUiState

    data object Failed : ExportUiState
}

/**
 * Admin backup (PRD, récit 6 ter). The data is read from the server first, and only then does the
 * admin choose where the file goes (phone or Drive), so a failed reading never leaves an empty file.
 */
class ExportViewModel(
    private val export: ExportRepository?,
    private val appVersion: String,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private var json: String? = null

    fun prepare() {
        val repo = export ?: return
        val current = _uiState.value
        if (current == ExportUiState.Exporting || current == ExportUiState.Picking || current is ExportUiState.AwaitingDestination) return
        _uiState.value = ExportUiState.Exporting
        viewModelScope.launch {
            _uiState.value = try {
                val at = now()
                json = familyExportJson(repo.loadFamilyData(), at, appVersion)
                ExportUiState.AwaitingDestination(exportFileName(at.atZone(ZoneId.systemDefault()).toLocalDate()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExportOfflineException) {
                ExportUiState.Offline
            } catch (e: Exception) {
                ExportUiState.Failed
            }
        }
    }

    /** Writes the prepared file with [write]; [write] removes the file it created if it fails. */
    fun save(write: suspend (String) -> Unit) {
        val text = json ?: return
        _uiState.value = ExportUiState.Exporting
        viewModelScope.launch {
            _uiState.value = try {
                write(text)
                json = null
                ExportUiState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportUiState.Failed
            }
        }
    }

    /** The screen has opened the file picker. */
    fun pickerOpened() {
        if (_uiState.value is ExportUiState.AwaitingDestination) _uiState.value = ExportUiState.Picking
    }

    /** The file picker was closed without choosing a place. */
    fun cancelled() {
        json = null
        _uiState.value = ExportUiState.Idle
    }
}
