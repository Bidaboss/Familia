package fr.familia.app.feature.birthdays

import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Member
import java.time.LocalDate

sealed interface BirthdaysUiState {
    data object Loading : BirthdaysUiState
    data class Success(
        val today: LocalDate,
        /** Next birthday of each member, nearest first. */
        val birthdays: List<Birthday>,
        /** Members without a birth date yet: an admin can add it in the Famille tab. */
        val missingDate: List<Member>,
    ) : BirthdaysUiState
    data object Error : BirthdaysUiState
}
