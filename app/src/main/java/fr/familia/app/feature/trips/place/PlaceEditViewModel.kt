package fr.familia.app.feature.trips.place

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripPlaceRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripPlace
import fr.familia.app.core.place.FoundPlace
import fr.familia.app.feature.trips.canDeleteStep
import fr.familia.app.feature.trips.isParticipant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime

sealed interface PlaceEditUiState {
    data object Loading : PlaceEditUiState

    /** The trip, the destination or the place is gone, or this member does not follow the trip. */
    data object Unavailable : PlaceEditUiState

    data class Editing(
        val kind: TripPlaceKind,
        val form: PlaceForm,
        val isNew: Boolean,
        /** A participant changes the place; a follower only reads it. */
        val canEdit: Boolean,
        val canDelete: Boolean,
        val errors: Set<PlaceFormError>,
        /** The destination it is under (« Ajaccio »), for the link « Voir sur Tripadvisor ». */
        val destination: String,
        /** The ISO code of the money of the trip (règle 42); null is the euro. */
        val currency: String? = null,
        /** The point of the destination, around which the search looks (règle 20); null when it has none. */
        val destinationLat: Double? = null,
        val destinationLng: Double? = null,
        val hasChanges: Boolean = false,
        /** Récit 122: the first and the last day of the stay of the destination, for « Jour » and the nights. */
        val stayStart: LocalDate? = null,
        val stayEnd: LocalDate? = null,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : PlaceEditUiState

    data object Done : PlaceEditUiState
}

/**
 * The page of an accommodation, a restaurant or a spot of a destination (récit 103, règles 19, 21,
 * 24): a new one under [stepId] with [kind] when [placeId] is null, else the one of [placeId].
 * The participants change the hour, the note and the cost, or delete it with the button at the
 * bottom; the followers read it.
 */
class PlaceEditViewModel(
    private val trips: TripRepository?,
    private val places: TripPlaceRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val tripId: String,
    private val placeId: String?,
    private val stepId: String? = null,
    private val kind: TripPlaceKind? = null,
    /** The name a new place starts with (chosen in a search); null for an empty name. */
    private val presetName: String? = null,
    /** Récit 122: the day of a new place added from « ＋ Ajouter » of a day; null for « À caser ». */
    private val presetDay: LocalDate? = null,
    private val writeTimeoutMillis: Long = 4_000,
    /** Keeps the draft when Android stops the app in the background (audit design n° 3). */
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlaceEditUiState>(PlaceEditUiState.Loading)
    val uiState: StateFlow<PlaceEditUiState> = _uiState.asStateFlow()

    private var original: TripPlace? = null
    private var editedId = ""
    private var editedStepId = ""
    private var editedKind = TripPlaceKind.Restaurant
    private var initialForm = PlaceForm()
    private var showErrors = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val tripRepo = trips ?: return unavailable()
        val placeRepo = places ?: return unavailable()
        val memberRepo = members ?: return unavailable()
        val loaded = (try {
            val family = memberRepo.observeFamily().first()
            val trip = tripRepo.getTrip(tripId)
            val existing = placeId?.let { placeRepo.getPlace(tripId, it) }
            val step = (existing?.stepId ?: stepId)?.let { tripRepo.getStep(tripId, it) }
            // Récit 122: the stay of the destination, as « Le trajet » draws it.
            val stay = if (trip != null && step != null) {
                withTimeoutOrNull(writeTimeoutMillis) { tripRepo.observeSteps(tripId).first() }
                    ?.let { steps -> fr.familia.app.feature.trips.detail.tripTimeline(trip, steps).destinations.firstOrNull { it.step.id == step.id } }
                    ?.let { it.start to it.end }
                    ?: (step.departDate to (step.arriveDate ?: step.departDate))
            } else {
                null
            }
            Loaded(family, trip, existing, step?.id, step?.place, step?.takeIf { it.hasPosition }?.lat, step?.takeIf { it.hasPosition }?.lng, stay)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }) ?: return unavailable()
        val me = loaded.family.members.firstOrNull { it.id == currentMemberId } ?: return unavailable()
        val trip: Trip = loaded.trip ?: return unavailable()
        if (me.id !in trip.readerIds && !isParticipant(trip, me)) return unavailable()
        if (placeId != null && loaded.existing == null) return unavailable()
        val destinationId = loaded.stepId ?: return unavailable()
        val placeKind = loaded.existing?.kind ?: kind ?: return unavailable()
        val canEdit = isParticipant(trip, me)
        // Only a participant adds a place.
        if (loaded.existing == null && !canEdit) return unavailable()

        original = loaded.existing
        editedStepId = destinationId
        editedKind = placeKind
        // Kept with the draft: a place saved just before Android stopped the app is not written twice.
        editedId = loaded.existing?.id ?: savedState.get<String>(DRAFT_ID) ?: placeRepo.newPlaceId(tripId).also { savedState[DRAFT_ID] = it }
        initialForm = loaded.existing?.let { PlaceForm.from(it) } ?: PlaceForm(name = presetName.orEmpty().take(MAX_PLACE_NAME), day = presetDay.takeIf { placeKind != TripPlaceKind.Lodging })
        val form = if (canEdit) restoreDraft() ?: initialForm else initialForm
        _uiState.value = PlaceEditUiState.Editing(
            kind = placeKind,
            form = form,
            isNew = loaded.existing == null,
            canEdit = canEdit,
            canDelete = loaded.existing != null && canDeleteStep(trip, me, loaded.family),
            errors = emptySet(),
            destination = loaded.destination.orEmpty().substringBefore('→').substringBefore(',').trim(),
            currency = trip.currency,
            destinationLat = loaded.lat,
            destinationLng = loaded.lng,
            hasChanges = form != initialForm,
            stayStart = loaded.stay?.first,
            stayEnd = loaded.stay?.second,
        )
    }

