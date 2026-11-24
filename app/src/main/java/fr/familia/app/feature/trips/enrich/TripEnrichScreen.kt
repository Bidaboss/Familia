package fr.familia.app.feature.trips.enrich

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.feature.trips.TripFormColors
import fr.familia.app.feature.trips.TripPage
import fr.familia.app.feature.trips.TripPageAction
import fr.familia.app.feature.trips.TripPageCard
import fr.familia.app.feature.trips.TripPageCheck
import fr.familia.app.feature.trips.TripPageField
import fr.familia.app.feature.trips.detail.TripDetailUiState
import fr.familia.app.feature.trips.detail.TripDetailViewModel
import fr.familia.app.feature.trips.detail.TripPathColors
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

/** The three steps of « Enrichir avec une IA » (fiche F6). */
enum class EnrichStep { Copy, Paste, Check }

/** The step shown: the check once an answer is read, else the one the person is on. */
internal fun shownStep(chosen: EnrichStep, reading: EnrichReading?): EnrichStep =
    if (reading is EnrichReading.Read) EnrichStep.Check else if (chosen == EnrichStep.Check) EnrichStep.Paste else chosen

/**
 * Stateless. [allowed] is false for someone who does not go: they read the trip, they do not add to it.
 * Fiche F6: three steps (Copier, Coller, Valider) on the light blue of the trips, the target of the
 * prompt under the title (« Tout le voyage ▾ »), one full button a step.
 */
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
    startStep: EnrichStep = EnrichStep.Copy,
) {
    var chosen by rememberSaveable { mutableStateOf(startStep) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val step = shownStep(chosen, state.reading)
    val target = state.target
    val targetName = when (target) {
        EnrichTarget.Trip -> stringResource(R.string.enrich_for_trip)
        is EnrichTarget.Destination -> destinations.firstOrNull { it.id == target.stepId }?.let(::placeName) ?: stringResource(R.string.enrich_for_trip)
    }
    val reading = state.reading as? EnrichReading.Read
    val action = when {
        loading || !allowed -> null
        step == EnrichStep.Paste -> TripPageAction(stringResource(R.string.enrich_read), onRead, enabled = state.answer.isNotBlank() && !state.saving)
        step == EnrichStep.Check && reading != null && state.added == null -> {
            val count = chosenCount(reading, state.ticked)
            TripPageAction(
                if (state.saving) stringResource(R.string.enrich_adding) else pluralStringResource(R.plurals.enrich_add, count, count),
                onAdd,
                enabled = count > 0 && !state.saving,
                busy = state.saving,
            )
        }
        else -> null
    }
    TripPage(
        title = stringResource(R.string.enrich_title),
        onBack = onBack,
        subtitle = if (allowed && !loading) targetName else null,
        onSubtitle = if (allowed && !loading && destinations.isNotEmpty() && !state.saving) ({ choosing = true }) else null,
        subtitleLabel = stringResource(R.string.enrich_for_change),
        action = action,
    ) {
        if (loading) {
            LoadingState()
            return@TripPage
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!allowed) {
                Text(stringResource(R.string.enrich_not_allowed), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                return@Column
            }
            Steps(step, canCheck = reading != null) { picked -> chosen = picked }
            Text(stringResource(R.string.enrich_intro), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            when (step) {
                EnrichStep.Copy -> CopyStep(prompt) { chosen = EnrichStep.Paste }
                EnrichStep.Paste -> PasteStep(state, onAnswer)
                EnrichStep.Check -> if (reading != null) Preview(reading, state, onToggle)
            }
        }
    }
    if (choosing) {
        TargetChoice(destinations, target, onPick = { picked -> choosing = false; chosen = EnrichStep.Copy; onTarget(picked) }, onDismiss = { choosing = false })
    }
}

/** « 1 Copier — 2 Coller — 3 Valider »: the step shown in ink, the others white; « Valider » once an answer is read. */
@Composable
private fun Steps(step: EnrichStep, canCheck: Boolean, onPick: (EnrichStep) -> Unit) {
    val labels = listOf(
        EnrichStep.Copy to stringResource(R.string.enrich_step_copy),
        EnrichStep.Paste to stringResource(R.string.enrich_step_paste),
        EnrichStep.Check to stringResource(R.string.enrich_step_check),
    )
    Row(Modifier.fillMaxWidth().selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, (each, label) ->
            if (index > 0) Box(Modifier.weight(0.4f).padding(horizontal = 6.dp).height(2.dp).background(StepLine))
            val on = each == step
            Row(
                Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .selectable(selected = on, enabled = each != EnrichStep.Check || canCheck, role = Role.Tab) { onPick(each) }
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.size(28.dp).background(if (on) TripPathColors.Ink else Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${index + 1}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (on) Color.White else TripPathColors.Ink) }
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** Step 1: the white card edged in blue, « Copier le prompt », the prompt to read or change, « J'ai la réponse ». */
@Composable
private fun CopyStep(prompt: String, onNext: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var open by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable(prompt) { mutableStateOf(prompt) }
    var copied by rememberSaveable(prompt) { mutableStateOf(false) }
    TripPageCard(Modifier.border(1.5.dp, TripFormColors.Edge, RoundedCornerShape(26.dp))) {
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).background(TripFormColors.IconBackground, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = TripFormColors.Icon, modifier = Modifier.size(20.dp))
            }
            Text(stringResource(R.string.enrich_copy_title), fontFamily = Fredoka, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        }
        Text(stringResource(R.string.enrich_copy_hint), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Button(
            onClick = {
                clipboard.setText(AnnotatedString(custom))
                copied = true
            },
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TripPathColors.Ink, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.enrich_copy), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
        if (copied) {
            Text(
                stringResource(R.string.enrich_copied),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        TextButton(
            onClick = { open = !open },
            colors = ButtonDefaults.textButtonColors(contentColor = TripPathColors.Ink),
            modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.enrich_show_prompt), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        if (open) {
            TripPageField(
                label = stringResource(R.string.enrich_prompt_label),
                value = custom,
                onValueChange = { custom = it.take(MAX_PROMPT_CHARS) },
                singleLine = false,
                minLines = 6,
                maxLines = 14,
            )
        }
        TextButton(
            onClick = onNext,
            colors = ButtonDefaults.textButtonColors(contentColor = TripPathColors.Ink),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.enrich_have_answer), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** Step 2: the answer pasted, read by « Analyser » at the bottom; a refused answer says why, in red. */
@Composable
private fun PasteStep(state: EnrichUiState, onAnswer: (String) -> Unit) {
    TripPageCard {
        TripPageField(
            label = stringResource(R.string.enrich_answer),
            value = state.answer,
            onValueChange = onAnswer,
            singleLine = false,
            minLines = 6,
            maxLines = 12,
            enabled = !state.saving,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
    val refused = state.reading as? EnrichReading.Refused
    if (refused != null) {
        Text(
            stringResource(refusedText(refused)),
            color = ErrorRed,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Step 3: what the answer proposes, all ticked; what was left out, and why. */
@Composable
private fun Preview(reading: EnrichReading.Read, state: EnrichUiState, onToggle: (String) -> Unit) {
    TripPageCard {
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
                Text(destination.city, fontFamily = Fredoka, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
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
    }
    if (reading.skipped.isNotEmpty()) {
        Text(stringResource(R.string.enrich_skipped_title), fontFamily = Fredoka, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
        reading.skipped.forEach { skip ->
            val text = if (skip.reason == SkipReason.MissingName) stringResource(skipText(skip.reason)) else stringResource(skipText(skip.reason), skip.what)
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TripPathColors.Ink.copy(alpha = 0.8f))
        }
    }
    val added = state.added
    if (added != null) {
        Text(
            pluralStringResource(R.plurals.enrich_added, added, added),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (state.saveFailed) {
        Text(stringResource(R.string.enrich_save_failed), color = ErrorRed, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Who the prompt is for: the whole trip, or one destination. */
@Composable
private fun TargetChoice(destinations: List<TripStep>, current: EnrichTarget, onPick: (EnrichTarget) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enrich_for)) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                Choice(stringResource(R.string.enrich_for_trip), current == EnrichTarget.Trip) { onPick(EnrichTarget.Trip) }
                destinations.forEach { step ->
                    val target = EnrichTarget.Destination(step.id)
                    Choice(stringResource(R.string.enrich_for_destination, placeName(step)), current == target) { onPick(target) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) } },
    )
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
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .alpha(if (enabled || checked) 1f else 0.6f),
    ) {
        TripPageCheck(checked)
        Column(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold)
            if (detail != null) Text(detail, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TripPathColors.Ink.copy(alpha = 0.8f))
        }
    }
}

private val StepLine = Color(0xFFC9D6DD)
private val ErrorRed = Color(0xFFB3261E)
private const val MAX_PROMPT_CHARS = 20_000

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
