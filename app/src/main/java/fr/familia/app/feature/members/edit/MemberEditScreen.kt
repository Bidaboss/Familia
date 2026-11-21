package fr.familia.app.feature.members.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.Color
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.form.ChoiceTile
import fr.familia.app.core.ui.form.ChoiceTiles
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormLabel
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.StickyActionBar
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
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
import fr.familia.app.core.ui.FamiliaTopBar
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.R
import fr.familia.app.core.ui.HouseholdMemberPicker
import fr.familia.app.core.ui.pickGroups
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
    today: LocalDate = familyToday(),
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
    val editing = state as? MemberEditUiState.Editing
    Scaffold(
        modifier = Modifier.themeBackground(),
        containerColor = Color.Transparent,
        bottomBar = {
            if (editing != null) {
                StickyActionBar(stringResource(R.string.member_save), onClick = onSave, enabled = !editing.isSaving, busy = editing.isSaving)
            }
        },
        topBar = {
            FamiliaTopBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                state is MemberEditUiState.Added -> R.string.member_added_title
                                (state as? MemberEditUiState.Editing)?.isNew == false -> R.string.member_edit_title
                                else -> R.string.member_add_title
                            },
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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
                MemberEditUiState.Loading, is MemberEditUiState.Saved ->
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
    @Suppress("UNUSED_PARAMETER") onSave: () -> Unit,
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // The avatar and its color in one tile (lot-3-fiche-et-reglages.png): no dialog any more.
        AvatarHeader(form) { index -> onChange { it.copy(colorIndex = index) } }

        FormTile(icon = Icons.Outlined.Group, title = stringResource(R.string.member_tile_who)) {
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
            BirthDateField(form.birthDate, FormError.BirthDateInFuture in errors, today) { date ->
                onChange { it.copy(birthDate = date) }
            }
            FormLabel(stringResource(R.string.member_profile))
            ChoiceTiles(MemberProfile.entries, columns = 3) { profile, tileModifier ->
                ChoiceTile(
                    title = stringResource(profile.labelRes()),
                    selected = form.profile == profile,
                    onClick = { onChange { it.copy(profile = profile) } },
                    modifier = tileModifier,
                )
            }
            if (form.needsEmail) {
                OutlinedTextField(
                    value = form.email,
                    onValueChange = { value -> onChange { it.copy(email = value) } },
                    label = { Text(stringResource(R.string.member_email)) },
                    isError = FormError.EmailInvalid in errors || FormError.EmailTaken in errors,
                    supportingText = errorText(errors, FormError.EmailInvalid, FormError.EmailTaken)
                        ?: if (state.canEditEmail) helpText(stringResource(R.string.member_email_help_optional)) else null,
                    enabled = state.canEditEmail,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // One sentence for every field this member cannot change, instead of one per field.
            if (!state.canEditEmail && form.needsEmail || !state.canEditParents && form.needsParents) {
                FormHint(stringResource(R.string.member_locked_fields))
            }
        }

        HouseholdSection(
            state.households,
            form.householdId,
            errors,
            onChange,
            onAddHousehold = if (state.canAddHousehold) onAddHousehold else null,
        )

        if (form.needsParents) {
            FormTile(icon = Icons.Outlined.FamilyRestroom, title = stringResource(R.string.member_parents)) {
                FormHint(stringResource(R.string.member_parents_help))
                // By household, the child's first; a household ticks its adults (règle de Kévin, 17/11).
                HouseholdMemberPicker(
                    groups = pickGroups(state.adults, state.households, form.householdId),
                    selected = form.parentIds,
                    onChange = { ids -> onChange { it.copy(parentIds = ids) } },
                    enabled = state.canEditParents,
                )
                errorText(errors, FormError.SoleParentOfChild, FormError.ParentsMissing, FormError.TooManyParents)?.invoke()
            }
        }

        if (state.saveFailed) {
            Text(
                stringResource(R.string.member_save_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.canInvite) {
            OutlinedButton(
                onClick = { onInvite(form.firstName.trim(), form.email.trim().lowercase()) },
                colors = ButtonDefaults.outlinedButtonColors(containerColor = LocalClayColors.current.card, contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.invite_send))
            }
        }
        // « Retirer » at the very bottom, with a pale red outline (recette de la refonte, point 7).
        if (state.canRemove) RemoveButton(form.firstName, state.soleParentOf, enabled = !state.isSaving, onRemove)
        if (state.removeOwnerOnly) {
            FormHint(stringResource(R.string.member_remove_owner_only, form.firstName))
        }
    }
}

/** The avatar as it will look, 96 dp, and the colors to choose from under it, in one tile. */
@Composable
private fun AvatarHeader(form: MemberForm, onPickColor: (Int) -> Unit) {
    val preview = form.toMember(id = "preview", uid = null).copy(firstName = form.firstName.ifBlank { "?" })
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MemberAvatar(preview, size = 96.dp)
            ColorChoices(form.colorIndex, onPickColor)
            FormHint(stringResource(R.string.member_color_hint))
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
        colors = ButtonDefaults.outlinedButtonColors(containerColor = LocalClayColors.current.card, contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
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
        body = when {
            state.email != null -> stringResource(R.string.member_added_body_invite)
            state.hasApp -> stringResource(R.string.member_added_body_no_email, state.firstName)
            else -> stringResource(R.string.member_added_body_no_app, state.firstName)
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
private fun HouseholdSection(
    households: List<Household>,
    selectedId: String?,
    errors: Set<FormError>,
    onChange: ((MemberForm) -> MemberForm) -> Unit,
    /** Null for a chef de famille: only the admin principal creates households. */
    onAddHousehold: ((String) -> Unit)?,
) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    FormTile(icon = Icons.Outlined.Home, title = stringResource(R.string.member_tile_household)) {
        ChoiceTiles(households, columns = 2) { household, tileModifier ->
            ChoiceTile(
                title = household.name,
                selected = household.id == selectedId,
                onClick = { onChange { it.copy(householdId = household.id) } },
                modifier = tileModifier,
            )
        }
        if (onAddHousehold != null) {
            OutlinedButton(onClick = { dialogOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
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
    // A box like the other fields: the label, then the date; a tap opens the calendar.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = { pickerOpen = true },
            shape = RoundedCornerShape(12.dp),
            color = LocalClayColors.current.card,
            border = BorderStroke(1.dp, if (inFuture) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
            modifier = Modifier.weight(1f).heightIn(min = 64.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    stringResource(R.string.member_birth_date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    date?.format(dateFormatter) ?: stringResource(R.string.member_birth_date_none),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        if (date != null) {
            TextButton(onClick = { onPick(null) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_clear))
            }
        }
    }
    if (inFuture) errorText(setOf(FormError.BirthDateInFuture), FormError.BirthDateInFuture)?.invoke()
    if (pickerOpen) {
        val todayMillis = today.toUtcMillis()
        // A birth date is typed rather than scrolled to, decades back (audit DESIGN du 14/11);
        // the calendar stays one tap away.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.toUtcMillis(),
            initialDisplayMode = DisplayMode.Input,
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
private fun ColorChoices(selected: Int?, onPick: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().selectableGroup(),
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
