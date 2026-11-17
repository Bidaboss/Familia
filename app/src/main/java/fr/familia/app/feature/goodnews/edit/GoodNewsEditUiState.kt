package fr.familia.app.feature.goodnews.edit

import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.core.ui.PickGroup

enum class PublishFailure { NeedsNetwork, PhotoUnreadable, Failed }

sealed interface GoodNewsEditUiState {
    data object Loading : GoodNewsEditUiState

    /** Removed meanwhile, or not the author's. */
    data object Unavailable : GoodNewsEditUiState

    data class Editing(
        val form: GoodNewsForm,
        /** Null for a new good news; the one being changed otherwise (its photos stay). */
        val existing: GoodNews?,
        /** Whose news it may be: me first, then the children of my household (récit 32). */
        val targets: List<Member>,
        /** Who can be named. */
        val candidates: List<Member>,
        /** [candidates] by household, for the picker (règle de Kévin, 17/11). */
        val mentionGroups: List<PickGroup> = emptyList(),
        val errors: Set<GoodNewsFormError> = emptySet(),
        /** While sending: the photo being sent (1, 2, 3), or 0 once the photos are sent. */
        val sendingPhoto: Int? = null,
        val failure: PublishFailure? = null,
        /** The challenge of a foyer in progress, which a new good news can count for; null otherwise. */
        val challenge: MonthChallenge? = null,
    ) : GoodNewsEditUiState {
        val isNew: Boolean get() = existing == null
        val isSaving: Boolean get() = sendingPhoto != null
        val canAddPhotos: Boolean get() = isNew && form.photos.size < GoodNews.PHOTOS_MAX
    }

    /** Published (or queued): the screen closes. */
    data object Done : GoodNewsEditUiState
}
