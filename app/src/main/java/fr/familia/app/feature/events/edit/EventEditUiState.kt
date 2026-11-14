package fr.familia.app.feature.events.edit

import fr.familia.app.core.model.EventTarget

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
    ) : EventEditUiState

    /** Saved or deleted (or queued until the network is back): the screen closes. */
    data object Done : EventEditUiState
}
