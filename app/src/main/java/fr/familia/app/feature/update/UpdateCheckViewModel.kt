package fr.familia.app.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.AppConfigOfflineException
import fr.familia.app.core.data.AppConfigRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface UpdateCheckUiState {
    data object Checking : UpdateCheckUiState
    data object UpToDate : UpdateCheckUiState
    data class Available(val update: AppUpdate) : UpdateCheckUiState

    /** The server could not be reached: the cache is not trusted to say "up to date". */
    data object Offline : UpdateCheckUiState
    data object Failed : UpdateCheckUiState
}

/** "Vérifier les mises à jour" in the Famille tab: asks the server, never the cache. */
class UpdateCheckViewModel(
    private val config: AppConfigRepository?,
    private val installedVersionCode: Int,
    private val downloadUrl: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateCheckUiState>(UpdateCheckUiState.Checking)
    val uiState: StateFlow<UpdateCheckUiState> = _uiState.asStateFlow()

    init {
        check()
    }

    fun check() {
        val repo = config ?: run { _uiState.value = UpdateCheckUiState.Failed; return }
        _uiState.value = UpdateCheckUiState.Checking
        viewModelScope.launch {
            _uiState.value = try {
                val latest = withTimeoutOrNull(TIMEOUT_MS) { repo.fetchAppConfig() }
                when {
                    latest == null -> UpdateCheckUiState.Offline
                    else -> availableUpdate(latest, installedVersionCode, downloadUrl)
                        ?.let { UpdateCheckUiState.Available(it) }
                        ?: UpdateCheckUiState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppConfigOfflineException) {
                UpdateCheckUiState.Offline
            } catch (e: Exception) {
                UpdateCheckUiState.Failed
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 8_000L
    }
}
