package fr.familia.app.feature.gifts.notebook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.form.FormDeleteButton
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.scrollToFirstError
import fr.familia.app.feature.gifts.WishLook
import fr.familia.app.feature.gifts.WishPill
import fr.familia.app.feature.gifts.item.GiftItemFormError
import fr.familia.app.feature.gifts.item.Label
import fr.familia.app.feature.gifts.item.LinkField
import fr.familia.app.feature.gifts.item.LinkPreviewCard
import fr.familia.app.feature.gifts.item.PhotoField
import fr.familia.app.feature.gifts.item.Round
import fr.familia.app.feature.gifts.item.WhiteField
import fr.familia.app.feature.gifts.item.normalizeLink
import fr.familia.app.feature.gifts.item.shownPhoto
import fr.familia.app.feature.gifts.occasionLabel
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp

/**
 * A private idea of the notebook (PRD, récit 9 bis), after the prototype (correctif E11 à E13, fiche
 * K5): « Une idée pour X », the 🔒 that says only I see it, the link with « Coller » and its preview,
 * the name, the price, « Où trouver le cadeau ? », a note for me and « Enregistrer l'idée ». From the
 * notebook itself, the person, the occasion and where it stands are chosen too. Stateless.
 */
@Composable
fun NotebookIdeaScreen(
    state: NotebookIdeaUiState,
    onChange: ((NotebookIdeaForm) -> NotebookIdeaForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    /** « Coller »: the link of the clipboard, whose image, title and price are then read. */
    onPasteLink: (String) -> Unit = { url -> onChange { it.copy(url = url) } },
) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val editing = state as? NotebookIdeaUiState.Editing
    val hasChanges = editing?.hasChanges == true
    val leave: () -> Unit = { if (hasChanges) confirmLeave = true else onBack() }
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
    val forName = editing?.let { personName(it) }
    val title = when {
        editing?.isNew == false -> stringResource(R.string.notebook_idea_edit_title)
        forName != null -> stringResource(R.string.notebook_idea_for_title, forName)
        else -> stringResource(R.string.notebook_idea_new_title)
    }
    Box(Modifier.fillMaxSize().themeBackground()) {
        Column(Modifier.fillMaxSize()) {
            // Back in a white round, the title in the middle (E11), and the « ? » of the screen.
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Round { BackButton(onClick = leave) }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
                )
                Round { HelpButton(ScreenHelp.Notebook) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    NotebookIdeaUiState.Loading, NotebookIdeaUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    NotebookIdeaUiState.Unavailable -> EmptyState(
                        icon = Icons.Outlined.CardGiftcard,
                        title = stringResource(R.string.notebook_idea_unavailable_title),
                        body = stringResource(R.string.notebook_idea_unavailable_body),
                    )
                    is NotebookIdeaUiState.Editing -> FormContent(state, onChange, onDelete, onPasteLink, onSave, leave)
                }
            }
        }
    }
}

/** Who the idea is for, when known: the member chosen, or the first name typed. */
private fun personName(state: NotebookIdeaUiState.Editing): String? =
    state.fixedPerson?.firstName
        ?: state.people.firstOrNull { it.id == state.form.personId }?.firstName
        ?: state.form.otherName.trim().takeIf { it.isNotEmpty() }

