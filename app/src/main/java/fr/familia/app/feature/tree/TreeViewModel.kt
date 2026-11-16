package fr.familia.app.feature.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.TreeRepository
import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.MonthChallenge
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

/** What the card of the Accueil shows: the tree, and the challenge of the month once it exists. */
data class TreeUiState(val tree: FamilyTree, val challenge: MonthChallenge?, val month: YearMonth)

class TreeViewModel(repository: TreeRepository?, month: YearMonth) : ViewModel() {
    val uiState: StateFlow<TreeUiState?> = combine(
        repository?.observeTree() ?: flowOf(FamilyTree()),
        repository?.observeChallenge(month) ?: flowOf(null),
    ) { tree, challenge -> TreeUiState(tree, challenge, month) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
