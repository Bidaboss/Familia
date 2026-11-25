package fr.familia.app.feature.gifts.surprise

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.OpenSurprise
import fr.familia.app.core.model.SurpriseOccasion
import fr.familia.app.core.model.ageOn
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.HouseholdMemberPicker
import fr.familia.app.core.ui.PickGroup
import fr.familia.app.core.ui.form.ChipGroup
import fr.familia.app.core.ui.form.FormChip
import fr.familia.app.core.ui.form.FormDeleteButton
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.feature.gifts.giftAccent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The yellow of information of the fiches Cadeaux (§0): background and text. */
private val NoticeBackground = Color(0xFFFFF3C4)
private val NoticeText = Color(0xFF5A3A12)

/**
 * « Nouvelle surprise » / « Modifier la surprise » (brief §3, fiche C3): title, occasion, date, the
 * yellow message that the person never sees it, then « Qui la voit » by household. Stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurpriseEditScreen(
    state: SurpriseEditUiState,
    onChange: ((SurpriseForm) -> SurpriseForm) -> Unit,
    onSave: (suggestedTitle: String) -> Unit,
    onDelete: () -> Unit,
    onJoin: (String) -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val editing = state as? SurpriseEditUiState.Editing
    val hasChanges = editing?.hasChanges == true
    BackHandler(enabled = hasChanges) { confirmLeave = true }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.event_leave_title)) },
            text = { Text(stringResource(R.string.event_leave_body)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.member_leave_keep)) } },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) { Text(stringResource(R.string.member_leave_discard)) }
            },
        )
    }
    val suggested = editing?.let { suggestedTitle(it.form, it.beneficiary) }.orEmpty()
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        topBar = {
            FamiliaTopBar(
                title = {
                    Text(
                        stringResource(if (editing?.isNew == false) R.string.surprise_edit_title else R.string.surprise_new_title),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton(onClick = { if (hasChanges) confirmLeave = true else onBack() }) },
            )
        },
        bottomBar = {
            editing?.let { StickyActionBar(text = stringResource(R.string.member_save), onClick = { onSave(suggested) }, busy = it.isSaving) }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                SurpriseEditUiState.Loading, is SurpriseEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                SurpriseEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.CardGiftcard,
                    title = stringResource(R.string.surprise_unavailable_title),
                    body = stringResource(R.string.surprise_unavailable_body),
                )
                is SurpriseEditUiState.Editing -> FormContent(state, suggested, onChange, onDelete, onJoin, onOpen)
            }
        }
    }
}

/** The title the field suggests, and the one saved when it stays empty. */
@Composable
internal fun suggestedTitle(form: SurpriseForm, beneficiary: Member): String {
    val name = beneficiary.firstName
    return when (form.occasion) {
        SurpriseOccasion.Birthday -> form.date?.let { ageOn(beneficiary, it) }
            ?.let { stringResource(R.string.surprise_title_birthday, it, name) }
            ?: stringResource(R.string.surprise_title_birthday_plain, name)
        SurpriseOccasion.Christmas -> stringResource(R.string.surprise_title_christmas, name)
        SurpriseOccasion.Other -> stringResource(R.string.surprise_title_other, name)
    }
}

