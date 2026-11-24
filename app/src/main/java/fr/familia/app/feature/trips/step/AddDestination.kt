package fr.familia.app.feature.trips.step

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.TravelPlaceRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.place.Place
import fr.familia.app.core.place.PlaceLocator
import fr.familia.app.core.place.PlaceScale
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.ui.BackButton
import fr.familia.app.feature.trips.carnet.countryName
import fr.familia.app.feature.trips.carnet.flagOf
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.edit.SUGGEST_MIN_CHARS
import fr.familia.app.feature.trips.edit.SUGGEST_PAUSE_MS
import fr.familia.app.feature.trips.edit.destinationName
import fr.familia.app.feature.trips.reminderTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A place offered by « Ajouter une destination » (fiche F2 §3): its name, its point, its country, its size. */
data class DestinationChoice(val name: String, val lat: Double, val lng: Double, val countryCode: String?, val scale: PlaceScale)

/** A place found by the phone as a choice; null without a name. */
internal fun choiceOf(place: Place): DestinationChoice? {
    val name = (place.shortName ?: destinationName(place)).trim().takeIf { it.isNotEmpty() } ?: return null
    return DestinationChoice(name, place.lat, place.lng, place.countryCode, place.scale ?: PlaceScale.City)
}

/** A place the family knows: a country when its name is the name of its country, else a town. */
internal fun knownChoice(name: String, lat: Double, lng: Double, countryCode: String?): DestinationChoice {
    val country = countryCode?.takeIf { it.length == 2 }?.let(::countryName)
    val scale = if (country != null && country.equals(name.trim(), ignoreCase = true)) PlaceScale.Country else PlaceScale.City
    return DestinationChoice(name.trim(), lat, lng, countryCode, scale)
}

/** Each name once (the first one met), in the order given. */
internal fun distinctChoices(choices: List<DestinationChoice>): List<DestinationChoice> =
    choices.filter { it.name.isNotBlank() }.distinctBy { it.name.lowercase(Locale.FRENCH) }

/**
 * What the list shows (F2 point 7): before [SUGGEST_MIN_CHARS] letters the places the family knows,
 * then what the phone found; each time of the size chosen.
 */
internal fun shownChoices(query: String, scale: PlaceScale, known: List<DestinationChoice>, found: List<DestinationChoice>?): List<DestinationChoice> =
    (if (query.trim().length < SUGGEST_MIN_CHARS) known else found.orEmpty()).filter { it.scale == scale }

/**
 * The destination a touch adds (F2 point 7): one night from [day], inside the trip; the nights are
 * then set with − and + on the timeline.
 */
internal fun newDestinationStep(id: String, trip: Trip, day: LocalDate, choice: DestinationChoice): TripStep {
    val lastStart = trip.endDate.minusDays(1).coerceAtLeast(trip.startDate)
    val start = day.coerceIn(trip.startDate, lastStart)
    val end = start.plusDays(1).coerceAtMost(trip.endDate)
    return TripStep(
        id = id,
        type = StepType.Lodging,
        place = choice.name.take(MAX_STEP_PLACE),
        departDate = start,
        departTime = null,
        arriveDate = end.takeIf { it > start },
        arriveTime = null,
        note = null,
        lat = choice.lat,
        lng = choice.lng,
    )
}

data class AddDestinationUiState(
    val query: String = "",
    val scale: PlaceScale = PlaceScale.City,
    val known: List<DestinationChoice> = emptyList(),
    /** Null while nothing is asked; empty when the phone found nothing. */
    val found: List<DestinationChoice>? = null,
    val searching: Boolean = false,
    val adding: Boolean = false,
    val failed: Boolean = false,
    /** The name just added: the screen says it and closes. */
    val added: String? = null,
) {
    val shown: List<DestinationChoice> get() = shownChoices(query, scale, known, found)
    val typing: Boolean get() = query.trim().length >= SUGGEST_MIN_CHARS
}

