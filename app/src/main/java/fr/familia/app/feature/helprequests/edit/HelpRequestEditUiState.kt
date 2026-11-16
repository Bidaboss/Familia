package fr.familia.app.feature.helprequests.edit

import fr.familia.app.core.model.Member

/** A group of people to tick at once: the whole family, or one household (récit 14). */
data class NotifyShortcut(val label: String?, val memberIds: Set<String>)

sealed interface HelpRequestEditUiState {
    data object Loading : HelpRequestEditUiState

    /** Deleted meanwhile, or a request this member may not edit. */
    data object Unavailable : HelpRequestEditUiState

    data class Editing(
        val form: HelpRequestForm,
        val isNew: Boolean,
        /** For whom the request may be: me first, then my children (récit 13). */
        val targets: List<Member>,
        /** The adults who can be warned. */
        val adults: List<Member>,
        /** « Tout le monde » (null label) then each household. */
        val shortcuts: List<NotifyShortcut>,
        /** Always warned and not untickable: the parents of a child, the current volunteer. */
        val lockedIds: Set<String>,
        val errors: Set<HelpFormError>,
        /** The photo already saved with the request, null for a new one or a duplicate. */
        val storedPhoto: String? = null,
        val hasChanges: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : HelpRequestEditUiState {
        val warnedIds: Set<String> get() = form.notifyIds + lockedIds
    }

    /** Saved (or queued until the network is back): the screen closes. */
    data object Done : HelpRequestEditUiState
}
