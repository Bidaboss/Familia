package fr.familia.app.feature.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.TreeRepository
import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.MonthChallenge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/**
 * What the card of the Accueil shows: the tree, the challenge of the month once it exists, and
 * [hostTurn], the month the foyer of the member can prepare its challenge for (récit 35 bis).
 */
data class TreeUiState(val tree: FamilyTree, val challenge: MonthChallenge?, val month: YearMonth, val hostTurn: YearMonth? = null)

class TreeViewModel(
    repository: TreeRepository?,
    today: LocalDate,
    /** The foyer of the member; null leaves out the challenge of a foyer. */
    householdId: String? = null,
) : ViewModel() {
    private val month = YearMonth.from(today)

    private val turn = if (repository == null || householdId == null) {
        flowOf(null)
    } else {
        combine(
            repository.observeRotation(),
            repository.observeChallenge(month),
            repository.observeHosted(month),
            repository.observeHosted(month.plusMonths(1)),
        ) { rotation, challenge, now, next -> HostGuide.turn(rotation, householdId, today, challenge, now, next) }
            .catch { e -> if (e is CancellationException) throw e else emit(null) }
    }

    val uiState: StateFlow<TreeUiState?> = combine(
        repository?.observeTree() ?: flowOf(FamilyTree()),
        repository?.observeChallenge(month) ?: flowOf(null),
        turn,
    ) { tree, challenge, hostTurn -> TreeUiState(tree, challenge, month, hostTurn) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
