package fr.familia.app.feature.goodnews.feed

import fr.familia.app.feature.goodnews.GoodNewsItem
import java.time.Instant

sealed interface GoodNewsFeedUiState {
    data object Loading : GoodNewsFeedUiState
    data object Error : GoodNewsFeedUiState

    /** Newest first, by pages of 20 (récit 32). [actionFailed] shows a short message. */
    data class Success(
        val items: List<GoodNewsItem>,
        val canLoadMore: Boolean,
        val now: Instant,
        val actionFailed: Boolean = false,
        /** The filter « Les petits » (récit 43): only the good news with that label. */
        val littleOnesOnly: Boolean = false,
    ) : GoodNewsFeedUiState
}
