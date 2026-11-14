package fr.familia.app.feature.members.edit

import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member

sealed interface MemberEditUiState {
    data object Loading : MemberEditUiState

    /** The member was removed meanwhile, or the link is wrong. */
    data object NotFound : MemberEditUiState

    data class Editing(
        val form: MemberForm,
        val isNew: Boolean,
        val households: List<Household>,
        /** Adults who can be responsible for a child (never the edited member). */
        val adults: List<Member>,
        /** False on one's own profile: the rules forbid changing one's own admin flag. */
        val canChangeAdmin: Boolean,
        /** Shown once the admin has tried to save, so empty fields are not red from the start. */
        val errors: Set<FormError>,
        /** True once the form differs from what was opened: leaving then asks for confirmation. */
        val hasChanges: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : MemberEditUiState

    /** Saved (or queued until the network is back): the screen closes. */
    data object Saved : MemberEditUiState
}
