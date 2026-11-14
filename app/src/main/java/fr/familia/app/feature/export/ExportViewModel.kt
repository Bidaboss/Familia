package fr.familia.app.feature.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.ExportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data object Exporting : ExportUiState
    data object Done : ExportUiState
    data object Failed : ExportUiState
}

/**
 * Admin backup (PRD, récit 6 ter). The screen picks where the file goes (phone or Drive) with the
 * Android file picker, then hands [export] a function that writes the text there.
 */
class ExportViewModel(
    private val export: ExportRepository?,
    private val appVersion: String,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun export(write: suspend (String) -> Unit) {
        val repo = export ?: return
        if (_uiState.value == ExportUiState.Exporting) return
        _uiState.value = ExportUiState.Exporting
        viewModelScope.launch {
            _uiState.value = try {
                write(familyExportJson(repo.loadFamilyData(), now(), appVersion))
                ExportUiState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportUiState.Failed
            }
        }
    }

    /** The file picker was closed without choosing a place. */
    fun cancelled() {
        if (_uiState.value != ExportUiState.Exporting) _uiState.value = ExportUiState.Idle
    }
}
