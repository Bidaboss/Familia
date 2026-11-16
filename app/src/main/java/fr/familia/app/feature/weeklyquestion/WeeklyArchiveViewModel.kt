package fr.familia.app.feature.weeklyquestion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.WeeklyQuestionRepository
import fr.familia.app.core.model.WeeklyQuestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface WeeklyArchiveUiState {
    data object Loading : WeeklyArchiveUiState
    data object Empty : WeeklyArchiveUiState
    data object Error : WeeklyArchiveUiState

    /** Newest first. Each one opens on its answers. */
    data class Ready(val questions: List<WeeklyQuestion>) : WeeklyArchiveUiState
}

/** « Nos réponses » (récit 37): the questions already asked, about two years of Mondays. */
class WeeklyArchiveViewModel(questions: WeeklyQuestionRepository?) : ViewModel() {

    val uiState: StateFlow<WeeklyArchiveUiState> = (questions?.observeLatest(ARCHIVE_SIZE) ?: flowOf(emptyList()))
        .map { list -> if (list.isEmpty()) WeeklyArchiveUiState.Empty else WeeklyArchiveUiState.Ready(list) }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(WeeklyArchiveUiState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyArchiveUiState.Loading)

    companion object {
        const val ARCHIVE_SIZE = 104
    }
}
