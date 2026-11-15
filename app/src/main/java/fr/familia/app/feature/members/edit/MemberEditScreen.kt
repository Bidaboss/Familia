package fr.familia.app.feature.members.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.MemberColors
import fr.familia.app.core.theme.OnMemberColor
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Admin form for one member. Stateless: every change goes through [onChange]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberEditScreen(
    state: MemberEditUiState,
    onChange: ((MemberForm) -> MemberForm) -> Unit,
    onAddHousehold: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    today: LocalDate = LocalDate.now(),
    onRemove: () -> Unit = {},
    onInvite: (firstName: String, email: String) -> Unit = { _, _ -> },
) {
    // Leaving a changed form asks first, so a slip of the thumb does not lose what was typed.
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val hasChanges = (state as? MemberEditUiState.Editing)?.hasChanges == true
    val back: () -> Unit = {
        if (hasChanges) {
            confirmLeave = true
        } else {
            onBack()
        }
    }
    BackHandler(enabled = hasChanges) { confirmLeave = true }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.member_leave_title)) },
            text = { Text(stringResource(R.string.member_leave_body)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.member_leave_keep)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) {
                    Text(stringResource(R.string.member_leave_discard))
                }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                state is MemberEditUiState.Added -> R.string.member_added_title
                                (state as? MemberEditUiState.Editing)?.isNew == false -> R.string.member_edit_title
                                else -> R.string.member_add_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    BackButton(onClick = back)
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                MemberEditUiState.Loading, MemberEditUiState.Saved ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                MemberEditUiState.NotFound -> EmptyState(
                    icon = Icons.Outlined.PersonOff,
                    title = stringResource(R.string.member_not_found_title),
                    body = stringResource(R.string.member_not_found_body),
                )
                is MemberEditUiState.Editing ->
                    MemberFormContent(state, onChange, onAddHousehold, onSave, onRemove, onInvite, today)
                is MemberEditUiState.Added -> AddedContent(state, onInvite, onBack)
            }
        }
    }
}

