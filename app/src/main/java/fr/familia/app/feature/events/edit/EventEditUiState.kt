package fr.familia.app.feature.events.edit

import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Household
import fr.familia.app.core.ui.PickGroup
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
        /** « Se répète » shows (récit 54): a new event, or one of a series, with recurringEvents on. */
        val showRepeat: Boolean = false,
        /** One of a series: saving and deleting ask « Cette fois seulement » or « Celle-ci et les suivantes ». */
        val isOccurrence: Boolean = false,
        /** The households of « Chez un foyer » (récit 74), by name. */
        val households: List<Household> = emptyList(),
        /** Whom « @ » suggests in the note (Kévin, 19/11): everyone else, by household. */
        val mentionGroups: List<PickGroup> = emptyList(),
    ) : EventEditUiState

    /** Saved or deleted (or queued until the network is back): the screen closes. */
    data class Done(val confirmation: Confirmation) : EventEditUiState
}

/** What a change to one event of a series applies to (récit 54). */
enum class SeriesScope { ThisOnly, ThisAndFollowing }