    private data class Loaded(
        val family: Family,
        val trip: Trip?,
        val existing: TripPlace?,
        val stepId: String?,
        val destination: String?,
        val lat: Double?,
        val lng: Double?,
        val stay: Pair<LocalDate, LocalDate>?,
    )

    private fun unavailable() {
        _uiState.value = PlaceEditUiState.Unavailable
    }

    fun update(change: (PlaceForm) -> PlaceForm) {
        val state = _uiState.value as? PlaceEditUiState.Editing ?: return
        if (!state.canEdit) return
        val form = change(state.form)
        keepDraft(form)
        _uiState.value = state.copy(
            form = form,
            errors = if (showErrors) validate(form) else emptySet(),
            hasChanges = form != initialForm,
            saveFailed = false,
        )
    }

    /** A place chosen in the search (règle 20, « Choisir un autre … »): its name and its location; the rest stays. */
    fun choose(found: FoundPlace) = update { it.choose(found) }

    /** A name typed in the search that it did not find (« Ajouter « … » »): no address, no point. */
    fun chooseTyped(name: String) = update { it.copy(name = name.trim().take(MAX_PLACE_NAME), pick = null) }

    private fun keepDraft(form: PlaceForm) {
        savedState[DRAFT_NAME] = form.name
        savedState[DRAFT_TIME] = form.arriveTime?.toString().orEmpty()
        savedState[DRAFT_NOTE] = form.note
        savedState[DRAFT_COST] = form.cost
        savedState[DRAFT_PICK_NAME] = form.pick?.name
        savedState[DRAFT_PICK_ADDRESS] = form.pick?.address
        savedState[DRAFT_PICK_LAT] = form.pick?.lat?.toString()
        savedState[DRAFT_PICK_LNG] = form.pick?.lng?.toString()
        savedState[DRAFT_DAY] = form.day?.toString().orEmpty()
        savedState[DRAFT_CHECK_IN] = form.checkIn?.toString().orEmpty()
        savedState[DRAFT_CHECK_OUT] = form.checkOut?.toString().orEmpty()
    }

    /** The draft kept before Android stopped the app, or null when there is none. */
    private fun restoreDraft(): PlaceForm? {
        val name = savedState.get<String>(DRAFT_NAME) ?: return null
        return PlaceForm(
            name = name,
            arriveTime = savedState.get<String>(DRAFT_TIME)?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
            note = savedState.get<String>(DRAFT_NOTE).orEmpty(),
            cost = savedState.get<String>(DRAFT_COST).orEmpty(),
            pick = savedState.get<String>(DRAFT_PICK_NAME)?.let { pickName ->
                PlacePick(
                    name = pickName,
                    address = savedState.get<String>(DRAFT_PICK_ADDRESS),
                    lat = savedState.get<String>(DRAFT_PICK_LAT)?.toDoubleOrNull(),
                    lng = savedState.get<String>(DRAFT_PICK_LNG)?.toDoubleOrNull(),
                )
            },
            day = savedState.get<String>(DRAFT_DAY).toDate(),
            checkIn = savedState.get<String>(DRAFT_CHECK_IN).toDate(),
            checkOut = savedState.get<String>(DRAFT_CHECK_OUT).toDate(),
        )
    }

    fun save() {
        val state = _uiState.value as? PlaceEditUiState.Editing ?: return
        val repo = places ?: return
        if (state.isSaving || !state.canEdit) return
        showErrors = true
        val errors = validate(state.form)
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errors = errors)
            return
        }
        val place = state.form.toPlace(editedId, editedStepId, editedKind)
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            if (write { repo.savePlace(tripId, place, isNew = original == null) }) _uiState.value = PlaceEditUiState.Done else markFailed()
        }
    }

    fun delete() {
        val state = _uiState.value as? PlaceEditUiState.Editing ?: return
        val repo = places ?: return
        val place = original ?: return
        if (state.isSaving || !state.canDelete) return
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            if (write { repo.deletePlace(tripId, place.id) }) _uiState.value = PlaceEditUiState.Done else markFailed()
        }
    }

    private fun markFailed() {
        val state = _uiState.value as? PlaceEditUiState.Editing ?: return
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
        const val DRAFT_ID = "place_draft_id"
        const val DRAFT_NAME = "place_draft_name"
        const val DRAFT_TIME = "place_draft_time"
        const val DRAFT_NOTE = "place_draft_note"
        const val DRAFT_COST = "place_draft_cost"
        const val DRAFT_PICK_NAME = "place_draft_pick_name"
        const val DRAFT_PICK_ADDRESS = "place_draft_pick_address"
        const val DRAFT_PICK_LAT = "place_draft_pick_lat"
        const val DRAFT_PICK_LNG = "place_draft_pick_lng"
        const val DRAFT_DAY = "place_draft_day"
        const val DRAFT_CHECK_IN = "place_draft_check_in"
        const val DRAFT_CHECK_OUT = "place_draft_check_out"
    }
}

private fun String?.toDate(): LocalDate? = this?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
