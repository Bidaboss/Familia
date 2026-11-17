package fr.familia.app.feature.turns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TurnRepository
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Turn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The form: a title, and the households that take part, in their order. */
data class TurnForm(val title: String = "", val order: List<String> = emptyList())

enum class TurnFormError { TitleMissing, TooFewHouseholds }

sealed interface TurnEditUiState {
    data object Loading : TurnEditUiState

    /** Removed meanwhile, or not mine to change. */
    data object Unavailable : TurnEditUiState

    data class Editing(
        val form: TurnForm,
        /** Every household of the family, by name: the ones not in [TurnForm.order] can be added. */
        val households: List<Household>,
        val isNew: Boolean,
        val errors: Set<TurnFormError> = emptySet(),
        val isSaving: Boolean = false,
        val failed: Boolean = false,
    ) : TurnEditUiState

    data object Done : TurnEditUiState
}

internal fun TurnForm.validate(): Set<TurnFormError> = buildSet {
    if (title.isBlank()) add(TurnFormError.TitleMissing)
    if (order.size < Turn.HOUSEHOLDS_MIN) add(TurnFormError.TooFewHouseholds)
}

/** Moves [id] one place up (-1) or down (+1) in [order]. */
internal fun List<String>.moved(id: String, by: Int): List<String> {
    val i = indexOf(id)
    val j = i + by
    if (i < 0 || j !in indices) return this
    return toMutableList().also { it[i] = it[j]; it[j] = id }
}

/**
 * Creates a rotation, or lets its author or the admin principal change it ([turnId]). The order
 * is the order of the households in the form; an edit keeps the household whose turn it is
 * when it is still there, or starts again from the first one.
 */
class TurnEditViewModel(
    private val turns: TurnRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val turnId: String?,
) : ViewModel() {

    private val state = MutableStateFlow<TurnEditUiState>(TurnEditUiState.Loading)
    val uiState: StateFlow<TurnEditUiState> = state.asStateFlow()
    private var original: Turn? = null

    init {
        viewModelScope.launch { state.value = runCatching { initial() }.getOrDefault(TurnEditUiState.Unavailable) }
    }

    private suspend fun initial(): TurnEditUiState {
        val family = members?.observeFamily()?.first() ?: return TurnEditUiState.Unavailable
        val me = family.members.firstOrNull { it.id == currentMemberId } ?: return TurnEditUiState.Unavailable
        val households = family.households.sortedBy { it.name.lowercase() }
        if (turnId == null) {
            if (!canCreateTurn(me)) return TurnEditUiState.Unavailable
            return TurnEditUiState.Editing(TurnForm(), households, isNew = true)
        }
        val turn = turns?.observe(turnId)?.first() ?: return TurnEditUiState.Unavailable
        if (!turnRights(turn, me, family.ownerId).edit) return TurnEditUiState.Unavailable
        original = turn
        return TurnEditUiState.Editing(TurnForm(turn.title, turn.householdIds), households, isNew = false)
    }

    private fun edit(change: (TurnEditUiState.Editing) -> TurnEditUiState.Editing) {
        state.update { if (it is TurnEditUiState.Editing && !it.isSaving) change(it) else it }
    }

    fun setTitle(title: String) = edit { it.copy(form = it.form.copy(title = title.take(Turn.TITLE_MAX)), errors = it.errors - TurnFormError.TitleMissing) }

    /** Adds a household at the end of the order, or takes it out. */
    fun toggle(householdId: String) = edit {
        val order = it.form.order
        val next = if (householdId in order) order - householdId else (order + householdId).take(Turn.HOUSEHOLDS_MAX)
        it.copy(form = it.form.copy(order = next), errors = if (next.size >= Turn.HOUSEHOLDS_MIN) it.errors - TurnFormError.TooFewHouseholds else it.errors)
    }

    fun moveUp(householdId: String) = edit { it.copy(form = it.form.copy(order = it.form.order.moved(householdId, -1))) }

    fun moveDown(householdId: String) = edit { it.copy(form = it.form.copy(order = it.form.order.moved(householdId, 1))) }

    fun dismissFailure() = edit { it.copy(failed = false) }

    fun save() {
        val current = state.value as? TurnEditUiState.Editing ?: return
        if (current.isSaving) return
        val errors = current.form.validate()
        if (errors.isNotEmpty()) {
            state.value = current.copy(errors = errors)
            return
        }
        val repo = turns ?: return
        state.value = current.copy(isSaving = true, failed = false)
        viewModelScope.launch {
            state.value = try {
                val title = current.form.title.trim()
                val before = original
                if (before == null) {
                    val me = members?.observeFamily()?.first()?.members?.firstOrNull { it.id == currentMemberId } ?: error("No member")
                    repo.create(repo.newId(), title, current.form.order, me)
                } else {
                    val keep = current.form.order.indexOf(before.currentHouseholdId).takeIf { it >= 0 } ?: 0
                    repo.edit(before.id, title, current.form.order, keep)
                }
                TurnEditUiState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                current.copy(isSaving = false, failed = true)
            }
        }
    }
}
