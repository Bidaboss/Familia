package fr.familia.app.feature.members

import fr.familia.app.core.model.Household
import fr.familia.app.core.model.HouseholdGroup
import fr.familia.app.core.model.Member

sealed interface MembersUiState {
    data object Loading : MembersUiState
    /**
     * The rights are read live, so a chef whose role is removed loses the edit buttons at once
     * (PRD, « Périmètre d'un chef de famille »).
     */
    data class Success(
        val groups: List<HouseholdGroup>,
        val currentMemberId: String,
        /** The admin principal, shown as such in the list. */
        val ownerId: String? = null,
        /** The admin principal: sees the Administration menu. */
        val isOwner: Boolean = false,
        /** The admin principal or a chef de famille: sees the add button. */
        val canAddMembers: Boolean = false,
        /** Members whose row opens the form. */
        val editableMemberIds: Set<String> = emptySet(),
        /** Households that get the rename pencil. */
        val renamableHouseholdIds: Set<String> = emptySet(),
        /** Members whose avatar opens the photo choice (PRD, récit 3 bis). */
        val photoEditableMemberIds: Set<String> = emptySet(),
    ) : MembersUiState
    data object Error : MembersUiState
}
