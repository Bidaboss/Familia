package fr.familia.app.feature.members.admin

import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member

/** An adult of the family, with the switch that makes them chef de famille. */
data class AdultRow(val member: Member, val householdName: String?, val isOwner: Boolean)

/** A household and how many members it has: only an empty one can be deleted. */
data class HouseholdRow(val household: Household, val memberCount: Int)

sealed interface AdministrationUiState {
    data object Loading : AdministrationUiState

    data object Error : AdministrationUiState

    /** The caller is not (or no longer) the admin principal. */
    data object NotAllowed : AdministrationUiState

    data class Ready(
        /** Adults by first name: only an adult can be chef de famille. */
        val adults: List<AdultRow>,
        /** Households by name. */
        val households: List<HouseholdRow>,
    ) : AdministrationUiState
}
