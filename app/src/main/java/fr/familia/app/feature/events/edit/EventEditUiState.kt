package fr.familia.app.feature.events.edit

import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.ui.Confirmation

/** A choice of the "Pour qui" list, with the household or child name when there is one. */
data class TargetChoice(val target: EventTarget, val name: String?)

sealed interface EventEditUiState {
    data object Loading : EventEditUiState

    /** Deleted meanwhile, or the event is not one this member may edit. */
    data object Unavailable : EventEditUiState

    data class Editing(
        val form: EventForm,
        val isNew: Boolean,
        val targets: List<TargetChoice>,
        val errors: Set<EventFormError>,
        val hasChanges: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
        /** « Plus de photos » is on: the form shows the cover, [storedCover] being the saved one. */
        val showPhoto: Boolean = false,
        val storedCover: String? = null,
    ) : EventEditUiState

    /** Saved or deleted (or queued until the network is back): the screen closes. */
    data class Done(val confirmation: Confirmation) : EventEditUiState
}
