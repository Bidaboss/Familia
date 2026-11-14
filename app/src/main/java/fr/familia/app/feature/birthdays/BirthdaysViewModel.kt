package fr.familia.app.feature.birthdays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.upcomingBirthdays
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * Birthdays tab: the 13 birthdays come back every year with nothing to type (PRD, récit 3). They
 * are computed from the members' birth dates, so a corrected date is corrected everywhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BirthdaysViewModel(
    private val members: MemberRepository?,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<BirthdaysUiState> = attempt
        .flatMapLatest { n ->
            if (members == null) {
                flowOf(BirthdaysUiState.Success(today(), emptyList(), emptyList())) // Previews.
            } else {
                members.observeFamily()
                    .map<Family, BirthdaysUiState> { family ->
                        val day = today()
                        BirthdaysUiState.Success(
                            today = day,
                            birthdays = upcomingBirthdays(family.members, day),
                            missingDate = family.members.filter { it.birthDate == null }.sortedBy { it.firstName },
                        )
                    }
                    .onStart { if (n > 0) emit(BirthdaysUiState.Loading) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(BirthdaysUiState.Error)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BirthdaysUiState.Loading)

    fun retry() {
        attempt.update { it + 1 }
    }
}
