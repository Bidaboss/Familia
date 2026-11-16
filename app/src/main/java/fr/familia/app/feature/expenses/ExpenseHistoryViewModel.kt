package fr.familia.app.feature.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.ExpenseRepository
import fr.familia.app.core.model.ExpenseContainer
import fr.familia.app.core.model.ExpenseHistoryAction
import fr.familia.app.core.model.ExpenseHistoryEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** What the history tells of one change, in the words of the members. */
enum class ExpenseField { Title, Amount, Date, Payer, Category, Split, Currency }

/** One line of the history: who, what, when; [fields] for a change only. */
data class HistoryRow(
    val id: String,
    val action: ExpenseHistoryAction,
    val byName: String,
    val amountCents: Long,
    val fields: List<ExpenseField>,
    val at: LocalDateTime?,
)

sealed interface ExpenseHistoryUiState {
    data object Loading : ExpenseHistoryUiState
    data object Error : ExpenseHistoryUiState

    /** [title]: the last known title of the expense; [rows], newest first. */
    data class Success(val title: String, val rows: List<HistoryRow>) : ExpenseHistoryUiState
}

/** The Firestore fields the server follows, grouped the way a member thinks of them. */
fun fieldsOf(changed: List<String>): List<ExpenseField> = changed.mapNotNull {
    when (it) {
        "title" -> ExpenseField.Title
        "amountCents" -> ExpenseField.Amount
        "date" -> ExpenseField.Date
        "payerId" -> ExpenseField.Payer
        "category" -> ExpenseField.Category
        "mode", "householdIds", "amounts" -> ExpenseField.Split
        "currency", "originalCents", "rate" -> ExpenseField.Currency
        else -> null
    }
}.distinct().sorted()

/** The history, newest first, in the time of the phone. */
fun historyRows(entries: List<ExpenseHistoryEntry>, zone: ZoneId): List<HistoryRow> =
    entries.sortedWith(compareByDescending<ExpenseHistoryEntry> { it.at }.thenByDescending { it.id }).map {
        HistoryRow(it.id, it.action, it.byName, it.amountCents, fieldsOf(it.changed), it.at?.atZone(zone)?.toLocalDateTime())
    }

/**
 * The history of one expense of a trip (récit 16 bis) or of an event (PRODUIT-57), written by the server on each change. The
 * rules let only the adult participants read it: anyone else gets [ExpenseHistoryUiState.Error].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseHistoryViewModel(
    private val expenses: ExpenseRepository?,
    container: ExpenseContainer,
    private val expenseId: String,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val bookId = container.bookId
    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<ExpenseHistoryUiState> = attempt
        .flatMapLatest { n ->
            val repo = expenses ?: return@flatMapLatest flowOf(ExpenseHistoryUiState.Error)
            repo.observeHistory(bookId, expenseId)
                .map<List<ExpenseHistoryEntry>, ExpenseHistoryUiState> { list ->
                    val rows = historyRows(list, zone)
                    val title = list.maxByOrNull { it.at ?: Instant.MIN }?.title.orEmpty()
                    ExpenseHistoryUiState.Success(title, rows)
                }
                .onStart { if (n > 0) emit(ExpenseHistoryUiState.Loading) }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(ExpenseHistoryUiState.Error)
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseHistoryUiState.Loading)

    fun retry() {
        attempt.update { it + 1 }
    }
}
