package fr.familia.app.feature.trips.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
import fr.familia.app.feature.trips.canCreateTrip
import fr.familia.app.feature.trips.canManageTrip
import fr.familia.app.feature.trips.participantChoices
import fr.familia.app.feature.trips.tripAudience
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A member offered in the form, with their household name to tell two namesakes apart. */
data class MemberChoice(val id: String, val firstName: String, val householdName: String?, val locked: Boolean = false, val householdId: String? = null)

sealed interface TripEditUiState {
    data object Loading : TripEditUiState

    /** Deleted meanwhile, or not a trip this member may edit. */
    data object Unavailable : TripEditUiState

    data class Editing(
        val form: TripForm,
        val isNew: Boolean,
        /** Who may take part; the creator is locked. */
        val participants: List<MemberChoice>,
        /** The whole family: who follows the trip. */
        val readers: List<MemberChoice>,
        val errors: Set<TripFormError>,
        val hasChanges: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : TripEditUiState

    /** Saved or deleted (or queued until the network is back). [tripId] is the trip to open next. */
    data class Done(val tripId: String?, val deleted: Boolean) : TripEditUiState
}

/**
 * Create a trip ([tripId] null), or edit and delete one (PRD, récits 11 and 12). By default, the
 * whole family follows it; the creator unticks whoever should not see it.
 */
class TripEditViewModel(
    private val trips: TripRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val tripId: String?,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TripEditUiState>(TripEditUiState.Loading)
    val uiState: StateFlow<TripEditUiState> = _uiState.asStateFlow()

    private var original: Trip? = null
    private var me: Member? = null
    private var editedId = ""
    private var initialForm = TripForm()
    private var showErrors = false
    private var familyIds: Set<String> = emptySet()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val tripRepo = trips ?: return
        val memberRepo = members ?: return
        val loaded = try {
            val family = memberRepo.observeFamily().first()
            family to tripId?.let { tripRepo.getTrip(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val family: Family = loaded?.first ?: return unavailable()
        val existing = loaded.second
        val current = family.members.firstOrNull { it.id == currentMemberId } ?: return unavailable()
        if (tripId != null && (existing == null || !canManageTrip(existing, current, family))) return unavailable()
        if (tripId == null && !canCreateTrip(current)) return unavailable()

        me = current
        original = existing
        familyIds = tripAudience(family).map { it.id }.toSet()
        editedId = existing?.id ?: tripRepo.newTripId()
        val creatorId = existing?.creatorId ?: current.id
        val households = family.households.associate { it.id to it.name }
        fun choice(m: Member) = MemberChoice(m.id, m.firstName, households[m.householdId], locked = m.id == creatorId, householdId = m.householdId)
        // Editing keeps the participants already chosen, even those this member could not add.
        val offered = participantChoices(current, family)
        val kept = family.members.filter { it.id in existing?.participantIds.orEmpty() && offered.none { o -> o.id == it.id } }
        initialForm = existing?.let { TripForm.from(it) }
            ?: TripForm(participantIds = setOf(current.id), readerIds = familyIds)
        _uiState.value = TripEditUiState.Editing(
            form = initialForm,
            isNew = existing == null,
            participants = (offered + kept).map(::choice),
            readers = tripAudience(family).sortedBy { it.firstName.lowercase() }.map { choice(it).copy(locked = false) },
            errors = emptySet(),
        )
    }

    private fun unavailable() {
        _uiState.value = TripEditUiState.Unavailable
    }

    fun update(change: (TripForm) -> TripForm) {
        val state = _uiState.value as? TripEditUiState.Editing ?: return
        val form = change(state.form)
        _uiState.value = state.copy(
            form = form,
            errors = if (showErrors) validate(form) else emptySet(),
            hasChanges = form != initialForm,
            saveFailed = false,
        )
    }

    fun save() {
        val state = _uiState.value as? TripEditUiState.Editing ?: return
        val repo = trips ?: return
        val author = me ?: return
        if (state.isSaving) return
        showErrors = true
        val errors = validate(state.form)
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errors = errors)
            return
        }
        val form = state.form
        val creatorId = original?.creatorId ?: author.id
        // Participants in the order of the form, the creator first; followers include them all.
        val participants = state.participants.map { it.id }.filter { it in form.participantIds || it == creatorId }
        val readers = (participants + state.readers.map { it.id }.filter { it in form.readerIds }).distinct()
        val trip = Trip(
            id = editedId,
            title = form.title.trim(),
            startDate = requireNotNull(form.startDate),
            endDate = requireNotNull(form.endDate),
            participantIds = participants,
            readerIds = readers,
            creatorId = creatorId,
            creatorName = original?.creatorName ?: author.firstName,
        )
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            if (write { repo.saveTrip(trip, isNew = original == null) }) {
                _uiState.value = TripEditUiState.Done(tripId = trip.id, deleted = false)
            } else {
                markFailed()
            }
        }
    }

    /** Deletes the trip and its steps, after the screen asked for confirmation. */
    fun delete() {
        val state = _uiState.value as? TripEditUiState.Editing ?: return
        val repo = trips ?: return
        val trip = original ?: return
        if (state.isSaving) return
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            if (write { repo.deleteTrip(trip.id) }) _uiState.value = TripEditUiState.Done(tripId = null, deleted = true) else markFailed()
        }
    }

    private fun markFailed() {
        val state = _uiState.value as? TripEditUiState.Editing ?: return
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
