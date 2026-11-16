package fr.familia.app.feature.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.ExpenseRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.ExpenseContainer
import fr.familia.app.core.model.ExpenseContainerType
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** Someone the form offers: a payer, or a household to share with. */
data class ExpenseChoice(val id: String, val name: String)

sealed interface ExpenseEditUiState {
    data object Loading : ExpenseEditUiState

    /** Not an adult participant, the accounts are closed, or the expense is gone or not this member's. */
    data object Unavailable : ExpenseEditUiState

    data class Editing(
        val form: ExpenseForm,
        val isNew: Boolean,
        val payers: List<ExpenseChoice>,
        val households: List<ExpenseChoice>,
        val errors: Set<ExpenseFormError> = emptySet(),
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : ExpenseEditUiState

    data object Done : ExpenseEditUiState
}

/**
 * Add, change or delete an expense of a trip (récit 16 bis). Its author changes it, the creator of
 * the trip corrects any of them. Offline, the save shows at once and leaves with the network.
 */
class ExpenseEditViewModel(
    private val expenses: ExpenseRepository?,
    private val trips: TripRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    tripId: String,
    private val expenseId: String?,
    private val today: () -> LocalDate = LocalDate::now,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val container = ExpenseContainer(ExpenseContainerType.Trip, tripId)
    private val _uiState = MutableStateFlow<ExpenseEditUiState>(ExpenseEditUiState.Loading)
    val uiState: StateFlow<ExpenseEditUiState> = _uiState.asStateFlow()

    private var me: Member? = null
    private var payerHouseholds: Map<String, String> = emptyMap()
    private var bookHouseholds: Set<String> = emptySet()
    private var showErrors = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val repo = expenses ?: return unavailable()
        val loaded = try {
            val family = members?.observeFamily()?.first() ?: return unavailable()
            val trip = trips?.getTrip(container.id) ?: return unavailable()
            val book = repo.observeBook(container).first() ?: return unavailable()
            val existing = expenseId?.let { id -> repo.observeExpenses(book.id).first().firstOrNull { it.id == id } }
            Triple(family, trip to book, existing)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val (family, tripAndBook, existing) = loaded ?: return unavailable()
        val (trip, book) = tripAndBook
        val member = family.members.firstOrNull { it.id == currentMemberId }
        if (member == null || member.profile != MemberProfile.Adult || member.id !in trip.participantIds || book.closed) return unavailable()
        if (expenseId != null && (existing == null || (existing.authorId != member.id && book.creatorId != member.id))) return unavailable()
        me = member
        val payers = family.members.filter { it.id in trip.participantIds && it.profile == MemberProfile.Adult }
        payerHouseholds = payers.associate { it.id to it.householdId }
        bookHouseholds = book.shares.keys
        val names = family.households.associate { it.id to it.name }
        _uiState.value = ExpenseEditUiState.Editing(
            form = existing?.let(::formOf) ?: ExpenseForm(date = today(), payerId = member.id, householdIds = bookHouseholds),
            isNew = existing == null,
            payers = payers.map { ExpenseChoice(it.id, it.firstName) }.sortedBy { it.name.lowercase() },
            households = bookHouseholds.map { ExpenseChoice(it, names[it] ?: it) }.sortedBy { it.name.lowercase() },
        )
    }

    private fun unavailable() {
        _uiState.value = ExpenseEditUiState.Unavailable
    }

    fun update(change: (ExpenseForm) -> ExpenseForm) {
        _uiState.update { state ->
            if (state !is ExpenseEditUiState.Editing) return@update state
            val form = change(state.form)
            state.copy(form = form, errors = if (showErrors) validate(form, payerHouseholds, bookHouseholds) else emptySet(), saveFailed = false)
        }
    }

    fun save() {
        val state = _uiState.value as? ExpenseEditUiState.Editing ?: return
        val repo = expenses ?: return
        val member = me ?: return
        val errors = validate(state.form, payerHouseholds, bookHouseholds)
        showErrors = true
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errors = errors)
            return
        }
        val expense = expenseOf(expenseId ?: repo.newExpenseId(container.bookId), state.form, payerHouseholds, member.id)
        write(state) { repo.saveExpense(container.bookId, expense, member, isNew = expenseId == null) }
    }

    fun delete() {
        val state = _uiState.value as? ExpenseEditUiState.Editing ?: return
        val repo = expenses ?: return
        val id = expenseId ?: return
        write(state) { repo.deleteExpense(container.bookId, id) }
    }

    private fun write(state: ExpenseEditUiState.Editing, block: suspend () -> Unit) {
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            val ok = try {
                // Offline, the write waits in the queue: it counts as done.
                withTimeoutOrNull(writeTimeoutMillis) { block() }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _uiState.value = if (ok) ExpenseEditUiState.Done else state.copy(isSaving = false, saveFailed = true)
        }
    }
}
