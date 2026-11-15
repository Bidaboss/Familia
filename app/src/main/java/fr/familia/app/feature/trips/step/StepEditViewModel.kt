package fr.familia.app.feature.trips.step

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.TripStep
import fr.familia.app.feature.trips.canDeleteStep
import fr.familia.app.feature.trips.isParticipant
import fr.familia.app.feature.trips.reminderTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId

sealed interface StepEditUiState {
    data object Loading : StepEditUiState

    /** The trip or the step is gone, or this member does not take part. */
    data object Unavailable : StepEditUiState

    data class Editing(
        val form: StepForm,
        val isNew: Boolean,
        val canDelete: Boolean,
        val errors: Set<StepFormError>,
        val hasChanges: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : StepEditUiState

    data object Done : StepEditUiState
}

/**
 * Add, edit or delete a step of a trip (PRD, récit 11). Saving sets the reminder of récit 14:
 * 3 hours after the planned arrival, in the phone's time zone, unless someone said « bien arrivé ».
 */
class StepEditViewModel(
    private val trips: TripRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val tripId: String,
    private val stepId: String?,
    private val writeTimeoutMillis: Long = 4_000,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StepEditUiState>(StepEditUiState.Loading)
    val uiState: StateFlow<StepEditUiState> = _uiState.asStateFlow()

    private var original: TripStep? = null
    private var editedId = ""
    private var initialForm = StepForm()
    private var showErrors = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val tripRepo = trips ?: return
        val memberRepo = members ?: return
        val loaded = try {
            Triple(memberRepo.observeFamily().first(), tripRepo.getTrip(tripId), stepId?.let { tripRepo.getStep(tripId, it) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val (family, trip, existing) = loaded ?: return unavailable()
        val me = family.members.firstOrNull { it.id == currentMemberId } ?: return unavailable()
        if (trip == null || !isParticipant(trip, me)) return unavailable()
        if (stepId != null && existing == null) return unavailable()

        original = existing
        editedId = existing?.id ?: tripRepo.newStepId(tripId)
        initialForm = existing?.let { StepForm.from(it) } ?: StepForm(departDate = trip.startDate)
        _uiState.value = StepEditUiState.Editing(
            form = initialForm,
            isNew = existing == null,
            canDelete = existing != null && canDeleteStep(trip, me, family),
            errors = emptySet(),
        )
    }

    private fun unavailable() {
        _uiState.value = StepEditUiState.Unavailable
    }

    fun update(change: (StepForm) -> StepForm) {
        val state = _uiState.value as? StepEditUiState.Editing ?: return
        val form = change(state.form)
        _uiState.value = state.copy(
            form = form,
            errors = if (showErrors) validate(form) else emptySet(),
            hasChanges = form != initialForm,
            saveFailed = false,
        )
    }

    fun save() {
        val state = _uiState.value as? StepEditUiState.Editing ?: return
        val repo = trips ?: return
        if (state.isSaving) return
        showErrors = true
        val errors = validate(state.form)
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errors = errors)
            return
        }
        val step = state.form.toStep(editedId, original?.arrival)
        val remindAt = reminderTime(step, zone(), now())
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            if (write { repo.saveStep(tripId, step, isNew = original == null, remindAt = remindAt) }) {
                _uiState.value = StepEditUiState.Done
            } else {
                markFailed()
            }
        }
    }

    fun delete() {
        val state = _uiState.value as? StepEditUiState.Editing ?: return
        val repo = trips ?: return
        val step = original ?: return
        if (state.isSaving || !state.canDelete) return
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            if (write { repo.deleteStep(tripId, step.id) }) _uiState.value = StepEditUiState.Done else markFailed()
        }
    }

    private fun markFailed() {
        val state = _uiState.value as? StepEditUiState.Editing ?: return
        _uiState.value = state.copy(isSaving = false, saveFailed = true)
    }

    /** True once the write is accepted, or still pending after the timeout (queued offline). */
    private suspend fun write(block: suspend () -> Unit): Boolean = try {
        withTimeoutOrNull(writeTimeoutMillis) { block() }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}
