package fr.familia.app.feature.gifts.notebook

import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.gifts.item.GiftItemFormError
import fr.familia.app.feature.gifts.occasionLabel
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp

/** A private idea of the notebook: for whom, which occasion, what, and where it stands. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookIdeaScreen(
    state: NotebookIdeaUiState,
    onChange: ((NotebookIdeaForm) -> NotebookIdeaForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val editing = state as? NotebookIdeaUiState.Editing
    val hasChanges = editing?.hasChanges == true
    BackHandler(enabled = hasChanges) { confirmLeave = true }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.event_leave_title)) },
            text = { Text(stringResource(R.string.event_leave_body)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.member_leave_keep)) } },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.member_leave_discard))
                }
            },
        )
    }
    Scaffold(
        topBar = {
            FamiliaTopBar(
                title = {
                    Text(stringResource(if (editing?.isNew == false) R.string.notebook_idea_edit_title else R.string.notebook_idea_new_title), maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { BackButton(onClick = { if (hasChanges) confirmLeave = true else onBack() }) },
                actions = { HelpButton(ScreenHelp.Notebook) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                NotebookIdeaUiState.Loading, NotebookIdeaUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                NotebookIdeaUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.CardGiftcard,
                    title = stringResource(R.string.notebook_idea_unavailable_title),
                    body = stringResource(R.string.notebook_idea_unavailable_body),
                )
                is NotebookIdeaUiState.Editing -> FormContent(state, onChange, onSave, onDelete)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormContent(
    state: NotebookIdeaUiState.Editing,
    onChange: ((NotebookIdeaForm) -> NotebookIdeaForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val form = state.form
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        Text(
            stringResource(R.string.notebook_idea_private),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Section(R.string.notebook_idea_for) {
            // From a member's page (retour d'essai du 18/11): that member, and no other choice.
            val fixed = state.fixedPerson
            if (fixed != null) {
                Text(fixed.firstName, style = MaterialTheme.typography.bodyLarge)
            } else {
                ChipRow {
                    state.people.forEach { person ->
                        Chip(person.firstName, selected = form.personId == person.id) {
                            onChange { it.copy(personId = if (it.personId == person.id) null else person.id, otherName = "") }
                        }
                    }
                }
                OutlinedTextField(
                    value = form.otherName,
                    onValueChange = { value -> onChange { it.copy(otherName = value, personId = null) } },
                    label = { Text(stringResource(R.string.notebook_idea_other)) },
                    isError = state.errors.any { it == NotebookIdeaError.PersonMissing || it == NotebookIdeaError.NameTooLong },
                    supportingText = when {
                        NotebookIdeaError.PersonMissing in state.errors -> errorLine(R.string.error_notebook_person_missing)
                        NotebookIdeaError.NameTooLong in state.errors -> errorLine(R.string.error_notebook_name_too_long)
                        else -> null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Section(R.string.notebook_idea_occasion) {
            ChipRow {
                GiftOccasion.entries.forEach { occasion ->
                    Chip(occasionLabel(occasion), selected = form.occasion == occasion) {
                        onChange { it.copy(occasion = if (it.occasion == occasion) null else occasion) }
                    }
                }
            }
            OutlinedTextField(
                value = form.year,
                onValueChange = { value -> onChange { it.copy(year = value) } },
                label = { Text(stringResource(R.string.notebook_idea_year)) },
                isError = NotebookIdeaError.BadYear in state.errors,
                supportingText = if (NotebookIdeaError.BadYear in state.errors) errorLine(R.string.error_notebook_bad_year) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = form.title,
            onValueChange = { value -> onChange { it.copy(title = value) } },
            label = { Text(stringResource(R.string.gift_item_field_title)) },
            isError = GiftItemFormError.TitleMissing in state.itemErrors || GiftItemFormError.TitleTooLong in state.itemErrors,
            supportingText = itemError(state.itemErrors, GiftItemFormError.TitleMissing, GiftItemFormError.TitleTooLong),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.url,
            onValueChange = { value -> onChange { it.copy(url = value) } },
            label = { Text(stringResource(R.string.gift_item_field_link)) },
            isError = GiftItemFormError.BadLink in state.itemErrors,
            supportingText = itemError(state.itemErrors, GiftItemFormError.BadLink),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.price,
            onValueChange = { value -> onChange { it.copy(price = value) } },
            label = { Text(stringResource(R.string.gift_item_field_price)) },
            suffix = { Text("€") },
            isError = GiftItemFormError.BadPrice in state.itemErrors,
            supportingText = itemError(state.itemErrors, GiftItemFormError.BadPrice),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.note,
            onValueChange = { value -> onChange { it.copy(note = value) } },
            label = { Text(stringResource(R.string.gift_item_field_note)) },
            isError = GiftItemFormError.NoteTooLong in state.itemErrors,
            supportingText = itemError(state.itemErrors, GiftItemFormError.NoteTooLong),
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Section(R.string.notebook_idea_status) {
            ChipRow {
                GiftStatus.entries.forEach { status ->
                    Chip(statusLabel(status), selected = form.status == status) { onChange { it.copy(status = status) } }
                }
            }
        }
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
private fun Section(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = Modifier.heightIn(min = 56.dp))
}

@Composable
private fun DeleteButton(title: String, enabled: Boolean, onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { confirm = true },
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.notebook_idea_delete)) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.notebook_idea_delete_title, title.trim())) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.event_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_keep)) }
            },
        )
    }
}

private fun errorLine(res: Int): @Composable () -> Unit = {
    Text(stringResource(res), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

private fun itemError(errors: Set<GiftItemFormError>, vararg kinds: GiftItemFormError): (@Composable () -> Unit)? {
    val error = kinds.firstOrNull { it in errors } ?: return null
    return errorLine(
        when (error) {
            GiftItemFormError.TitleMissing -> R.string.error_gift_title_missing
            GiftItemFormError.TitleTooLong -> R.string.error_gift_item_title_too_long
            GiftItemFormError.BadLink -> R.string.error_gift_bad_link
            GiftItemFormError.BadPrice -> R.string.error_gift_bad_price
            GiftItemFormError.NoteTooLong -> R.string.error_gift_note_too_long
        },
    )
}

/** Fictional form for previews and screenshots: an idea for someone outside the family. */
internal fun previewNotebookIdea(errors: Set<NotebookIdeaError> = emptySet()): NotebookIdeaUiState.Editing {
    fun member(id: String, name: String) = Member(id, name, null, null, "h1", MemberProfile.Adult, false, emptyList(), null)
    return NotebookIdeaUiState.Editing(
        form = NotebookIdeaForm(
            otherName = "Mamie Jo", occasion = GiftOccasion.Christmas, year = "2026",
            title = "Une écharpe rouge", price = "25", status = GiftStatus.Bought,
        ),
        isNew = true,
        people = listOf(member("b", "Inès"), member("c", "Jules")),
        errors = errors,
    )
}

@FamiliaPreviews
@Composable
private fun NotebookIdeaScreenPreview() {
    FamiliaTheme { Surface { NotebookIdeaScreen(previewNotebookIdea(), {}, {}, {}, {}) } }
}
