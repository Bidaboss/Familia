package fr.familia.app.feature.trips.step

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.feature.trips.canDeleteStep
import fr.familia.app.feature.trips.edit.toDate
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
import java.time.LocalTime
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
        /** The trip of the step, recalled at the top of the form (DESIGN-57). */
        val trip: Trip? = null,
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
    /** Keeps the draft when Android stops the app in the background (audit design n° 3). */
    private val savedState: SavedStateHandle = SavedStateHandle(),
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
        // Kept with the draft: a step saved just before Android stopped the app is not written twice.
        editedId = existing?.id ?: savedState.get<String>(DRAFT_ID) ?: tripRepo.newStepId(tripId).also { savedState[DRAFT_ID] = it }
        initialForm = existing?.let { StepForm.from(it) } ?: StepForm(departDate = trip.startDate)
        val form = restoreDraft() ?: initialForm
        _uiState.value = StepEditUiState.Editing(
            form = form,
            hasChanges = form != initialForm,
            isNew = existing == null,
            canDelete = existing != null && canDeleteStep(trip, me, family),
            errors = emptySet(),
            trip = trip,
        )
    }

    private fun unavailable() {
        _uiState.value = StepEditUiState.Unavailable
    }

    fun update(change: (StepForm) -> StepForm) {
        val state = _uiState.value as? StepEditUiState.Editing ?: return
        val form = change(state.form)
        keepDraft(form)
        _uiState.value = state.copy(
            form = form,
            errors = if (showErrors) validate(form) else emptySet(),
            hasChanges = form != initialForm,
            saveFailed = false,
        )
    }

    private fun keepDraft(form: StepForm) {
        savedState[DRAFT_TYPE] = form.type.name
        savedState[DRAFT_PLACE] = form.place
        savedState[DRAFT_DEPART_DATE] = form.departDate?.toString().orEmpty()
        savedState[DRAFT_DEPART_TIME] = form.departTime?.toString().orEmpty()
        savedState[DRAFT_ARRIVE_DATE] = form.arriveDate?.toString().orEmpty()
        savedState[DRAFT_ARRIVE_TIME] = form.arriveTime?.toString().orEmpty()
        savedState[DRAFT_NOTE] = form.note
    }

    /** The draft kept before Android stopped the app, or null when there is none. */
    private fun restoreDraft(): StepForm? {
        val place = savedState.get<String>(DRAFT_PLACE) ?: return null
        return StepForm(
            type = StepType.entries.firstOrNull { it.name == savedState.get<String>(DRAFT_TYPE) } ?: StepForm().type,
            place = place,
            departDate = savedState.get<String>(DRAFT_DEPART_DATE).toDate(),
            departTime = savedState.get<String>(DRAFT_DEPART_TIME).toTime(),
            arriveDate = savedState.get<String>(DRAFT_ARRIVE_DATE).toDate(),
            arriveTime = savedState.get<String>(DRAFT_ARRIVE_TIME).toTime(),
            note = savedState.get<String>(DRAFT_NOTE).orEmpty(),
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

    companion object {
        const val DRAFT_TYPE = "step_draft_type"
        const val DRAFT_PLACE = "step_draft_place"
        const val DRAFT_DEPART_DATE = "step_draft_depart_date"
        const val DRAFT_DEPART_TIME = "step_draft_depart_time"
        const val DRAFT_ARRIVE_DATE = "step_draft_arrive_date"
        const val DRAFT_ARRIVE_TIME = "step_draft_arrive_time"
        const val DRAFT_NOTE = "step_draft_note"
        const val DRAFT_ID = "step_draft_id"
    }
}

private fun String?.toTime(): LocalTime? = this?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
