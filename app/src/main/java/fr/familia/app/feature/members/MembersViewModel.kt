package fr.familia.app.feature.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Famille tab: the family grouped by household (PRD, "Utilisateurs"). */
@OptIn(ExperimentalCoroutinesApi::class)
class MembersViewModel(
    private val members: MemberRepository?,
    private val currentMemberId: String,
) : ViewModel() {

    /** Bumped by [retry]: a failed listener is finished, so retrying starts a new one. */
    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<MembersUiState> = attempt
        .flatMapLatest { n ->
            (members?.observeFamily() ?: emptyFlow())
                .map<Family, MembersUiState> { family ->
                    MembersUiState.Success(
                        groups = group(family),
                        currentMemberId = currentMemberId,
                        isAdmin = family.members.any { it.id == currentMemberId && it.isAdmin },
                    )
                }
                .onStart { if (n > 0) emit(MembersUiState.Loading) }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(MembersUiState.Error)
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MembersUiState.Loading)

    fun retry() {
        attempt.update { it + 1 }
    }

    companion object {
        private val memberOrder = compareBy<Member>({ it.profile != MemberProfile.Adult }, { it.firstName.lowercase() })

        /**
         * Households in name order; inside each, adults then children, by first name. Members whose
         * household does not exist come last, in a group without household ("Autres").
         */
        fun group(family: Family): List<HouseholdGroup> {
            val known = family.households.map { it.id }.toSet()
            val households = family.households
                .sortedBy { it.name.lowercase() }
                .map { household ->
                    HouseholdGroup(
                        household = household,
                        members = family.members.filter { it.householdId == household.id }.sortedWith(memberOrder),
                    )
                }
            val others = HouseholdGroup(
                household = null,
                members = family.members.filter { it.householdId !in known }.sortedWith(memberOrder),
            )
            return (households + others).filter { it.members.isNotEmpty() }
        }
    }
}
