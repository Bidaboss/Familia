package fr.familia.app.feature.trips.place

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.place.FoundPlace
import fr.familia.app.core.place.PlaceSearch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the search of a place shows under its field. */
sealed interface PlaceSearchResults {
    data object Loading : PlaceSearchResults

    /** The destination has no point on the map: only a typed name can be added. */
    data object NoPoint : PlaceSearchResults

    /** OpenStreetMap did not answer (no network, busy). */
    data object Failed : PlaceSearchResults

    /** Nearest first; empty when nothing matches. */
    data class Found(val places: List<FoundPlace>) : PlaceSearchResults
}

data class PlaceSearchUiState(
    val kind: TripPlaceKind,
    val text: String = "",
    val results: PlaceSearchResults = PlaceSearchResults.Loading,
)

/**
 * The search dedicated to each kind of place (récit 103, règle 20): the named accommodations,
 * restaurants or spots around the point of the destination, then those whose name holds the typed
 * words, a short while after the last key. Nothing is stored; only the point and the words leave
 * the phone.
 */
class PlaceSearchViewModel(
    private val search: PlaceSearch?,
    kind: TripPlaceKind,
    private val lat: Double?,
    private val lng: Double?,
    private val waitMillis: Long = 600,
    /** Keeps the typed words when Android stops the app in the background. */
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaceSearchUiState(kind = kind, text = savedState.get<String>(TEXT).orEmpty()))
    val uiState: StateFlow<PlaceSearchUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        run(wait = false)
    }

    fun type(text: String) {
        val clean = text.take(MAX_PLACE_NAME)
        val before = _uiState.value.text
        if (clean == before) return
        savedState[TEXT] = clean
        _uiState.value = _uiState.value.copy(text = clean)
        // Only the words count: a space added at the end asks nothing new.
        if (clean.trim() != before.trim()) run(wait = true)
    }

    fun retry() = run(wait = false)

    private fun run(wait: Boolean) {
        job?.cancel()
        val service = search
        if (service == null || lat == null || lng == null) {
            _uiState.value = _uiState.value.copy(results = PlaceSearchResults.NoPoint)
            return
        }
        val state = _uiState.value
        _uiState.value = state.copy(results = PlaceSearchResults.Loading)
        job = viewModelScope.launch {
            if (wait) delay(waitMillis)
            val found = service.search(lat, lng, state.kind, state.text.trim())
            _uiState.value = _uiState.value.copy(results = found?.let { PlaceSearchResults.Found(it) } ?: PlaceSearchResults.Failed)
        }
    }

    private companion object {
        const val TEXT = "place_search_text"
    }
}
