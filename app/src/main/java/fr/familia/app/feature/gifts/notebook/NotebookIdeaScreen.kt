package fr.familia.app.feature.gifts.notebook

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.form.ChipGroup
import fr.familia.app.core.ui.form.FormChip
import fr.familia.app.core.ui.form.FormDeleteButton
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.core.ui.form.scrollToFirstError
import fr.familia.app.feature.gifts.giftAccent
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
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        bottomBar = {
            editing?.let { StickyActionBar(text = stringResource(R.string.member_save), onClick = onSave, busy = it.isSaving) }
        },
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
                is NotebookIdeaUiState.Editing -> FormContent(state, onChange, onDelete)
            }
        }
    }
}

/** The tiles of the form, in their order on the screen. */
private enum class IdeaPart { Person, Occasion, Idea }

/** The form in tiles (refonte globale, lot 2): same fields, same order, same rules. */
@Composable
private fun FormContent(
    state: NotebookIdeaUiState.Editing,
    onChange: ((NotebookIdeaForm) -> NotebookIdeaForm) -> Unit,
    onDelete: () -> Unit,
) {
    val form = state.form
    val accent = giftAccent()
    val scroll = rememberScrollState()
    val firstError = when {
        state.errors.any { it == NotebookIdeaError.PersonMissing || it == NotebookIdeaError.NameTooLong } -> IdeaPart.Person
        NotebookIdeaError.BadYear in state.errors -> IdeaPart.Occasion
        state.itemErrors.isNotEmpty() -> IdeaPart.Idea
        else -> null
    }
    val part = scrollToFirstError(scroll, state.errors to state.itemErrors, firstError)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).imePadding().padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FormHint(stringResource(R.string.notebook_idea_private))
        FormTile(Icons.Outlined.Person, stringResource(R.string.notebook_idea_for), part(IdeaPart.Person), accent = accent) {
            // From a member's page (retour d'essai du 18/11): that member, and no other choice.
            val fixed = state.fixedPerson
            if (fixed != null) {
                Text(fixed.firstName, style = MaterialTheme.typography.bodyLarge)
            } else {
                ChipGroup {
                    state.people.forEach { person ->
                        FormChip(person.firstName, selected = form.personId == person.id, onClick = {
                            onChange { it.copy(personId = if (it.personId == person.id) null else person.id, otherName = "") }
                        })
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
                    shape = FieldShape,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        FormTile(Icons.Outlined.Celebration, stringResource(R.string.notebook_idea_occasion), part(IdeaPart.Occasion), accent = accent) {
            ChipGroup {
                GiftOccasion.entries.forEach { occasion ->
                    FormChip(occasionLabel(occasion), selected = form.occasion == occasion, onClick = {
                        onChange { it.copy(occasion = if (it.occasion == occasion) null else occasion) }
                    })
                }
            }
            OutlinedTextField(
                value = form.year,
                onValueChange = { value -> onChange { it.copy(year = value) } },
                label = { Text(stringResource(R.string.notebook_idea_year)) },
                isError = NotebookIdeaError.BadYear in state.errors,
                supportingText = if (NotebookIdeaError.BadYear in state.errors) errorLine(R.string.error_notebook_bad_year) else null,
                singleLine = true,
                shape = FieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FormTile(Icons.Outlined.CardGiftcard, stringResource(R.string.notebook_idea_tile), part(IdeaPart.Idea), accent = accent) {
            OutlinedTextField(
                value = form.title,
                onValueChange = { value -> onChange { it.copy(title = value) } },
                label = { Text(stringResource(R.string.gift_item_field_title)) },
                isError = GiftItemFormError.TitleMissing in state.itemErrors || GiftItemFormError.TitleTooLong in state.itemErrors,
                supportingText = itemError(state.itemErrors, GiftItemFormError.TitleMissing, GiftItemFormError.TitleTooLong),
                singleLine = true,
                shape = FieldShape,
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
                shape = FieldShape,
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
                shape = FieldShape,
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
                shape = FieldShape,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FormTile(Icons.Outlined.Checklist, stringResource(R.string.notebook_idea_status), accent = accent) {
            ChipGroup {
                GiftStatus.entries.forEach { status ->
                    FormChip(statusLabel(status), selected = form.status == status, onClick = { onChange { it.copy(status = status) } })
                }
            }
        }
        if (state.saveFailed) {
            FormHint(
                stringResource(R.string.gift_save_failed),
                isError = true,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (!state.isNew) DeleteButton(form.title, !state.isSaving, onDelete)
    }
}

private val FieldShape = RoundedCornerShape(16.dp)

@Composable
private fun DeleteButton(title: String, enabled: Boolean, onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    FormDeleteButton(stringResource(R.string.notebook_idea_delete), onClick = { confirm = true }, enabled = enabled)
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
            GiftItemFormError.WhereTooLong -> R.string.error_gift_where_too_long
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
