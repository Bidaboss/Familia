package fr.familia.app.feature.members

import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member

/**
 * A household and its members, adults first then children, each sorted by first name. [household]
 * is null for the "Autres" group: members whose household is missing, so nobody silently disappears.
 */
data class HouseholdGroup(val household: Household?, val members: List<Member>)

sealed interface MembersUiState {
    data object Loading : MembersUiState
    data class Success(val groups: List<HouseholdGroup>, val currentMemberId: String) : MembersUiState
    data object Error : MembersUiState
}
