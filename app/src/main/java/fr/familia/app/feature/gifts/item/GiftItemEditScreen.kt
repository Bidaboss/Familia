package fr.familia.app.feature.gifts.item

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews

/** Form of a gift item (add, edit, remove). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftItemEditScreen(
    state: GiftItemEditUiState,
    onChange: ((GiftItemForm) -> GiftItemForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val hasChanges = (state as? GiftItemEditUiState.Editing)?.hasChanges == true
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
                            if ((state as? GiftItemEditUiState.Editing)?.isNew == false) R.string.gift_item_edit_title else R.string.gift_item_new_title,
                        ),
                    )
                },
                navigationIcon = { BackButton(onClick = { if (hasChanges) confirmLeave = true else onBack() }) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                GiftItemEditUiState.Loading, GiftItemEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                GiftItemEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.CardGiftcard,
                    title = stringResource(R.string.gift_item_unavailable_title),
                    body = stringResource(R.string.gift_item_unavailable_body),
                )
                is GiftItemEditUiState.Editing -> FormContent(state, onChange, onSave, onDelete)
            }
        }
    }
}

@Composable
private fun FormContent(
    state: GiftItemEditUiState.Editing,
    onChange: ((GiftItemForm) -> GiftItemForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val form = state.form
    val errors = state.errors
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.hiddenFromBeneficiary) {
            Text(
                stringResource(R.string.gift_item_hidden_hint, state.beneficiaryName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = form.title,
            onValueChange = { value -> onChange { it.copy(title = value) } },
            label = { Text(stringResource(R.string.gift_item_field_title)) },
            isError = GiftItemFormError.TitleMissing in errors || GiftItemFormError.TitleTooLong in errors,
            supportingText = errorText(errors, GiftItemFormError.TitleMissing, GiftItemFormError.TitleTooLong),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.url,
            onValueChange = { value -> onChange { it.copy(url = value) } },
            label = { Text(stringResource(R.string.gift_item_field_link)) },
            isError = GiftItemFormError.BadLink in errors,
            supportingText = errorText(errors, GiftItemFormError.BadLink),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.price,
            onValueChange = { value -> onChange { it.copy(price = value) } },
            label = { Text(stringResource(R.string.gift_item_field_price)) },
            suffix = { Text("€") },
            isError = GiftItemFormError.BadPrice in errors,
            supportingText = errorText(errors, GiftItemFormError.BadPrice),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.note,
            onValueChange = { value -> onChange { it.copy(note = value) } },
            label = { Text(stringResource(R.string.gift_item_field_note)) },
            placeholder = { Text(stringResource(R.string.gift_item_field_note_hint)) },
            isError = GiftItemFormError.NoteTooLong in errors,
            supportingText = errorText(errors, GiftItemFormError.NoteTooLong),
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
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

@Composable
private fun DeleteButton(title: String, enabled: Boolean, onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { confirm = true },
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.gift_item_delete)) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.gift_item_delete_title, title.trim())) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete() }) {
                    Text(stringResource(R.string.event_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_keep)) } },
        )
    }
}

private fun errorText(errors: Set<GiftItemFormError>, vararg kinds: GiftItemFormError): (@Composable () -> Unit)? {
    val error = kinds.firstOrNull { it in errors } ?: return null
    val res = when (error) {
        GiftItemFormError.TitleMissing -> R.string.error_gift_title_missing
        GiftItemFormError.TitleTooLong -> R.string.error_gift_item_title_too_long
        GiftItemFormError.BadLink -> R.string.error_gift_bad_link
        GiftItemFormError.BadPrice -> R.string.error_gift_bad_price
        GiftItemFormError.NoteTooLong -> R.string.error_gift_note_too_long
    }
    return { Text(stringResource(res), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

/** Fictional form for previews and screenshots: a surprise idea for Inès. */
internal fun previewGiftItemForm(errors: Set<GiftItemFormError> = emptySet()) = GiftItemEditUiState.Editing(
    form = GiftItemForm("Une écharpe rouge", "https://example.com/echarpe", "25", "Laine douce, pas de mohair"),
    isNew = true,
    hiddenFromBeneficiary = true,
    beneficiaryName = "Inès",
    errors = errors,
)

@FamiliaPreviews
@Composable
private fun GiftItemEditScreenPreview() {
    FamiliaTheme { Surface { GiftItemEditScreen(previewGiftItemForm(), {}, {}, {}, {}) } }
}
