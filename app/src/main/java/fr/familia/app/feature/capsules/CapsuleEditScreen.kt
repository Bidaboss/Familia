package fr.familia.app.feature.capsules

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.familia.app.R
import fr.familia.app.core.model.Capsule
import fr.familia.app.core.photo.keepPhotoAccess
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.PickGroup
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.PeopleRow
import fr.familia.app.core.ui.form.PersonChoice
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.core.ui.householdState
import fr.familia.app.core.ui.toggleHousehold
import fr.familia.app.core.ui.toggleMember
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** What the screen can ask of its ViewModel. */
data class CapsuleEditActions(
    val onText: (String) -> Unit = {},
    val onToggleRecipient: (String) -> Unit = {},
    val onRecipients: (Set<String>) -> Unit = {},
    val onOpenAt: (LocalDate) -> Unit = {},
    val onAddPhotos: (List<String>) -> Unit = {},
    val onRemovePhoto: (String) -> Unit = {},
    val onSeal: () -> Unit = {},
)

/** « Nouvelle capsule »: the message, its photos, who it is for, and when it opens. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapsuleEditScreen(state: CapsuleEditUiState, actions: CapsuleEditActions, onBack: () -> Unit) {
    val editing = state as? CapsuleEditUiState.Editing
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.capsules_add), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { HelpButton(ScreenHelp.Capsules) },
            )
        },
        // The single black button at the bottom (refonte, recette commune n° 7).
        bottomBar = {
            if (editing != null) {
                StickyActionBar(
                    text = stringResource(R.string.capsule_edit_seal),
                    onClick = actions.onSeal,
                    enabled = !editing.isSaving,
                    busy = editing.isSaving,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                CapsuleEditUiState.Loading, is CapsuleEditUiState.Done -> LoadingState(Modifier.align(Alignment.Center))
                CapsuleEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.HourglassEmpty,
                    title = stringResource(R.string.capsules_error_title),
                    body = stringResource(R.string.capsules_error_body),
                )
                is CapsuleEditUiState.Editing -> Form(state, actions)
            }
        }
    }
}

/** The capsule in tiles (refonte lot 4): the message, the photos, for whom, when it opens. */
@Composable
private fun Form(state: CapsuleEditUiState.Editing, actions: CapsuleEditActions) {
    val form = state.form
    val enabled = !state.isSaving
    val scroll = rememberScrollState()
    // Where each tile starts, to bring the first one in error into view (recette commune n° 3).
    val tops = remember { mutableStateMapOf<CapsuleTile, Int>() }
    LaunchedEffect(state.errors) {
        val first = CapsuleTile.entries.firstOrNull { tile -> state.errors.any { it.tile == tile } } ?: return@LaunchedEffect
        tops[first]?.let { scroll.animateScrollTo(it) }
    }
    fun Modifier.top(tile: CapsuleTile) = onGloballyPositioned { tops[tile] = it.positionInParent().y.toInt() }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FormTile(Icons.Outlined.EditNote, stringResource(R.string.capsule_edit_text), Modifier.top(CapsuleTile.Text)) {
            val label = stringResource(R.string.capsule_edit_text)
            val missing = CapsuleFormError.TextMissing in state.errors
            OutlinedTextField(
                value = form.text,
                onValueChange = actions.onText,
                isError = missing,
                minLines = 5,
                enabled = enabled,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
            FormHint(stringResource(R.string.capsule_edit_count, form.text.length, Capsule.MAX_TEXT), isError = missing)
            // The photos go with the message: no title of their own.
            Photos(state, actions)
        }
        FormTile(Icons.Outlined.Groups, stringResource(R.string.capsule_edit_who), Modifier.top(CapsuleTile.Who)) {
            val toggle: (String) -> Unit = { id -> if (enabled) actions.onToggleRecipient(id) }
            if (state.groups.isNotEmpty()) {
                // By household (règle de Kévin, 17/11): the household box ticks all of it.
                Faces(state.groups, form.recipientIds, enabled, actions.onRecipients)
            } else {
                PeopleRow(
                    choices = state.members.map { PersonChoice(it.id, it.firstName, selected = it.id in form.recipientIds, memberId = it.id) },
                    multiple = true,
                    onToggle = toggle,
                )
            }
            if (CapsuleFormError.NoRecipient in state.errors) FormHint(stringResource(R.string.capsule_edit_no_recipient), isError = true)
        }
        FormTile(Icons.Outlined.Event, stringResource(R.string.capsule_edit_when), Modifier.top(CapsuleTile.When)) {
            val first = Capsule.earliestOpening(state.today)
            val last = Capsule.latestOpening(state.today)
            val wrong = CapsuleFormError.DateMissing in state.errors || CapsuleFormError.DateOutOfRange in state.errors
            OpenDayBox(form.openAt, first, last, enabled, wrong, actions.onOpenAt)
            if (wrong) FormHint(stringResource(R.string.capsule_edit_no_date), isError = true)
            FormHint(stringResource(R.string.capsule_edit_when_hint, capsuleDay(first), capsuleDay(last)))
            FormHint(stringResource(R.string.capsule_edit_sealed_note))
        }
        state.failure?.let { failure ->
            Surface(shape = RoundedCornerShape(16.dp), color = LocalClayColors.current.card) {
                FormHint(
                    stringResource(
                        when (failure) {
                            CapsuleSaveFailure.NeedsNetwork -> R.string.capsule_edit_needs_network
                            CapsuleSaveFailure.PhotoUnreadable -> R.string.capsule_edit_photo_unreadable
                            CapsuleSaveFailure.Failed -> R.string.capsule_edit_failed
                        },
                    ),
                    isError = true,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        state.sendingPhoto?.let { n ->
            // Text only: a moving bar would freeze the screenshots.
            Text(
                if (n == 0) stringResource(R.string.capsule_edit_sealing) else stringResource(R.string.capsule_edit_sending_photo, n, form.photos.size),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The tiles that can hold an error, top to bottom. */
private enum class CapsuleTile { Text, Who, When }

private val CapsuleFormError.tile: CapsuleTile
    get() = when (this) {
        CapsuleFormError.TextMissing -> CapsuleTile.Text
        CapsuleFormError.NoRecipient -> CapsuleTile.Who
        CapsuleFormError.DateMissing, CapsuleFormError.DateOutOfRange -> CapsuleTile.When
    }

/** Each household: its box, which ticks all of it, then a round per member. */
@Composable
private fun Faces(groups: List<PickGroup>, selected: Set<String>, enabled: Boolean, onChange: (Set<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        groups.forEach { group ->
            val ids = group.members.map { it.id }
            val state = householdState(selected, ids)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .triStateToggleable(state = state, enabled = enabled && ids.isNotEmpty(), role = Role.Checkbox) { onChange(toggleHousehold(selected, ids)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TriStateCheckbox(state = state, onClick = null, enabled = enabled && ids.isNotEmpty())
                Spacer(Modifier.width(12.dp))
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            PeopleRow(
                choices = group.members.map { PersonChoice(it.id, it.firstName, selected = it.id in selected, memberId = it.id) },
                multiple = true,
                onToggle = { id -> if (enabled) onChange(toggleMember(selected, id)) },
            )
        }
    }
}

@Composable
private fun Photos(state: CapsuleEditUiState.Editing, actions: CapsuleEditActions) {
    // The Android photo picker: no permission to ask, and it works on every phone of the family.
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(Capsule.MAX_PHOTOS)) { uris ->
        keepPhotoAccess(context, uris)
        actions.onAddPhotos(uris.map { it.toString() })
    }
    state.form.photos.forEachIndexed { index, uri ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = uri,
                contentDescription = stringResource(R.string.capsule_photo, index + 1, state.form.photos.size),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (!state.isSaving) {
                OutlinedButton(onClick = { actions.onRemovePhoto(uri) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.capsule_edit_photo_remove, index + 1))
                }
            }
        }
    }
    if (state.canAddPhotos && !state.isSaving) {
        OutlinedButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.capsule_edit_add_photos, Capsule.MAX_PHOTOS - state.form.photos.size))
        }
    }
}

/** Opens a calendar where only the days the rules accept can be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenDayBox(day: LocalDate?, first: LocalDate, last: LocalDate, enabled: Boolean, isError: Boolean, onPick: (LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    // A box like the other days of the forms (refonte lot 4), not a « Choisir » button.
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.5.dp, if (isError) colors.error else if (day != null) colors.outline else colors.outlineVariant, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = { open = true })
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            day?.let { stringResource(R.string.capsule_opens_on, capsuleDay(it)) } ?: stringResource(R.string.capsule_edit_pick_day),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (day != null) colors.onSurface else colors.onSurfaceVariant,
        )
    }
    if (open) {
        val firstMillis = first.toUtcMillis()
        val lastMillis = last.toUtcMillis()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (day ?: first.plusMonths(11)).toUtcMillis(),
            yearRange = first.year..last.year,
            selectableDates = remember(firstMillis, lastMillis) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in firstMillis..lastMillis
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** A half-written capsule of Camille for Inès, for previews and screenshots: no photo to load. */
internal fun previewCapsuleEdit(): CapsuleEditUiState.Editing = CapsuleEditUiState.Editing(
    form = CapsuleForm(text = "Un petit mot pour Noël prochain.", recipientIds = setOf("b"), openAt = LocalDate.of(2027, 12, 24)),
    today = LocalDate.of(2026, 11, 16),
    members = listOf(RecipientChoice("a", "Camille"), RecipientChoice("b", "Inès"), RecipientChoice("c", "Noé"), RecipientChoice("d", "Lou")),
)

@FamiliaPreviews
@Composable
private fun CapsuleEditPreview() {
    FamiliaTheme { Surface { CapsuleEditScreen(previewCapsuleEdit(), CapsuleEditActions(), {}) } }
}
