package fr.familia.app.feature.gifts.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.gifts.occasionLabel
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Form of a gift list for an occasion (create, edit, delete). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftListEditScreen(
    state: GiftListEditUiState,
    onChange: ((GiftListForm) -> GiftListForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val hasChanges = (state as? GiftListEditUiState.Editing)?.hasChanges == true
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if ((state as? GiftListEditUiState.Editing)?.isNew == false) R.string.gift_list_edit_title else R.string.gift_list_new_title,
                        ),
                    )
                },
                navigationIcon = { BackButton(onClick = { if (hasChanges) confirmLeave = true else onBack() }) },
                actions = { HelpButton(ScreenHelp.GiftListEdit) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                GiftListEditUiState.Loading, GiftListEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                GiftListEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.CardGiftcard,
                    title = stringResource(R.string.gift_list_missing_title),
                    body = stringResource(R.string.gift_list_unavailable_body),
                )
                is GiftListEditUiState.Editing -> FormContent(state, onChange, onSave, onDelete)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormContent(
    state: GiftListEditUiState.Editing,
    onChange: ((GiftListForm) -> GiftListForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val form = state.form
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        OutlinedTextField(
            value = form.title,
            onValueChange = { value -> onChange { it.copy(title = value) } },
            label = { Text(stringResource(R.string.gift_list_field_title)) },
            placeholder = { Text(stringResource(R.string.gift_list_field_title_hint)) },
            isError = GiftListFormError.TitleMissing in state.errors || GiftListFormError.TitleTooLong in state.errors,
            supportingText = titleError(state.errors),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle(R.string.gift_list_field_occasion)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GiftOccasion.entries.forEach { occasion ->
                FilterChip(
                    selected = form.occasion == occasion,
                    onClick = { onChange { it.copy(occasion = occasion) } },
                    label = { Text(occasionLabel(occasion), style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }

        DateField(form.date, GiftListFormError.DateMissing in state.errors) { date -> onChange { it.copy(date = date) } }

        SectionTitle(R.string.gift_list_field_for_whom)
        if (state.isNew) {
            Column(Modifier.selectableGroup()) {
                state.beneficiaries.forEach { choice ->
                    val selected = form.beneficiaryId == choice.member.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(selected = selected, role = Role.RadioButton) {
                                onChange { it.copy(beneficiaryId = choice.member.id) }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(beneficiaryLabel(choice), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        } else {
            state.beneficiary?.let { Text(beneficiaryLabel(it), style = MaterialTheme.typography.bodyLarge) }
        }
        if (state.beneficiary?.surprise == true) {
            Text(
                stringResource(R.string.gift_list_surprise_hint, state.beneficiary?.member?.firstName.orEmpty()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Viewers(state, onChange)

        if (state.saveFailed) {
            Text(stringResource(R.string.gift_save_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            if (state.isSaving) {
                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.member_save))
            }
        }
        if (!state.isNew) DeleteButton(form.title, !state.isSaving, onDelete)
    }
}

/** Who sees the list: shortcuts for the whole family and each household, then one box per member. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Viewers(state: GiftListEditUiState.Editing, onChange: ((GiftListForm) -> GiftListForm) -> Unit) {
    val surprise = state.beneficiary?.surprise == true
    val beneficiaryId = state.form.beneficiaryId
    // The beneficiary of a surprise list is never offered.
    val candidates = state.members.filter { !surprise || it.id != beneficiaryId }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(R.string.gift_list_field_viewers)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onChange { form -> form.copy(viewerIds = candidates.map { it.id }.toSet()) } },
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.event_for_family)) }
            state.households.forEach { household ->
                OutlinedButton(
                    onClick = {
                        onChange { form ->
                            form.copy(viewerIds = form.viewerIds + candidates.filter { it.householdId == household.id }.map { it.id })
                        }
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.gift_list_add_household, household.name)) }
            }
            OutlinedButton(
                onClick = { onChange { form -> form.copy(viewerIds = emptySet()) } },
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.gift_list_viewers_none)) }
        }
        candidates.forEach { member ->
            val locked = member.id in state.locked
            val checked = locked || member.id in state.form.viewerIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(value = checked, enabled = !locked, role = Role.Checkbox) { on ->
                        onChange { form -> form.copy(viewerIds = if (on) form.viewerIds + member.id else form.viewerIds - member.id) }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = !locked)
                Spacer(Modifier.width(16.dp))
                Text(member.firstName, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun beneficiaryLabel(choice: BeneficiaryChoice): String = when {
    choice.isMe -> stringResource(R.string.gift_list_for_me)
    choice.surprise -> stringResource(R.string.gift_list_for_surprise, choice.member.firstName)
    else -> choice.member.firstName
}

@Composable
private fun SectionTitle(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

private fun titleError(errors: Set<GiftListFormError>): (@Composable () -> Unit)? {
    val res = when {
        GiftListFormError.TitleMissing in errors -> R.string.error_gift_title_missing
        GiftListFormError.TitleTooLong in errors -> R.string.error_gift_list_title_too_long
        else -> return null
    }
    return { ErrorText(res) }
}

@Composable
private fun ErrorText(res: Int) {
    Text(stringResource(res), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate?, missing: Boolean, onPick: (LocalDate) -> Unit) {
    var picker by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(R.string.gift_list_field_date)
        OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(date?.format(dateFormatter) ?: stringResource(R.string.event_pick_date))
        }
        if (missing) ErrorText(R.string.error_event_date_missing)
    }
    if (picker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
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
    OutlinedButton(
        onClick = { confirm = true },
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.gift_list_delete)) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.gift_list_delete_title, title.trim())) },
            text = { Text(stringResource(R.string.gift_list_delete_body)) },
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

/** Fictional form for previews and screenshots: a surprise list for Inès. */
internal fun previewGiftListForm(): GiftListEditUiState.Editing {
    fun member(id: String, name: String, household: String, profile: MemberProfile = MemberProfile.Adult) =
        Member(id, name, null, null, household, profile, false, emptyList(), null)
    val me = member("a", "Camille", "h1")
    val ines = member("b", "Inès", "h2")
    val jules = member("c", "Jules", "h2")
    return GiftListEditUiState.Editing(
        form = GiftListForm("Les 40 ans d'Inès", GiftOccasion.Birthday, LocalDate.of(2027, 2, 3), "b", setOf("a", "c")),
        isNew = true,
        beneficiaries = listOf(
            BeneficiaryChoice(me, isMe = true, surprise = false),
            BeneficiaryChoice(ines, isMe = false, surprise = true),
            BeneficiaryChoice(jules, isMe = false, surprise = true),
        ),
        members = listOf(me, ines, jules),
        households = listOf(Household("h1", "Foyer Martin"), Household("h2", "Foyer Durand")),
        locked = setOf("a"),
    )
}

@FamiliaPreviews
@Composable
private fun GiftListEditScreenPreview() {
    FamiliaTheme { Surface { GiftListEditScreen(previewGiftListForm(), {}, {}, {}, {}) } }
}