@Composable
private fun FormContent(
    state: SurpriseEditUiState.Editing,
    suggested: String,
    onChange: ((SurpriseForm) -> SurpriseForm) -> Unit,
    onDelete: () -> Unit,
    onJoin: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val form = state.form
    val accent = giftAccent()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.isNew) {
            state.mine.forEach { surprise -> AlreadyThere(state.beneficiary, surprise.title, null, stringResource(R.string.surprise_open)) { onOpen(surprise.id) } }
            state.joinable.forEach { open -> AlreadyThere(state.beneficiary, open.title, open, stringResource(R.string.surprise_join), !state.isSaving) { onJoin(open.id) } }
            if (state.joinFailed) {
                FormHint(stringResource(R.string.surprise_join_failed), isError = true, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }

        FormTile(Icons.Outlined.Edit, stringResource(R.string.surprise_field_title), accent = accent) {
            OutlinedTextField(
                value = form.title,
                onValueChange = { value -> onChange { it.copy(title = value) } },
                placeholder = { Text(suggested, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                isError = SurpriseFormError.TitleTooLong in state.errors,
                supportingText = if (SurpriseFormError.TitleTooLong in state.errors) {
                    { Text(stringResource(R.string.surprise_error_title_long), color = MaterialTheme.colorScheme.error) }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FormTile(Icons.Outlined.Celebration, stringResource(R.string.surprise_field_occasion), accent = accent) {
            ChipGroup {
                SurpriseOccasion.entries.forEach { occasion ->
                    FormChip(
                        label = occasionLabel(occasion),
                        selected = form.occasion == occasion,
                        onClick = { onChange { it.copy(occasion = occasion) } },
                    )
                }
            }
        }

        FormTile(Icons.Outlined.CalendarMonth, stringResource(R.string.surprise_field_date), accent = accent) {
            DateField(form.date, dateError(state.errors)) { date -> onChange { it.copy(date = date, dateTouched = true) } }
        }

        Notice(stringResource(R.string.surprise_never_sees, state.beneficiary.firstName))

        FormTile(Icons.Outlined.Visibility, stringResource(R.string.surprise_field_members), accent = accent) {
            Members(state, onChange)
        }

        if (state.saveFailed) {
            FormHint(stringResource(R.string.surprise_save_failed), isError = true, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (!state.isNew) DeleteButton(form.title, !state.isSaving, onDelete)
    }
}

@Composable
internal fun occasionLabel(occasion: SurpriseOccasion): String = stringResource(
    when (occasion) {
        SurpriseOccasion.Christmas -> R.string.surprise_occasion_christmas
        SurpriseOccasion.Birthday -> R.string.surprise_occasion_birthday
        SurpriseOccasion.Other -> R.string.surprise_occasion_other
    },
)

@Composable
private fun dateError(errors: Set<SurpriseFormError>): String? = when {
    SurpriseFormError.DateMissing in errors -> stringResource(R.string.surprise_error_date_missing)
    SurpriseFormError.DatePast in errors -> stringResource(R.string.surprise_error_date_past)
    else -> null
}

/** The yellow message of the fiche C3: the person never sees the surprise. */
@Composable
private fun Notice(text: String, modifier: Modifier = Modifier) {
    Surface(color = NoticeBackground, shape = RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = NoticeText,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** A surprise already in preparation for the same person, to open (mine) or to join (someone else's). */
@Composable
private fun AlreadyThere(beneficiary: Member, title: String, open: OpenSurprise?, action: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(color = NoticeBackground, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.surprise_exists_title, beneficiary.firstName),
                style = MaterialTheme.typography.titleSmall,
                color = NoticeText,
            )
            Text(
                if (open != null) stringResource(R.string.surprise_exists_body, title, open.creatorName) else stringResource(R.string.surprise_mine_body, title),
                style = MaterialTheme.typography.bodyMedium,
                color = NoticeText,
            )
            Button(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
        }
    }
}

/** Who sees it: the whole family or nobody else, then by household; I stay ticked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Members(state: SurpriseEditUiState.Editing, onChange: ((SurpriseForm) -> SurpriseForm) -> Unit) {
    val locked = setOf(state.meId)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onChange { it.copy(memberIds = state.candidates.map { m -> m.id }.toSet()) } },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.surprise_members_all)) }
            OutlinedButton(
                onClick = { onChange { it.copy(memberIds = locked) } },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.surprise_members_none)) }
        }
        HouseholdMemberPicker(
            groups = memberGroups(state.households, state.candidates),
            selected = state.form.memberIds + locked,
            onChange = { picked -> onChange { it.copy(memberIds = picked + locked) } },
        )
    }
}

@Composable
private fun memberGroups(households: List<Household>, candidates: List<Member>): List<PickGroup> {
    val known = households.map { it.id }.toSet()
    val groups = households.map { household -> PickGroup(household.name, candidates.filter { it.householdId == household.id }) }
    val others = PickGroup(stringResource(R.string.members_other_household), candidates.filter { it.householdId !in known })
    return (groups + others).filter { it.members.isNotEmpty() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate?, error: String?, onPick: (LocalDate) -> Unit) {
    var picker by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { picker = true },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(date?.format(dateFormatter) ?: stringResource(R.string.surprise_pick_date), fontWeight = FontWeight.Bold)
        }
        error?.let { FormHint(it, isError = true) }
    }
    if (picker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: familyToday()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun DeleteButton(title: String, enabled: Boolean, onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    FormDeleteButton(stringResource(R.string.surprise_delete), onClick = { confirm = true }, enabled = enabled)
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.surprise_delete_title, title.trim())) },
            text = { Text(stringResource(R.string.surprise_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete() }) {
                    Text(stringResource(R.string.event_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_keep)) } },
        )
    }
}

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.FRANCE)

/** Fictional form for previews and screenshots: a surprise for Inès, with one already started by Jules. */
internal fun previewSurpriseForm(): SurpriseEditUiState.Editing {
    fun member(id: String, name: String, household: String, birth: LocalDate? = null) =
        Member(id, name, null, birth, household, MemberProfile.Adult, false, emptyList(), null)
    val me = member("a", "Camille", "h1")
    val ines = member("b", "Inès", "h2", LocalDate.of(1987, 2, 3))
    val jules = member("c", "Jules", "h2")
    return SurpriseEditUiState.Editing(
        form = SurpriseForm(occasion = SurpriseOccasion.Birthday, date = LocalDate.of(2027, 2, 3), memberIds = setOf("a", "c")),
        isNew = true,
        beneficiary = ines,
        meId = "a",
        candidates = listOf(me, jules),
        households = listOf(Household("h1", "Foyer Martin"), Household("h2", "Foyer Durand")),
        joinable = listOf(OpenSurprise("s1", "b", "Les 40 ans d'Inès", "Jules")),
    )
}

@FamiliaPreviews
@Composable
private fun SurpriseEditScreenPreview() {
    FamiliaTheme { Surface { SurpriseEditScreen(previewSurpriseForm(), {}, {}, {}, {}, {}, {}) } }
}
