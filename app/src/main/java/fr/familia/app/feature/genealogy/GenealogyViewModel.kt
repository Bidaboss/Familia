package fr.familia.app.feature.genealogy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GenealogyRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.GenealogyTree
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

sealed interface GenealogyUiState {
    data object Loading : GenealogyUiState
    data object Failed : GenealogyUiState

    /** [words]: the words I chose for the people of the tree, by person id. */
    data class Ready(val tree: GenealogyTree, val words: Map<String, String>, val me: String) : GenealogyUiState {
        /** Nobody is joined to anyone yet: the screen says how the tree fills up. */
        val isEmpty: Boolean get() = tree.links.isEmpty()
    }
}

/** L'arbre généalogique (PRD, récit 40): everyone reads it. Changing it comes with récit 40c. */
class GenealogyViewModel(genealogy: GenealogyRepository?, members: MemberRepository?, currentMemberId: String) : ViewModel() {

    val uiState: StateFlow<GenealogyUiState> =
        if (genealogy == null || members == null) {
            MutableStateFlow(GenealogyUiState.Ready(GenealogyTree(emptyList(), emptyList(), emptyList()), emptyMap(), currentMemberId))
        } else {
            combine(
                members.observeFamily(),
                genealogy.observeKin(),
                genealogy.observeLinks(),
                // My words are a nicety: without them, the tree still shows.
                genealogy.observeWords(currentMemberId).onStart { emit(emptyMap()) }.catch { e -> if (e is CancellationException) throw e else emit(emptyMap()) },
            ) { family, kin, links, words ->
                val ready: GenealogyUiState = GenealogyUiState.Ready(GenealogyTree(family.members, kin, links), words, currentMemberId)
                ready
            }
                .catch { e -> if (e is CancellationException) throw e else emit(GenealogyUiState.Failed) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenealogyUiState.Loading)
        }
}
