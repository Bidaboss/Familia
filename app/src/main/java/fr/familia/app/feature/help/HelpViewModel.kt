package fr.familia.app.feature.help

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.isOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * [admins] is null until the family is read, or when it cannot be read: the line then says "un
 * chef de famille". It names the admin principal, then the chefs of the member's household.
 */
data class HelpUiState(val topics: List<HelpTopic>, val admins: String?)

class HelpViewModel(members: MemberRepository?, currentMember: Member) : ViewModel() {

    private val initialTopics = helpTopics(isChef = currentMember.isAdmin)

    val uiState: StateFlow<HelpUiState> = (members?.observeFamily() ?: emptyFlow())
        .map { family ->
            val me = family.members.firstOrNull { it.id == currentMember.id } ?: currentMember
            val owner = family.members.firstOrNull { it.id == family.ownerId }
            val chefs = family.members
                .filter { it.isAdmin && it.householdId == me.householdId && it.id != owner?.id }
                .map { it.firstName }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
            HelpUiState(helpTopics(isChef = me.isAdmin, isOwner = family.isOwner(me)), adminNames(listOfNotNull(owner?.firstName) + chefs))
        }
        .catch { e -> if (e is CancellationException) throw e else emit(HelpUiState(initialTopics, null)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HelpUiState(initialTopics, null))
}
