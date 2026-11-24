package fr.familia.app.feature.trips.enrich

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.feature.trips.detail.TripDetailUiState
import fr.familia.app.feature.trips.detail.TripDetailViewModel
import fr.familia.app.feature.trips.detail.placeName
import fr.familia.app.feature.trips.detail.tripTimeline
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** « Enrichir avec une IA » of [tripId] (récit 106), for those who go. */
@Composable
fun TripEnrichRoute(container: AppContainer?, currentMember: Member, tripId: String, onBack: () -> Unit) {
    val detail: TripDetailViewModel = viewModel(
        key = "trip-enrich-$tripId",
        factory = viewModelFactory {
            initializer { TripDetailViewModel(container?.tripRepository, container?.memberRepository, currentMember.id, tripId) }
        },
    )
    val enrich: TripEnrichViewModel = viewModel(
        key = "trip-enrich-answer-$tripId",
        factory = viewModelFactory {
            initializer { TripEnrichViewModel(container?.tripRepository, container?.tripPlaceRepository, container?.placeLocator, tripId) }
        },
    )
    val trip by detail.uiState.collectAsStateWithLifecycle()
    val state by enrich.state.collectAsStateWithLifecycle()
    val shown = trip as? TripDetailUiState.Success
    val prompt = remember(shown?.trip, shown?.steps, state.target) {
        shown?.let { success -> enrichPrompt(success.trip, success.steps, state.target) }.orEmpty()
    }
    TripEnrichScreen(
        loading = shown == null,
        allowed = shown?.isParticipant == true && container?.tripPlaceRepository != null,
        destinations = shown?.let { success -> tripTimeline(success.trip, success.steps).destinations.map { it.step } }.orEmpty(),
        prompt = prompt,
        state = state,
        onTarget = enrich::setTarget,
        onAnswer = enrich::setAnswer,
        onRead = { shown?.let { enrich.read(it.trip, it.steps) } },
        onToggle = enrich::toggle,
        onAdd = enrich::add,
        onBack = onBack,
    )
}

/** Stateless. [allowed] is false for someone who does not go: they read the trip, they do not add to it. */
@Composable
fun TripEnrichScreen(
    loading: Boolean,
    allowed: Boolean,
    destinations: List<TripStep>,
    prompt: String,
    state: EnrichUiState,
    onTarget: (EnrichTarget) -> Unit,
    onAnswer: (String) -> Unit,
    onRead: () -> Unit,
    onToggle: (String) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by rememberSaveable(prompt) { mutableStateOf(false) }
    Scaffold(topBar = { FamiliaTopBar(title = { Text(stringResource(R.string.enrich_title)) }, navigationIcon = { BackButton(onClick = onBack) }) }) { padding ->
        if (loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(ClaySpacing.screen),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!allowed) {
                Text(stringResource(R.string.enrich_not_allowed), style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Text(stringResource(R.string.enrich_intro), style = MaterialTheme.typography.bodyLarge)

            Heading(stringResource(R.string.enrich_for))
            Column(Modifier.selectableGroup()) {
                Choice(stringResource(R.string.enrich_for_trip), state.target == EnrichTarget.Trip) { onTarget(EnrichTarget.Trip) }
                destinations.forEach { step ->
                    val target = EnrichTarget.Destination(step.id)
                    Choice(stringResource(R.string.enrich_for_destination, placeName(step)), state.target == target) { onTarget(target) }
                }
            }

            Heading(stringResource(R.string.enrich_step_copy))
            Text(
                prompt,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            )
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(prompt))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.enrich_copy)) }
            if (copied) {
                Text(
                    stringResource(R.string.enrich_copied),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            Heading(stringResource(R.string.enrich_step_paste))
            OutlinedTextField(
                value = state.answer,
                onValueChange = onAnswer,
                label = { Text(stringResource(R.string.enrich_answer)) },
                minLines = 4,
                maxLines = 8,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onRead,
                enabled = state.answer.isNotBlank() && !state.saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.enrich_read)) }

            when (val reading = state.reading) {
                null -> Unit
                is EnrichReading.Refused -> Text(
                    stringResource(refusedText(reading)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                is EnrichReading.Read -> Preview(reading, state, onToggle, onAdd)
            }
        }
    }
}

