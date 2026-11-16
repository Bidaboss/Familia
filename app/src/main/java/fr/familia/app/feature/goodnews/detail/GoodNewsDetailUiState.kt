package fr.familia.app.feature.goodnews.detail

import fr.familia.app.core.model.Member
import fr.familia.app.feature.goodnews.GoodNewsItem
import java.time.Instant

sealed interface GoodNewsDetailUiState {
    data object Loading : GoodNewsDetailUiState

    /** Removed, or never readable. */
    data object Missing : GoodNewsDetailUiState

    /** [birthdayPerson]: the member whose birthday it is, on the « Joyeux anniversaire » of Familia (récit 36). */
    data class Success(
        val item: GoodNewsItem,
        val now: Instant,
        val actionFailed: Boolean = false,
        val birthdayPerson: Member? = null,
        /** Its first photo may leave Familia with a share (carte EXPERT-2). */
        val photoShareable: Boolean = false,
    ) : GoodNewsDetailUiState
}
