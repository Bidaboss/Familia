package fr.familia.app.feature.lending.edit

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
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.LendCategory
import fr.familia.app.core.photo.PhotoField
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.lending.lendCategoryLabel
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode

/** The form of a thing to lend (PRD, récit 51). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LendItemEditScreen(
    state: LendItemEditUiState,
    onChange: ((LendItemForm) -> LendItemForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val isNew = (state as? LendItemEditUiState.Editing)?.isNew != false
                    Text(stringResource(if (isNew) R.string.lend_new_title else R.string.lend_edit_title))
                },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                LendItemEditUiState.Loading, LendItemEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                LendItemEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.Category,
                    title = stringResource(R.string.lend_unavailable_title),
                    body = stringResource(R.string.lend_unavailable_body),
                )
                is LendItemEditUiState.Editing -> Form(state, onChange, onSave)
            }
        }
    }
}

@Composable
private fun Form(state: LendItemEditUiState.Editing, onChange: ((LendItemForm) -> LendItemForm) -> Unit, onSave: () -> Unit) {
    val form = state.form
    val enabled = !state.isSaving
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { v -> onChange { it.copy(name = v.take(LEND_NAME_MAX)) } },
            label = { Text(stringResource(R.string.lend_field_name)) },
            isError = LendFormError.NameMissing in state.errors,
            supportingText = if (LendFormError.NameMissing in state.errors) {
                { Text(stringResource(R.string.lend_error_name)) }
            } else {
                null
            },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        CategoryChips(form.category, enabled) { category -> onChange { it.copy(category = category) } }
        if (LendFormError.CategoryMissing in state.errors) {
            Text(stringResource(R.string.lend_error_category), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(
            value = form.note,
            onValueChange = { v -> onChange { it.copy(note = v.take(LEND_NOTE_MAX)) } },
            label = { Text(stringResource(R.string.lend_field_note)) },
            placeholder = { Text(stringResource(R.string.lend_field_note_hint)) },
            minLines = 2,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        PhotoField(stored = state.storedPhoto, edit = form.photo, onEdit = { edit -> onChange { it.copy(photo = edit) } }, enabled = enabled)
        if (state.saveFailed) {
            Text(stringResource(R.string.lend_save_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Button(onClick = onSave, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(if (state.isNew) R.string.lend_add else R.string.lend_save))
        }
    }
}

/** The six categories; one must be picked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(selected: LendCategory?, enabled: Boolean, onPick: (LendCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.lend_field_category), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LendCategory.entries.forEach { category ->
                FilterChip(
                    selected = selected == category,
                    onClick = { onPick(category) },
                    label = { Text(lendCategoryLabel(category)) },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
    }
}

@FamiliaPreviews
@Composable
private fun LendItemEditScreenPreview() {
    FamiliaTheme {
        Surface {
            LendItemEditScreen(
                LendItemEditUiState.Editing(LendItemForm(name = "Appareil à raclette", category = LendCategory.Kitchen), isNew = true),
                {}, {}, {},
            )
        }
    }
}