@Composable
private fun MemberFormContent(
    state: MemberEditUiState.Editing,
    onChange: ((MemberForm) -> MemberForm) -> Unit,
    onAddHousehold: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onInvite: (firstName: String, email: String) -> Unit,
    today: LocalDate,
) {
    val form = state.form
    val errors = state.errors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = form.firstName,
            onValueChange = { value -> onChange { it.copy(firstName = value) } },
            label = { Text(stringResource(R.string.member_first_name)) },
            isError = FormError.FirstNameMissing in errors || FormError.FirstNameTooLong in errors,
            supportingText = errorText(errors, FormError.FirstNameMissing, FormError.FirstNameTooLong),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        Section(stringResource(R.string.member_profile)) {
            Column(Modifier.selectableGroup()) {
                MemberProfile.entries.forEach { profile ->
                    ChoiceRow(
                        label = stringResource(profile.labelRes()),
                        selected = form.profile == profile,
                        onClick = { onChange { it.copy(profile = profile) } },
                    )
                }
            }
        }

        if (form.needsEmail) {
            OutlinedTextField(
                value = form.email,
                onValueChange = { value -> onChange { it.copy(email = value) } },
                label = { Text(stringResource(R.string.member_email)) },
                isError = FormError.EmailInvalid in errors || FormError.EmailTaken in errors,
                supportingText = errorText(errors, FormError.EmailInvalid, FormError.EmailTaken)
                    ?: helpText(
                        stringResource(if (state.canEditEmail) R.string.member_email_help else R.string.member_email_owner_only),
                    ),
                enabled = state.canEditEmail,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HouseholdSection(
            state.households,
            form.householdId,
            errors,
            onChange,
            onAddHousehold = if (state.canAddHousehold) onAddHousehold else null,
        )

        BirthDateField(form.birthDate, FormError.BirthDateInFuture in errors, today) { date ->
            onChange { it.copy(birthDate = date) }
        }

        if (form.needsParents) {
            Section(stringResource(R.string.member_parents)) {
                Text(
                    stringResource(R.string.member_parents_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.canEditParents) {
                    Text(
                        stringResource(R.string.member_parents_owner_only),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.adults.forEach { adult ->
                    val checked = adult.id in form.parentIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(checked, enabled = state.canEditParents, role = Role.Checkbox) { on ->
                                onChange { it.copy(parentIds = if (on) it.parentIds + adult.id else it.parentIds - adult.id) }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = state.canEditParents)
                        Spacer(Modifier.width(16.dp))
                        Text(adult.firstName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                errorText(errors, FormError.SoleParentOfChild, FormError.ParentsMissing, FormError.TooManyParents)?.invoke()
            }
        }

        ColorSection(form.colorIndex) { index -> onChange { it.copy(colorIndex = index) } }

        if (state.saveFailed) {
            Text(
                stringResource(R.string.member_save_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = onSave,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.member_save))
            }
        }
        if (state.canInvite) {
            OutlinedButton(
                onClick = { onInvite(form.firstName.trim(), form.email.trim().lowercase()) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.invite_send))
            }
        }
        if (state.canRemove) RemoveButton(form.firstName, state.soleParentOf, enabled = !state.isSaving, onRemove)
        if (state.removeOwnerOnly) {
            Text(
                stringResource(R.string.member_remove_owner_only, form.firstName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RemoveButton(firstName: String, soleParentOf: List<String>, enabled: Boolean, onRemove: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val blocked = soleParentOf.isNotEmpty()
    OutlinedButton(
        onClick = { confirm = true },
        enabled = enabled && !blocked,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.member_remove)) }
    if (blocked) {
        Text(
            stringResource(R.string.member_remove_blocked, soleParentOf.joinToString(", ")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.member_remove_title, firstName)) },
            text = { Text(stringResource(R.string.member_remove_body, firstName)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onRemove() }) {
                    Text(stringResource(R.string.member_remove_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_keep)) }
            },
        )
    }
}

/** After adding a member: offer to send the invitation right away. */
@Composable
private fun AddedContent(
    state: MemberEditUiState.Added,
    onInvite: (firstName: String, email: String) -> Unit,
    onDone: () -> Unit,
) {
    EmptyState(
        icon = Icons.Outlined.CheckCircle,
        title = stringResource(R.string.member_added_body_title, state.firstName),
        body = if (state.email != null) {
            stringResource(R.string.member_added_body_invite)
        } else {
            stringResource(R.string.member_added_body_no_app, state.firstName)
        },
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.email != null) {
                    Button(onClick = { onInvite(state.firstName, state.email) }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.invite_send))
                    }
                }
                TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_done))
                }
            }
        },
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HouseholdSection(
    households: List<Household>,
    selectedId: String?,
    errors: Set<FormError>,
    onChange: ((MemberForm) -> MemberForm) -> Unit,
    /** Null for a chef de famille: only the admin principal creates households. */
    onAddHousehold: ((String) -> Unit)?,
) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    Section(stringResource(R.string.member_household)) {
        Column(Modifier.selectableGroup()) {
            households.forEach { household ->
                ChoiceRow(household.name, household.id == selectedId) { onChange { it.copy(householdId = household.id) } }
            }
        }
        if (onAddHousehold != null) {
            TextButton(onClick = { dialogOpen = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.member_household_new))
            }
        }
        errorText(errors, FormError.HouseholdMissing)?.invoke()
    }
    if (dialogOpen && onAddHousehold != null) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(stringResource(R.string.member_household_new)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_FIRST_NAME) },
                    label = { Text(stringResource(R.string.member_household_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onAddHousehold(name); dialogOpen = false },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateField(date: LocalDate?, inFuture: Boolean, today: LocalDate, onPick: (LocalDate?) -> Unit) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    Section(stringResource(R.string.member_birth_date)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(date?.format(dateFormatter) ?: stringResource(R.string.member_birth_date_none))
            }
            if (date != null) {
                TextButton(onClick = { onPick(null) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_clear))
                }
            }
        }
        if (inFuture) errorText(setOf(FormError.BirthDateInFuture), FormError.BirthDateInFuture)?.invoke()
    }
    if (pickerOpen) {
        val todayMillis = today.toUtcMillis()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.toUtcMillis(),
            selectableDates = remember(todayMillis) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
                    override fun isSelectableYear(year: Int) = year <= today.year
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPick(it.toUtcDate()) }
                    pickerOpen = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSection(selected: Int?, onPick: (Int) -> Unit) {
    Section(stringResource(R.string.member_color)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            MemberColors.forEachIndexed { index, color ->
                val isSelected = index == selected
                val label = stringResource(R.string.member_color_n, index + 1)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                        )
                        .selectable(selected = isSelected, role = Role.RadioButton) { onPick(index) }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = OnMemberColor)
                }
            }
        }
    }
}

