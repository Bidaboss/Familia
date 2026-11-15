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
        /** The admin principal only: a chef de famille adds members to their own household. */
        val canAddHousehold: Boolean = false,
        /** False for a chef when the admin principal chose a responsible adult from another household. */
        val canEditParents: Boolean = true,
        /** Shown once the admin has tried to save, so empty fields are not red from the start. */
        val errors: Set<FormError>,
        /** False for a new member and for oneself: the rules forbid removing oneself. */
        val canRemove: Boolean = false,
        /** True when only the admin principal may remove this member (a chef, or a parent elsewhere). */
        val removeOwnerOnly: Boolean = false,
        /** Children for whom this member is the only responsible adult: removal waits until another is added. */
        val soleParentOf: List<String> = emptyList(),
        /** True for a member with an address who has not signed in yet. */
        val canInvite: Boolean = false,
        /** True once the form differs from what was opened: leaving then asks for confirmation. */
        val hasChanges: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : MemberEditUiState

    /** Saved or removed (or queued until the network is back): the screen closes. */
    data object Saved : MemberEditUiState

    /** A new member was saved: the screen offers to send the invitation when they have an address. */
    data class Added(val firstName: String, val email: String?) : MemberEditUiState
}
