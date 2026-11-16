package fr.familia.app.feature.polls.list

import fr.familia.app.core.model.Poll

/** One card of the list. */
data class PollRow(
    val poll: Poll,
    val authorName: String,
    val isMine: Boolean,
    val isOpen: Boolean,
    /** I still have to answer for myself. */
    val meToAnswer: Boolean,
    /** My children asked who have no answer yet. */
    val childrenToAnswer: List<String>,
    val answeredCount: Int,
    val askedCount: Int,
    /** The options in the lead, once it is closed. */
    val leaders: List<Int>,
)

sealed interface PollsUiState {
    data object Loading : PollsUiState
    data object Error : PollsUiState

    data class Success(
        /** Those I still have to answer first, then the others, newest first. */
        val open: List<PollRow>,
        val closed: List<PollRow>,
        val canAsk: Boolean,
    ) : PollsUiState {
        val isEmpty: Boolean get() = open.isEmpty() && closed.isEmpty()
    }
}