@Composable
private fun Preview(reading: EnrichReading.Read, state: EnrichUiState, onToggle: (String) -> Unit, onAdd: () -> Unit) {
    Heading(stringResource(R.string.enrich_step_check))
    reading.destinations.forEachIndexed { d, destination ->
        if (destination.isNew) {
            Line(
                title = stringResource(R.string.enrich_new_destination, destination.city),
                detail = stringResource(R.string.enrich_dates, day(destination.start), day(destination.end)),
                checked = destinationKey(d) in state.ticked,
                enabled = !state.saving && state.added == null,
                bold = true,
            ) { onToggle(destinationKey(d)) }
        } else {
            Text(destination.city, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        }
        val parentOn = !destination.isNew || destinationKey(d) in state.ticked
        destination.places.forEachIndexed { p, place ->
            Line(
                title = stringResource(R.string.enrich_place, stringResource(kindLabel(place.kind)), place.name),
                detail = listOfNotNull(place.address, place.arriveTime?.let { stringResource(R.string.enrich_at, it.format(HOUR)) }, place.note).joinToString(" · ").ifEmpty { null },
                checked = parentOn && placeKey(d, p) in state.ticked,
                enabled = parentOn && !state.saving && state.added == null,
                modifier = Modifier.padding(start = if (destination.isNew) 24.dp else 0.dp),
            ) { onToggle(placeKey(d, p)) }
        }
    }
    if (reading.skipped.isNotEmpty()) {
        Text(stringResource(R.string.enrich_skipped_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        reading.skipped.forEach { skip ->
            val text = if (skip.reason == SkipReason.MissingName) stringResource(skipText(skip.reason)) else stringResource(skipText(skip.reason), skip.what)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val count = chosenCount(reading, state.ticked)
    val added = state.added
    if (added == null) {
        Button(onClick = onAdd, enabled = count > 0 && !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(if (state.saving) stringResource(R.string.enrich_adding) else pluralStringResource(R.plurals.enrich_add, count, count))
        }
    } else {
        Text(
            pluralStringResource(R.plurals.enrich_added, added, added),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (state.saveFailed) {
        Text(stringResource(R.string.enrich_save_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Line(
    title: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Column(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("H 'h' mm", Locale.FRANCE)
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)

private fun day(date: LocalDate?): String = date?.format(DAY).orEmpty()

private fun kindLabel(kind: TripPlaceKind): Int = when (kind) {
    TripPlaceKind.Lodging -> R.string.enrich_kind_lodging
    TripPlaceKind.Restaurant -> R.string.enrich_kind_restaurant
    TripPlaceKind.Spot -> R.string.enrich_kind_spot
}

private fun refusedText(refused: EnrichReading.Refused): Int = when (refused) {
    EnrichReading.Refused.Empty -> R.string.enrich_refused_empty
    EnrichReading.Refused.TooLong -> R.string.enrich_refused_too_long
    EnrichReading.Refused.NotJson -> R.string.enrich_refused_not_json
    EnrichReading.Refused.NothingToAdd -> R.string.enrich_refused_nothing
}

private fun skipText(reason: SkipReason): Int = when (reason) {
    SkipReason.Link -> R.string.enrich_skip_link
    SkipReason.BadDates -> R.string.enrich_skip_dates
    SkipReason.OutsideTrip -> R.string.enrich_skip_outside
    SkipReason.Overlap -> R.string.enrich_skip_overlap
    SkipReason.MissingName -> R.string.enrich_skip_name
    SkipReason.BadKind -> R.string.enrich_skip_kind
    SkipReason.TooMany -> R.string.enrich_skip_too_many
}