/** The parts of the form, in their order on the screen, for the first error. */
private enum class IdeaPart { Person, Link, Idea, Occasion }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormContent(
    state: NotebookIdeaUiState.Editing,
    onChange: ((NotebookIdeaForm) -> NotebookIdeaForm) -> Unit,
    onDelete: () -> Unit,
    onPasteLink: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val form = state.form
    val enabled = !state.isSaving
    val scroll = rememberScrollState()
    val firstError = when {
        state.errors.any { it == NotebookIdeaError.PersonMissing || it == NotebookIdeaError.NameTooLong } -> IdeaPart.Person
        GiftItemFormError.BadLink in state.itemErrors -> IdeaPart.Link
        state.itemErrors.isNotEmpty() -> IdeaPart.Idea
        NotebookIdeaError.BadYear in state.errors -> IdeaPart.Occasion
        else -> null
    }
    val part = scrollToFirstError(scroll, state.errors to state.itemErrors, firstError)
    // From the notebook itself, nobody is chosen yet: the person, the occasion and where it stands.
    val full = state.fixedPerson == null
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LockBox(state)
        if (full) {
            Column(part(IdeaPart.Person), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(stringResource(R.string.notebook_idea_for))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.people.forEach { person ->
                        WishPill(person.firstName, selected = form.personId == person.id, enabled = enabled, onClick = {
                            onChange { it.copy(personId = if (it.personId == person.id) null else person.id, otherName = "") }
                        })
                    }
                }
                WhiteField(
                    value = form.otherName,
                    onValue = { value -> onChange { it.copy(otherName = value, personId = null) } },
                    placeholder = stringResource(R.string.notebook_idea_other),
                    error = when {
                        NotebookIdeaError.PersonMissing in state.errors -> errorLine(R.string.error_notebook_person_missing)
                        NotebookIdeaError.NameTooLong in state.errors -> errorLine(R.string.error_notebook_name_too_long)
                        else -> null
                    },
                )
            }
        }
        Column(part(IdeaPart.Link), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label(stringResource(R.string.wish_form_link))
            LinkField(form.url, state.itemErrors, enabled, onValue = { value -> onChange { it.copy(url = value) } }, onPaste = onPasteLink)
        }
        if (state.imageUrl != null || state.previewLoading || (normalizeLink(form.url) != null && form.title.isNotBlank())) {
            LinkPreviewCard(form.title, form.price, form.url, state.imageUrl, shownPhoto(form.photo, state.storedPhoto))
        }
        if (state.showPhoto) {
            // « Photo (facultative) » (E11): it stays mine, as the idea.
            Label(stringResource(R.string.wish_form_photo))
            PhotoField(form.photo, state.storedPhoto, form.title, hasLink = form.url.isNotBlank(), enabled = enabled) { photo ->
                onChange { it.copy(photo = photo) }
            }
        }
        Column(part(IdeaPart.Idea), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label(stringResource(R.string.notebook_idea_name))
            WhiteField(
                value = form.title,
                onValue = { value -> onChange { it.copy(title = value) } },
                placeholder = stringResource(R.string.wish_form_name_hint),
                error = itemError(state.itemErrors, GiftItemFormError.TitleMissing, GiftItemFormError.TitleTooLong),
                capitalize = true,
            )
            Label(stringResource(R.string.wish_form_price))
            WhiteField(
                value = form.price,
                onValue = { value -> onChange { it.copy(price = value) } },
                placeholder = "—",
                error = itemError(state.itemErrors, GiftItemFormError.BadPrice),
                keyboard = KeyboardType.Decimal,
                suffix = "€",
            )
            Label(stringResource(R.string.wish_form_where))
            WhiteField(
                value = form.whereToFind,
                onValue = { value -> onChange { it.copy(whereToFind = value) } },
                placeholder = stringResource(R.string.wish_form_where_hint),
                error = itemError(state.itemErrors, GiftItemFormError.WhereTooLong),
                capitalize = true,
            )
        }
        if (full) {
            Column(part(IdeaPart.Occasion), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(stringResource(R.string.notebook_idea_occasion))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GiftOccasion.entries.forEach { occasion ->
                        WishPill(occasionLabel(occasion), selected = form.occasion == occasion, enabled = enabled, onClick = {
                            onChange { it.copy(occasion = if (it.occasion == occasion) null else occasion) }
                        })
                    }
                }
                WhiteField(
                    value = form.year,
                    onValue = { value -> onChange { it.copy(year = value) } },
                    placeholder = stringResource(R.string.notebook_idea_year),
                    error = if (NotebookIdeaError.BadYear in state.errors) errorLine(R.string.error_notebook_bad_year) else null,
                    keyboard = KeyboardType.Number,
                )
                Label(stringResource(R.string.notebook_idea_status))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GiftStatus.entries.forEach { status ->
                        WishPill(statusLabel(status), selected = form.status == status, enabled = enabled, onClick = { onChange { it.copy(status = status) } })
                    }
                }
            }
        }
        Label(stringResource(R.string.notebook_idea_note))
        WhiteField(
            value = form.note,
            onValue = { value -> onChange { it.copy(note = value) } },
            placeholder = stringResource(R.string.notebook_idea_note_hint),
            error = itemError(state.itemErrors, GiftItemFormError.NoteTooLong),
            capitalize = true,
            singleLine = false,
        )
        if (state.saveFailed) {
            FormHint(
                stringResource(R.string.gift_save_failed),
                isError = true,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onSave,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = WishLook.Dark, contentColor = Color.White),
            // Full-width actions of Cadeaux: 48 dp (règle 57 of regles-cadeaux-themes-photo.md).
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.notebook_idea_save), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onClick = onCancel,
            enabled = enabled,
            border = BorderStroke(2.dp, WishLook.Edge),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = WishLook.Ink),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (!state.isNew) DeleteButton(form.title, enabled, onDelete)
    }
}

/** The pale yellow 🔒 (E11, E13): nobody but me sees the idea, and in a surprise until I add it. */
@Composable
private fun LockBox(state: NotebookIdeaUiState.Editing) {
    val name = personName(state)
    val text = when {
        state.fromSurprise && name != null -> stringResource(R.string.notebook_idea_lock_surprise, name)
        name != null -> stringResource(R.string.notebook_idea_lock, name)
        else -> stringResource(R.string.notebook_idea_lock_nobody)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = WishLook.InfoYellow, contentColor = WishLook.Ink, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
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
