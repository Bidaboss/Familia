package fr.familia.app.feature.goodnews.detail

import fr.familia.app.feature.goodnews.GoodNewsItem
import java.time.Instant

sealed interface GoodNewsDetailUiState {
    data object Loading : GoodNewsDetailUiState

    /** Removed, or never readable. */
    data object Missing : GoodNewsDetailUiState

    data class Success(val item: GoodNewsItem, val now: Instant, val actionFailed: Boolean = false) : GoodNewsDetailUiState
}
