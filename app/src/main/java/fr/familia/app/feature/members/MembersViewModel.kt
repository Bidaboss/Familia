package fr.familia.app.feature.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.HouseholdGroup
import fr.familia.app.core.model.householdGroups
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.canAddMembers
import fr.familia.app.core.model.canEditMember
import fr.familia.app.core.model.isOwner
import fr.familia.app.core.model.managesHousehold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                    val me = family.members.firstOrNull { it.id == currentMemberId }
                    MembersUiState.Success(
                        groups = group(family),
                        currentMemberId = currentMemberId,
                        ownerId = family.ownerId,
                        isOwner = me != null && family.isOwner(me),
                        canAddMembers = me != null && family.canAddMembers(me),
                        editableMemberIds = if (me == null) {
                            emptySet()
                        } else {
                            family.members.filter { family.canEditMember(me, it) }.map { it.id }.toSet()
                        },
                        renamableHouseholdIds = if (me == null) {
                            emptySet()
                        } else {
                            family.households.filter { family.managesHousehold(me, it.id) }.map { it.id }.toSet()
                        },
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

    private val _renameFailed = MutableStateFlow(false)

    /** True when the server refused a household name: the screen says so once. */
    val renameFailed: StateFlow<Boolean> = _renameFailed.asStateFlow()

    /**
     * Gives [household] a new name, trimmed to 1..[MAX_HOUSEHOLD_NAME] characters (the admin principal, or a
     * chef de famille for their own household; the rules check it too). Offline, the list shows the new name at once and Firestore sends it later.
     */
    fun renameHousehold(household: Household, name: String) {
        val repo = members ?: return
        val trimmed = name.trim().take(MAX_HOUSEHOLD_NAME)
        if (trimmed.isEmpty() || trimmed == household.name) return
        viewModelScope.launch {
            try {
                repo.saveHousehold(household.copy(name = trimmed))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _renameFailed.value = true
            }
        }
    }

    fun renameFailureShown() {
        _renameFailed.value = false
    }

    companion object {
        const val MAX_HOUSEHOLD_NAME = 40

        /** See [householdGroups], shared by every list of members. */
        fun group(family: Family): List<HouseholdGroup> = householdGroups(family)
    }
}