/**
 * « Ajouter une destination » (fiche F2 §3): the places the family already went to, or the search of
 * the phone (up to 5, DESIGN-103 A), by towns, regions or countries; a touch adds the destination at
 * [day] in [tripId] for one night.
 */
class AddDestinationViewModel(
    private val trips: TripRepository?,
    private val places: TravelPlaceRepository?,
    private val locator: PlaceLocator?,
    private val memberId: String,
    private val tripId: String,
    private val day: LocalDate,
    private val writeTimeoutMillis: Long = 4_000,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddDestinationUiState())
    val uiState: StateFlow<AddDestinationUiState> = _uiState.asStateFlow()
    private var search: Job? = null

    init {
        viewModelScope.launch { _uiState.update { it.copy(known = loadKnown()) } }
    }

    private suspend fun loadKnown(): List<DestinationChoice> = try {
        withTimeoutOrNull(KNOWN_WAIT_MS) {
            val manual = places?.observePlaces(memberId)?.first().orEmpty()
                .map { knownChoice(it.name, it.lat, it.lng, it.countryCode) }
            val followed = trips?.observeFollowedTrips(memberId)?.first().orEmpty()
                .filter { it.id != tripId }
                .sortedByDescending { it.startDate }
                .take(KNOWN_TRIPS)
            val stays = followed.flatMap { trip -> trips?.observeSteps(trip.id)?.first().orEmpty() }
                .filter { it.type == StepType.Lodging && it.hasPosition }
                .map { step -> step.place.substringBefore(',').trim() to step }
            val codes = HashMap<String, String?>()
            val fromTrips = stays.take(KNOWN_MAX).map { (name, step) ->
                val key = name.lowercase(Locale.FRENCH)
                val code = codes.getOrPut(key) { countryOf(step.lat!!, step.lng!!) }
                knownChoice(name, step.lat!!, step.lng!!, code)
            }
            distinctChoices(manual + fromTrips)
        }.orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private suspend fun countryOf(lat: Double, lng: Double): String? = try {
        withTimeoutOrNull(COUNTRY_WAIT_MS) { locator?.countryCodeOf(lat, lng) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    fun type(text: String) {
        _uiState.update { it.copy(query = text, failed = false) }
        search?.cancel()
        val query = text.trim()
        if (query.length < SUGGEST_MIN_CHARS) {
            _uiState.update { it.copy(found = null, searching = false) }
            return
        }
        search = viewModelScope.launch {
            delay(SUGGEST_PAUSE_MS)
            _uiState.update { it.copy(searching = true) }
            val found = try {
                locator?.suggest(query, SUGGEST_MAX).orEmpty().mapNotNull(::choiceOf)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            // A late answer for an older text is dropped.
            if (_uiState.value.query.trim() == query) _uiState.update { it.copy(found = distinctChoices(found), searching = false) }
        }
    }

    fun choose(scale: PlaceScale) = _uiState.update { it.copy(scale = scale) }

    fun add(choice: DestinationChoice) {
        val repo = trips ?: return
        if (_uiState.value.adding) return
        _uiState.update { it.copy(adding = true, failed = false) }
        viewModelScope.launch {
            val ok = try {
                val trip = repo.getTrip(tripId) ?: throw IllegalStateException("no trip")
                val step = newDestinationStep(repo.newStepId(tripId), trip, day, choice)
                // Offline, Firestore keeps the write and sends it later: the timeline shows it at once.
                withTimeoutOrNull(writeTimeoutMillis) { repo.saveStep(tripId, step, isNew = true, remindAt = reminderTime(step, zone(), now())) }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _uiState.update { if (ok) it.copy(adding = false, added = choice.name) else it.copy(adding = false, failed = true) }
        }
    }

    private companion object {
        const val KNOWN_WAIT_MS = 8_000L
        const val COUNTRY_WAIT_MS = 2_000L
        const val KNOWN_TRIPS = 12
        const val KNOWN_MAX = 20
        const val SUGGEST_MAX = 5
    }
}

@Composable
fun AddDestinationRoute(container: AppContainer?, currentMember: Member, tripId: String, day: LocalDate, onDone: () -> Unit) {
    val viewModel: AddDestinationViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AddDestinationViewModel(container?.tripRepository, container?.travelPlaceRepository, container?.placeLocator, currentMember.id, tripId, day)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val addedText = state.added?.let { stringResource(R.string.add_destination_added, it) }
    LaunchedEffect(addedText) {
        if (addedText != null) {
            Toast.makeText(context, addedText, Toast.LENGTH_SHORT).show()
            onDone()
        }
    }
    AddDestinationScreen(state, viewModel::type, viewModel::choose, viewModel::add, onDone)
}

/** Fiche F2 §3: on the light blue, the round back and the title, the search, « Villes | Régions | Pays », the places. */
@Composable
fun AddDestinationScreen(
    state: AddDestinationUiState,
    onType: (String) -> Unit,
    onScale: (PlaceScale) -> Unit,
    onPick: (DestinationChoice) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(TripPathColors.Sky).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color.White, contentColor = Ink, shadowElevation = 2.dp) { BackButton(onClick = onBack) }
            Text(
                stringResource(R.string.timeline_add_destination),
                fontFamily = Fredoka,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.padding(start = 12.dp).semantics { heading() },
            )
        }
        TextField(
            value = state.query,
            onValueChange = onType,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.add_destination_search), fontSize = 16.sp) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp)) },
            shape = RoundedCornerShape(20.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        )
        ScaleSwitch(state.scale, onScale)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val shown = state.shown
            when {
                state.searching && shown.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 24.dp), color = Ink)
                shown.isEmpty() -> Text(
                    stringResource(
                        when {
                            state.typing && state.found != null -> R.string.add_destination_none
                            state.typing -> R.string.add_destination_searching
                            else -> R.string.add_destination_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink,
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(shown, key = { "${it.name}-${it.lat}-${it.lng}" }) { choice ->
                        ChoiceRow(choice, enabled = !state.adding, onClick = { onPick(choice) })
                    }
                }
            }
            if (state.adding) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Ink)
        }
        if (state.failed) {
            Text(stringResource(R.string.add_destination_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** « Villes | Régions | Pays »: a white pill 42 dp, the chosen one dark; each 56 dp to touch. */
@Composable
private fun ScaleSwitch(scale: PlaceScale, onScale: (PlaceScale) -> Unit) {
    Row(Modifier.heightIn(min = 56.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clip(RoundedCornerShape(22.dp)).background(Color.White).padding(4.dp)) {
            listOf(
                PlaceScale.City to R.string.add_destination_cities,
                PlaceScale.Region to R.string.add_destination_regions,
                PlaceScale.Country to R.string.add_destination_countries,
            ).forEach { (value, label) ->
                val chosen = value == scale
                Box(
                    Modifier
                        .heightIn(min = 34.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (chosen) Dark else Color.White)
                        .selectable(selected = chosen, role = Role.Tab, onClick = { onScale(value) })
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(label), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (chosen) Color.White else Ink)
                }
            }
        }
    }
}

/** A white row 56 dp, rounded 16: the round flag of the country, the name. */
@Composable
private fun ChoiceRow(choice: DestinationChoice, enabled: Boolean, onClick: () -> Unit) {
    val country = choice.countryCode?.let(::countryName)
    val spoken = listOfNotNull(choice.name, country?.takeIf { !it.equals(choice.name, ignoreCase = true) }).joinToString(", ")
    val addLabel = stringResource(R.string.add_destination_add_a11y)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = addLabel, onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val flag = choice.countryCode?.let(::flagOf).orEmpty()
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFEFF3F6)), contentAlignment = Alignment.Center) {
            if (flag.isNotEmpty()) Text(flag, fontSize = 22.sp)
        }
        Text(
            choice.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private val Ink = Color(0xFF17333F)
private val Dark = Color(0xFF1E1B18)
