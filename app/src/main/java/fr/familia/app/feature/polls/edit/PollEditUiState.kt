package fr.familia.app.feature.polls.edit

import fr.familia.app.core.model.Member

/** A group to tick at once: the whole family (null label), or one household. */
data class AskShortcut(val label: String?, val memberIds: Set<String>)

/** One household of the « Qui répond ? » list. */
data class AskGroup(val name: String, val members: List<Member>)

sealed interface PollEditUiState {
    data object Loading : PollEditUiState

    /** A child, or a member no longer in the family: they do not ask. */
    data object Unavailable : PollEditUiState

    data class Editing(
        val form: PollForm,
        val groups: List<AskGroup>,
        val shortcuts: List<AskShortcut>,
        val errors: Set<PollFormError> = emptySet(),
        val hasChanges: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
        /** Asked from this event: « Pour : <titre> · <date> » and the motives of the event. */
        val forEvent: PollEvent? = null,
    ) : PollEditUiState

    /** Sent (or queued until the network is back): the screen closes. */
    data object Done : PollEditUiState
}
