package fr.familia.app.feature.help

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** [admins] is null until the family is read, or when it cannot be read: the line then says "un admin". */
data class HelpUiState(val topics: List<HelpTopic>, val admins: String?)

class HelpViewModel(members: MemberRepository?, isAdmin: Boolean) : ViewModel() {

    val uiState: StateFlow<HelpUiState> = (members?.observeFamily() ?: emptyFlow())
        .map { family ->
            val names = family.members.filter { it.isAdmin }.map { it.firstName }.sortedWith(String.CASE_INSENSITIVE_ORDER)
            HelpUiState(helpTopics(isAdmin), adminNames(names))
        }
        .catch { e -> if (e is CancellationException) throw e else emit(HelpUiState(helpTopics(isAdmin), null)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HelpUiState(helpTopics(isAdmin), null))
}
