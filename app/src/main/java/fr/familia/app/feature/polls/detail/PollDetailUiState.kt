package fr.familia.app.feature.polls.detail

import fr.familia.app.core.model.DateAnswer
import fr.familia.app.core.model.Poll

/** An answer being written: one per date (null until tapped), or the indexes chosen. */
data class Draft(val answers: List<DateAnswer?> = emptyList(), val choices: Set<Int> = emptySet())

/** The answer of [memberId], given by me (myself or one of my children). */
data class AnswerCard(
    val memberId: String,
    val firstName: String,
    val isMe: Boolean,
    val draft: Draft,
    /** A vote is saved, and the draft is still the same. */
    val isSaved: Boolean,
    /** « Réponds pour chaque date » / « Choisis au moins une réponse » after a tap on « Envoyer ». */
    val incomplete: Boolean = false,
)

/** One option and who chose it, most yes first in the results. */
data class OptionResult(
    val index: Int,
    val yesNames: List<String>,
    val maybeNames: List<String>,
    val isLeader: Boolean,
    val isChosen: Boolean,
)

/** A row of the answers table of a date poll: one answer per date, null while not answered. */
data class TableRow(val firstName: String, val answers: List<DateAnswer?>)

data class TableGroup(val household: String, val rows: List<TableRow>)

sealed interface PollActionError {
    data object Failed : PollActionError
}

sealed interface PollDetailUiState {
    data object Loading : PollDetailUiState

    /** Deleted, or not shared with me. */
    data object Missing : PollDetailUiState

    data class Success(
        val poll: Poll,
        val isOpen: Boolean,
        val isMine: Boolean,
        /** Shown when the author is no longer in the family. */
        val authorName: String,
        /** What I answer, for me and for my children asked; empty once it is closed. */
        val answers: List<AnswerCard>,
        val results: List<OptionResult>,
        val table: List<TableGroup>,
        val notAnsweredNames: List<String>,
        val canManage: Boolean,
        /** A date poll still without its date, for its author or the admin principal. */
        val canChooseDate: Boolean,
        val isBusy: Boolean = false,
        val error: PollActionError? = null,
    ) : PollDetailUiState
}
