package fr.familia.app.feature.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.MemberProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Famille tab: the family grouped by household (PRD, "Utilisateurs"). */
class MembersViewModel(
    members: MemberRepository?,
    private val currentMemberId: String,
) : ViewModel() {

    val uiState: StateFlow<MembersUiState> = (members?.observeFamily() ?: emptyFlow())
        .map<Family, MembersUiState> { MembersUiState.Success(group(it), currentMemberId) }
        .catch { emit(MembersUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MembersUiState.Loading)

    companion object {
        /** Households in name order; inside each, adults then children, by first name. */
        fun group(family: Family): List<HouseholdGroup> =
            family.households
                .sortedBy { it.name.lowercase() }
                .map { household ->
                    HouseholdGroup(
                        household = household,
                        members = family.members
                            .filter { it.householdId == household.id }
                            .sortedWith(
                                compareBy({ it.profile != MemberProfile.Adult }, { it.firstName.lowercase() }),
                            ),
                    )
                }
                .filter { it.members.isNotEmpty() }
    }
}
