package fr.familia.app.feature.gooddeals.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.GoodDealCategory
import fr.familia.app.core.model.GoodDealKind
import fr.familia.app.core.photo.PhotoField
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.formatDay
import fr.familia.app.feature.gooddeals.MAX_DEAL_DAYS
import fr.familia.app.feature.gooddeals.categoryLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode

/** The form of a bon plan (PRD, récit 45). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoodDealEditScreen(
    state: GoodDealEditUiState,
    onChange: ((GoodDealForm) -> GoodDealForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val editing = state as? GoodDealEditUiState.Editing
                    val address = editing?.kind == GoodDealKind.Address
                    Text(
                        stringResource(
                            when {
                                editing?.isNew == false && address -> R.string.gooddeal_address_edit_title
                                editing?.isNew == false -> R.string.gooddeal_edit_title
                                address -> R.string.gooddeal_address_new_title
                                else -> R.string.gooddeal_new_title
                            },
                        ),
                    )
                },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                GoodDealEditUiState.Loading, GoodDealEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                GoodDealEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.LocalOffer,
                    title = stringResource(R.string.gooddeal_unavailable_title),
                    body = stringResource(R.string.gooddeal_unavailable_body),
                )
                is GoodDealEditUiState.Editing -> Form(state, onChange, onSave)
            }
        }
    }
}

@Composable
private fun Form(state: GoodDealEditUiState.Editing, onChange: ((GoodDealForm) -> GoodDealForm) -> Unit, onSave: () -> Unit) {
    val form = state.form
    val enabled = !state.isSaving
    val address = state.kind == GoodDealKind.Address
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Shared with the whole family: no secret here (récit 45).
        Text(stringResource(R.string.gooddeal_no_secret), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = form.title,
            onValueChange = { v -> onChange { it.copy(title = v.take(80)) } },
            label = { Text(stringResource(if (address) R.string.gooddeal_field_name else R.string.gooddeal_field_title)) },
            isError = GoodDealFormError.TitleMissing in state.errors,
            supportingText = if (GoodDealFormError.TitleMissing in state.errors) {
                { Text(stringResource(R.string.gooddeal_error_title_missing)) }
            } else {
                null
            },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.text,
            onValueChange = { v -> onChange { it.copy(text = v.take(500)) } },
            label = { Text(stringResource(if (address) R.string.gooddeal_field_why else R.string.gooddeal_field_text)) },
            minLines = 2,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        if (address) {
            CategoryChips(form.category, enabled) { category -> onChange { it.copy(category = category) } }
            if (GoodDealFormError.CategoryMissing in state.errors) {
                Text(stringResource(R.string.gooddeal_error_category), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        } else {
            EndDayButton(form.lastDay, state.today, enabled) { day -> onChange { it.copy(lastDay = day) } }
            if (GoodDealFormError.EndInvalid in state.errors) {
                Text(stringResource(R.string.gooddeal_error_end), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        OutlinedTextField(
            value = form.place,
            onValueChange = { v -> onChange { it.copy(place = v.take(80)) } },
            label = { Text(stringResource(if (address) R.string.gooddeal_field_city else R.string.gooddeal_field_place)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.link,
            onValueChange = { v -> onChange { it.copy(link = v.trim().take(500)) } },
            label = { Text(stringResource(R.string.gooddeal_field_link)) },
            isError = GoodDealFormError.LinkInvalid in state.errors,
            supportingText = if (GoodDealFormError.LinkInvalid in state.errors) {
                { Text(stringResource(R.string.gooddeal_error_link)) }
            } else {
                null
            },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        if (address) {
            OutlinedTextField(
                value = form.phone,
                onValueChange = { v -> onChange { it.copy(phone = v.take(20)) } },
                label = { Text(stringResource(R.string.gooddeal_field_phone)) },
                isError = GoodDealFormError.PhoneInvalid in state.errors,
                supportingText = if (GoodDealFormError.PhoneInvalid in state.errors) {
                    { Text(stringResource(R.string.gooddeal_error_phone)) }
                } else {
                    null
                },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PhotoField(stored = state.storedPhoto, edit = form.photo, onEdit = { edit -> onChange { it.copy(photo = edit) } }, enabled = enabled)
        }
        if (state.saveFailed) {
            Text(stringResource(R.string.gooddeal_save_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Button(onClick = onSave, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(if (state.isNew) R.string.gooddeal_publish else R.string.gooddeal_save))
        }
    }
}

/** The six categories of a bonne adresse; one must be picked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(selected: GoodDealCategory?, enabled: Boolean, onPick: (GoodDealCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.gooddeal_field_category), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoodDealCategory.entries.forEach { category ->
                FilterChip(
                    selected = selected == category,
                    onClick = { onPick(category) },
                    label = { Text(categoryLabel(category)) },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
    }
}

/** Opens a calendar from today to 3 months on, the days the rules accept. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndDayButton(day: LocalDate?, today: LocalDate, enabled: Boolean, onPick: (LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(
            day?.let { stringResource(R.string.gooddeal_ends_on, formatDay(it, today).lowercase(Locale.FRANCE)) }
                ?: stringResource(R.string.gooddeal_pick_end),
        )
    }
    if (open) {
        val firstMillis = today.toUtcMillis()
        val lastMillis = today.plusDays(MAX_DEAL_DAYS).toUtcMillis()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (day ?: today).toUtcMillis(),
            yearRange = today.year..today.plusDays(MAX_DEAL_DAYS).year,
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

@FamiliaPreviews
@Composable
private fun GoodDealEditScreenPreview() {
    val today = LocalDate.of(2026, 11, 20)
    FamiliaTheme {
        Surface {
            GoodDealEditScreen(
                GoodDealEditUiState.Editing(GoodDealForm(title = "Fraises à cueillir", lastDay = today.plusDays(7)), isNew = true, today = today),
                {}, {}, {},
            )
        }
    }
}