/** Supporting text showing the first of [kinds] present in [errors], or null if none is. */
@Composable
private fun errorText(errors: Set<FormError>, vararg kinds: FormError): (@Composable () -> Unit)? {
    val error = kinds.firstOrNull { it in errors } ?: return null
    val message = stringResource(error.messageRes())
    return { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

private fun helpText(text: String): @Composable () -> Unit = { Text(text) }

private fun FormError.messageRes(): Int = when (this) {
    FormError.FirstNameMissing -> R.string.error_first_name_missing
    FormError.FirstNameTooLong -> R.string.error_first_name_too_long
    FormError.EmailInvalid -> R.string.error_email_invalid
    FormError.EmailTaken -> R.string.error_email_taken
    FormError.HouseholdMissing -> R.string.error_household_missing
    FormError.ParentsMissing -> R.string.error_parents_missing
    FormError.TooManyParents -> R.string.error_too_many_parents
    FormError.SoleParentOfChild -> R.string.error_sole_parent_of_child
    FormError.BirthDateInFuture -> R.string.error_birth_date_future
}

private fun MemberProfile.labelRes(): Int = when (this) {
    MemberProfile.Adult -> R.string.profile_adult
    MemberProfile.ChildWithApp -> R.string.profile_child_with_app
    MemberProfile.ChildWithoutApp -> R.string.profile_child_without_app
}

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.FRANCE)

// The date picker works in UTC milliseconds.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Fictional data for previews and screenshots. */
internal fun previewEditState(errors: Set<FormError> = emptySet()) = MemberEditUiState.Editing(
    form = MemberForm(
        firstName = "Lou",
        profile = MemberProfile.ChildWithApp,
        email = "lou@example.com",
        householdId = "h1",
        birthDate = LocalDate.of(2015, 3, 12),
        parentIds = setOf("a"),
        colorIndex = 4,
    ),
    isNew = true,
    households = listOf(Household("h1", "Foyer Martin"), Household("h2", "Foyer Bernard")),
    adults = listOf(
        Member("a", "Camille", null, null, "h1", MemberProfile.Adult, true, emptyList(), null),
        Member("b", "Sacha", null, null, "h1", MemberProfile.Adult, false, emptyList(), null),
    ),
    errors = errors,
    canAddHousehold = true,
)

/** An existing member who has not signed in yet, and is the only responsible adult of a child. */
internal fun previewExistingState() = previewEditState().copy(
    form = MemberForm(firstName = "Sacha", email = "sacha@example.com", householdId = "h1", colorIndex = 2),
    isNew = false,
    canRemove = true,
    soleParentOf = listOf("Noé"),
    canInvite = true,
)

@FamiliaPreviews
@Composable
private fun MemberEditScreenPreview() {
    FamiliaTheme {
        Surface {
            MemberEditScreen(previewEditState(), {}, {}, {}, {}, today = LocalDate.of(2026, 11, 14))
        }
    }
}

@FamiliaPreviews
@Composable
private fun MemberEditExistingPreview() {
    FamiliaTheme {
        Surface {
            MemberEditScreen(previewExistingState(), {}, {}, {}, {}, today = LocalDate.of(2026, 11, 14))
        }
    }
}

@FamiliaPreviews
@Composable
private fun MemberAddedPreview() {
    FamiliaTheme { Surface { MemberEditScreen(MemberEditUiState.Added("Lou", "lou@example.com"), {}, {}, {}, {}) } }
}
