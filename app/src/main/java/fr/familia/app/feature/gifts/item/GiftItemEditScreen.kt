package fr.familia.app.feature.gifts.item

import fr.familia.app.core.photo.PhotoField
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.feature.gifts.wishOccasionLabel
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
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp

/** Form of a gift item (add, edit, remove). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftItemEditScreen(
    state: GiftItemEditUiState,
    onChange: ((GiftItemForm) -> GiftItemForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    /** Récit 50: my wishes (null) or an idea for that member. */
    onChooseTarget: (String?) -> Unit = {},
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton(onClick = { if (hasChanges) confirmLeave = true else onBack() }) },
                actions = { HelpButton(ScreenHelp.GiftItemEdit) },
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
                is GiftItemEditUiState.Editing -> FormContent(state, onChange, onSave, onDelete, onChooseTarget)
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
    onChooseTarget: (String?) -> Unit,
) {
    val form = state.form
    val errors = state.errors
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        if (state.targets.isNotEmpty()) {
            ShareTargetChoice(state.targets, state.targetId, enabled = !state.isSaving, onSelect = onChooseTarget)
        }
        if (state.hiddenFromBeneficiary) {
            Text(
                stringResource(R.string.gift_item_hidden_hint, state.beneficiaryName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.previewLoading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text(stringResource(R.string.gift_item_preview_loading), style = MaterialTheme.typography.bodyLarge)
            }
        }
        state.imageUrl?.let { image ->
            // The picture of the shop page: decorative, the title says what the gift is.
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(MaterialTheme.shapes.medium),
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
        if (state.showOccasion) {
            WishOccasionChoice(form.occasion, state.forMe) { value -> onChange { it.copy(occasion = value) } }
        }
        if (state.coChoices.isNotEmpty()) {
            CoBeneficiaryChoice(state.coChoices, form.coBeneficiaryId, enabled = !state.isSaving) { value ->
                onChange { it.copy(coBeneficiaryId = value) }
            }
        }
        state.sharedWith?.let { other ->
            Text(
                stringResource(R.string.gift_item_co_with, other),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.showAlreadyBought) {
            AlreadyBoughtBox(
                checked = form.alreadyBought || state.alreadyBoughtLocked,
                locked = state.alreadyBoughtLocked,
                enabled = !state.isSaving,
            ) { value -> onChange { it.copy(alreadyBought = value) } }
        }
        if (state.showPhoto) {
            // The product, or a screenshot (récit 44).
            PhotoField(
                stored = state.storedPhoto,
                edit = form.photo,
                onEdit = { photo -> onChange { it.copy(photo = photo) } },
                enabled = !state.isSaving,
            )
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
        // Whoever shares the wish edits it, but only its keeper removes it (récit 73).
        if (!state.isNew && state.sharedByOther == null) DeleteButton(form.title, !state.isSaving, onDelete)
    }
}

/**
 * « Je l'ai déjà acheté, une participation me ferait plaisir » (récit 68). Once someone takes part,
 * the box stays checked, and says why.
 */
@Composable
private fun AlreadyBoughtBox(checked: Boolean, locked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = checked, enabled = enabled && !locked, role = Role.Checkbox, onValueChange = onChange),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled && !locked)
            Text(stringResource(R.string.gift_item_already_bought_check), style = MaterialTheme.typography.bodyLarge)
        }
        if (locked) {
            Text(
                stringResource(R.string.gift_item_already_bought_locked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

/** What a wish is for (récit 9 ter): « Quand tu veux » by default, Christmas or the birthday. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WishOccasionChoice(selected: GiftOccasion?, forMe: Boolean, onSelect: (GiftOccasion?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.gift_item_occasion_label), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null, GiftOccasion.Christmas, GiftOccasion.Birthday).forEach { occasion ->
                FilterChip(
                    selected = selected == occasion,
                    onClick = { onSelect(occasion) },
                    label = { Text(wishOccasionLabel(occasion, forMe)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

/**
 * Récit 50: where a link shared from another app goes. « Pour moi » adds it to my wishes; a name
 * makes it an idea for that person, in their « Idées de la famille », that they never see.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareTargetChoice(targets: List<ShareTarget>, selected: String?, enabled: Boolean, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.gift_item_target_label), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            targets.forEach { target ->
                FilterChip(
                    selected = selected == target.memberId,
                    onClick = { onSelect(target.memberId) },
                    enabled = enabled,
                    label = {
                        Text(
                            if (target.memberId == null) {
                                stringResource(R.string.gift_item_target_me)
                            } else {
                                stringResource(R.string.gift_item_target_idea, target.name)
                            },
                        )
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
    }
}

/**
 * « Cadeau commun avec… » (récit 73), optional: « Personne », or a member of the household of the
 * person. The wish then shows in their wishes too, and nobody tells them who reserved it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoBeneficiaryChoice(choices: List<ShareTarget>, selected: String?, enabled: Boolean, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.gift_item_co_label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                enabled = enabled,
                label = { Text(stringResource(R.string.gift_item_co_none)) },
                modifier = Modifier.heightIn(min = 56.dp),
            )
            choices.forEach { choice ->
                FilterChip(
                    selected = selected == choice.memberId,
                    onClick = { onSelect(choice.memberId) },
                    enabled = enabled,
                    label = { Text(choice.name) },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
        Text(
            stringResource(R.string.gift_item_co_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Fictional form of récit 50: a shared link, which an adult may make an idea for someone. */
internal fun previewSharedWishForm() = GiftItemEditUiState.Editing(
    form = GiftItemForm("Un puzzle de 1000 pièces", "https://example.com/puzzle"),
    isNew = true,
    hiddenFromBeneficiary = true,
    beneficiaryName = "Inès",
    hasChanges = true,
    targets = listOf(ShareTarget(null, "Camille"), ShareTarget("m2", "Inès"), ShareTarget("m3", "Noé")),
    targetId = "m2",
)

@FamiliaPreviews
@Composable
private fun SharedWishPreview() {
    FamiliaTheme { Surface { GiftItemEditScreen(previewSharedWishForm(), {}, {}, {}, {}) } }
}
