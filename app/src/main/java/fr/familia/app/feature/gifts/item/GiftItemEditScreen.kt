package fr.familia.app.feature.gifts.item

import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.familia.app.R
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.photo.PhotoEdit
import fr.familia.app.core.photo.SinglePhoto
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.photo.keepPhotoAccess
import fr.familia.app.core.photo.newCameraUri
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.form.ChipGroup
import fr.familia.app.core.ui.form.FormChip
import fr.familia.app.core.ui.form.FormDeleteButton
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.scrollToFirstError
import fr.familia.app.feature.gifts.PictureOver
import fr.familia.app.feature.gifts.WishLook
import fr.familia.app.feature.gifts.WishPill
import fr.familia.app.feature.gifts.detail.linkHost
import fr.familia.app.feature.gifts.formatEuros
import fr.familia.app.feature.gifts.parseEuros
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp

/** Form of a gift item (add, edit, remove), after the prototype (correctif E6 à E8, fiche K8). Stateless. */
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
    /** « Coller »: the link of the clipboard, whose image, title and price are then read. */
    onPasteLink: (String) -> Unit = { url -> onChange { it.copy(url = url) } },
) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val hasChanges = (state as? GiftItemEditUiState.Editing)?.hasChanges == true
    val leave: () -> Unit = { if (hasChanges) confirmLeave = true else onBack() }
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
    val editing = state as? GiftItemEditUiState.Editing
    val wish = editing?.showOccasion == true
    val title = stringResource(
        when {
            wish && editing?.isNew == false -> R.string.wish_form_edit_title
            wish -> R.string.wish_form_new_title
            editing?.isNew == false -> R.string.gift_item_edit_title
            else -> R.string.gift_item_new_title
        },
    )
    Box(Modifier.fillMaxSize().themeBackground()) {
        Column(Modifier.fillMaxSize()) {
            // Back in a white round, the title in the middle (E6), and the « ? » of the screen.
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
                Round { HelpButton(ScreenHelp.GiftItemEdit) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    GiftItemEditUiState.Loading, GiftItemEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    GiftItemEditUiState.Unavailable -> EmptyState(
                        icon = Icons.Outlined.CardGiftcard,
                        title = stringResource(R.string.gift_item_unavailable_title),
                        body = stringResource(R.string.gift_item_unavailable_body),
                    )
                    is GiftItemEditUiState.Editing -> FormContent(state, onChange, onDelete, onChooseTarget, onPasteLink, onSave, leave)
                }
            }
        }
    }
}

@Composable
internal fun Round(content: @Composable () -> Unit) {
    Surface(shape = CircleShape, color = Color.White, contentColor = WishLook.Ink, border = BorderStroke(1.dp, WishLook.Edge)) { content() }
}

/** The parts of the form, in their order on the screen, for the first error. */
private enum class ItemPart { Link, Wish }

private fun GiftItemFormError.part(): ItemPart = when (this) {
    GiftItemFormError.BadLink -> ItemPart.Link
    GiftItemFormError.TitleMissing, GiftItemFormError.TitleTooLong, GiftItemFormError.NoteTooLong, GiftItemFormError.BadPrice,
    GiftItemFormError.WhereTooLong -> ItemPart.Wish
}

/**
 * The form of the prototype (E6 à E8): bold labels over white rounded fields, the link and its
 * preview first, the photo, the name, the price, « déjà acheté », the occasion, a word for the family,
 * then the big brown button and « Annuler ». Same fields and rules as before.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormContent(
    state: GiftItemEditUiState.Editing,
    onChange: ((GiftItemForm) -> GiftItemForm) -> Unit,
    onDelete: () -> Unit,
    onChooseTarget: (String?) -> Unit,
    onPasteLink: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val form = state.form
    val errors = state.errors
    val wish = state.showOccasion
    val scroll = rememberScrollState()
    val part = scrollToFirstError(scroll, errors, errors.map { it.part() }.minOrNull())
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            // The surprise idea keeps its sentence: the person will not see it.
            state.hiddenFromBeneficiary -> InfoBox(stringResource(R.string.gift_item_hidden_hint, state.beneficiaryName))
            wish && state.forMe -> InfoBox("👪 " + stringResource(R.string.wish_form_family_sees))
        }
        if (state.targets.isNotEmpty()) {
            Label(stringResource(R.string.gift_item_target_label))
            ShareTargetChoice(state.targets, state.targetId, enabled = !state.isSaving, onSelect = onChooseTarget)
        }
        Column(part(ItemPart.Link), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label(stringResource(R.string.wish_form_link))
            LinkField(form.url, errors, enabled = !state.isSaving, onValue = { value -> onChange { it.copy(url = value) } }, onPaste = onPasteLink)
            if (state.previewLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.gift_item_preview_loading), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (form.url.isNotBlank() && (state.imageUrl != null || form.title.isNotBlank())) LinkPreview(state)
        }
        if (state.showPhoto) {
            Label(stringResource(R.string.wish_form_photo))
            PhotoField(form.photo, state.storedPhoto, form.title, hasLink = form.url.isNotBlank(), enabled = !state.isSaving) { photo ->
                onChange { it.copy(photo = photo) }
            }
        }
        Column(part(ItemPart.Wish), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label(stringResource(if (wish) R.string.wish_form_name else R.string.gift_item_field_title))
            WhiteField(
                value = form.title,
                onValue = { value -> onChange { it.copy(title = value) } },
                placeholder = stringResource(R.string.wish_form_name_hint),
                error = errorText(errors, GiftItemFormError.TitleMissing, GiftItemFormError.TitleTooLong),
                capitalize = true,
            )
            Label(stringResource(R.string.wish_form_price))
            WhiteField(
                value = form.price,
                onValue = { value -> onChange { it.copy(price = value) } },
                placeholder = "—",
                error = errorText(errors, GiftItemFormError.BadPrice),
                keyboard = KeyboardType.Decimal,
                suffix = "€",
            )
            // « Où trouver le cadeau ? » (K8): a shop, a town or a site; « En ligne · … » from a pasted link.
            Label(stringResource(R.string.wish_form_where))
            WhiteField(
                value = form.whereToFind,
                onValue = { value -> onChange { it.copy(whereToFind = value) } },
                placeholder = stringResource(R.string.wish_form_where_hint),
                error = errorText(errors, GiftItemFormError.WhereTooLong),
                capitalize = true,
            )
        }
        if (state.showAlreadyBought) {
            AlreadyBoughtBox(
                checked = form.alreadyBought || state.alreadyBoughtLocked,
                locked = state.alreadyBoughtLocked,
                enabled = !state.isSaving,
            ) { value -> onChange { it.copy(alreadyBought = value) } }
            if (form.alreadyBought || state.alreadyBoughtLocked) {
                val price = parseEuros(form.price)
                InfoBox(
                    if (price != null) stringResource(R.string.wish_form_bought_info_price, formatEuros(price)) else stringResource(R.string.wish_form_bought_info),
                )
            }
        }
        if (state.showOccasion) {
            Label(stringResource(R.string.wish_form_occasion))
            WishOccasionChoice(form.occasion, enabled = !state.isSaving) { value -> onChange { it.copy(occasion = value) } }
        }
        if (state.coChoices.isNotEmpty() || state.sharedWith != null) {
            Label(stringResource(R.string.gift_item_tile_joint))
            if (state.coChoices.isNotEmpty()) {
                CoBeneficiaryChoice(state.coChoices, form.coBeneficiaryId, enabled = !state.isSaving) { value ->
                    onChange { it.copy(coBeneficiaryId = value) }
                }
            }
            state.sharedWith?.let { other ->
                Text(stringResource(R.string.gift_item_co_with, other), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Label(stringResource(if (wish) R.string.wish_form_note else R.string.gift_item_field_note))
        WhiteField(
            value = form.note,
            onValue = { value -> onChange { it.copy(note = value) } },
            placeholder = stringResource(if (wish) R.string.wish_form_note_hint else R.string.gift_item_field_note_hint),
            error = errorText(errors, GiftItemFormError.NoteTooLong),
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
        Button(
            onClick = onSave,
            enabled = !state.isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = WishLook.Dark, contentColor = Color.White),
            // Full-width actions of Cadeaux: 48 dp (règle 57 of regles-cadeaux-themes-photo.md, l'admin 25/11).
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(
                    stringResource(
                        when {
                            !state.isNew -> R.string.member_save
                            wish && state.forMe && state.targetId == null -> R.string.wish_form_add_mine
                            else -> R.string.gift_item_add
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        OutlinedButton(
            onClick = onCancel,
            enabled = !state.isSaving,
            border = BorderStroke(2.dp, WishLook.Edge),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = WishLook.Ink),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        // Whoever shares the wish edits it, but only its keeper removes it (récit 73).
        if (!state.isNew && state.sharedByOther == null) DeleteButton(form.title, !state.isSaving, onDelete)
    }
}

@Composable
internal fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun InfoBox(text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = WishLook.InfoBlue, contentColor = WishLook.InfoBlueInk, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
    }
}

/** A white rounded field with a beige edge (E6), 56 dp at least. */
@Composable
internal fun WhiteField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    error: (@Composable () -> Unit)?,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalize: Boolean = false,
    singleLine: Boolean = true,
    suffix: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        placeholder = { Text(placeholder) },
        isError = error != null,
        supportingText = error,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        shape = FieldShape,
        suffix = suffix?.let { { Text(it) } },
        trailingIcon = trailing,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = WishLook.Field,
            unfocusedContainerColor = WishLook.Field,
            errorContainerColor = WishLook.Field,
            unfocusedBorderColor = WishLook.Edge,
            focusedBorderColor = WishLook.Dark,
            focusedTextColor = WishLook.Ink,
            unfocusedTextColor = WishLook.Ink,
            unfocusedPlaceholderColor = WishLook.Muted,
            focusedPlaceholderColor = WishLook.Muted,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}

/** « Colle un lien ici », with the dark « Coller » that takes the link of the clipboard (E6). */
@Composable
internal fun LinkField(url: String, errors: Set<GiftItemFormError>, enabled: Boolean, onValue: (String) -> Unit, onPaste: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    WhiteField(
        value = url,
        onValue = onValue,
        placeholder = stringResource(R.string.wish_form_link_hint),
        error = errorText(errors, GiftItemFormError.BadLink),
        keyboard = KeyboardType.Uri,
        trailing = {
            Button(
                onClick = { clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let(onPaste) },
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = WishLook.Dark, contentColor = Color.White),
                modifier = Modifier.padding(end = 6.dp).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.wish_form_paste), fontWeight = FontWeight.Bold) }
        },
    )
}

/**
 * What a link gives to an idea (E12): my [photo] first, else its picture, else 🎁, the title,
 * « 49,90 € · boutique.fr ».
 */
@Composable
internal fun LinkPreviewCard(title: String, price: String, url: String, imageUrl: String?, photo: Shown? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = WishLook.Field, contentColor = WishLook.Ink, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PictureOver(
                    imageUrl.takeIf { photo == null },
                    Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).background(WishLook.pastel(title)),
                ) {
                    if (photo != null) PhotoThumb(photo) else Text("🎁", style = MaterialTheme.typography.headlineMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text(title.ifBlank { "…" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val details = listOfNotNull(parseEuros(price)?.let { formatEuros(it) }, linkHost(url)).joinToString(" · ")
                    if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodyMedium, color = WishLook.Muted)
                }
            }
        }
        Text(
            stringResource(if (photo != null) R.string.wish_form_photo_replaces else R.string.wish_form_from_link),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** What the link gives (E7): its picture (or my photo, which comes first), the title, « 49,90 € · boutique.fr ». */
@Composable
private fun LinkPreview(state: GiftItemEditUiState.Editing) {
    val form = state.form
    val photo = shownPhoto(form.photo, state.storedPhoto)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = WishLook.Field, contentColor = WishLook.Ink, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).background(WishLook.pastel(form.title)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        photo != null -> PhotoThumb(photo)
                        state.imageUrl != null -> AsyncImage(
                            model = state.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> Text("🎁", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(form.title.ifBlank { "…" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val details = listOfNotNull(parseEuros(form.price)?.let { formatEuros(it) }, linkHost(form.url)).joinToString(" · ")
                    if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodyMedium, color = WishLook.Muted)
                }
            }
        }
        Text(
            stringResource(if (photo != null) R.string.wish_form_photo_replaces else R.string.wish_form_from_link),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** The photo to show: one just chosen, or the one kept, whose base is [stored]. */
internal sealed interface Shown {
    data class Local(val uri: String) : Shown
    data class Stored(val base: String) : Shown
}

internal fun shownPhoto(edit: PhotoEdit, stored: String?): Shown? = when (edit) {
    is PhotoEdit.Picked -> Shown.Local(edit.source)
    PhotoEdit.Removed -> null
    PhotoEdit.Unchanged -> stored?.let { Shown.Stored(it) }
}

@Composable
private fun PhotoThumb(photo: Shown) {
    when (photo) {
        is Shown.Local -> AsyncImage(model = photo.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        is Shown.Stored -> StorageImage(SinglePhoto.photoPath(photo.base), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

/**
 * « Photo (facultative) » (K8): « 📷 Prendre une photo » and « 🖼 Galerie » side by side, dashed; once
 * chosen, « Ta photo », « Elle remplace l'image du lien » (when there is a link) and « Retirer ». Shared
 * by a wish and a private idea (E11); [stored] is the base of the photo already kept.
 */
@Composable
internal fun PhotoField(edit: PhotoEdit, stored: String?, title: String, hasLink: Boolean, enabled: Boolean, onEdit: (PhotoEdit) -> Unit) {
    val context = LocalContext.current
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { keepPhotoAccess(context, listOf(it)) }
        uri?.let { onEdit(PhotoEdit.Picked(it.toString())) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        cameraFile?.takeIf { taken }?.let { onEdit(PhotoEdit.Picked(it)) }
    }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val photo = shownPhoto(edit, stored)
    if (photo != null) {
        Surface(shape = RoundedCornerShape(20.dp), color = WishLook.Field, contentColor = WishLook.Ink, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).background(WishLook.pastel(title))) { PhotoThumb(photo) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.wish_form_your_photo), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (hasLink) {
                        Text(stringResource(R.string.wish_form_your_photo_replaces), style = MaterialTheme.typography.bodyMedium, color = WishLook.Muted)
                    }
                }
                Button(
                    onClick = { onEdit(if (stored == null) PhotoEdit.Unchanged else PhotoEdit.Removed) },
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1E8DA), contentColor = WishLook.Ink),
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.wish_form_photo_remove), fontWeight = FontWeight.Bold) }
            }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hasCamera) {
            DashedButton("📷 " + stringResource(R.string.photo_take), enabled, Modifier.weight(1f)) {
                val uri = newCameraUri(context)
                cameraFile = uri.toString()
                try {
                    camera.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    cameraFile = null
                }
            }
        }
        DashedButton("🖼 " + stringResource(R.string.wish_form_gallery), enabled, Modifier.weight(1f)) {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

@Composable
private fun DashedButton(text: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val edge = Color(0xFFCDBA9C)
    Box(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(20.dp))
            .drawBehind {
                drawRoundRect(
                    edge,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

private val FieldShape = RoundedCornerShape(20.dp)

/**
 * « Je l'ai déjà acheté, une participation me ferait plaisir » (récit 68), as a switch. Once
 * someone takes part, it stays on, and says why.
 */
@Composable
private fun AlreadyBoughtBox(checked: Boolean, locked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = WishLook.Field,
            contentColor = WishLook.Ink,
            border = BorderStroke(2.dp, WishLook.Edge),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .toggleable(value = checked, enabled = enabled && !locked, role = Role.Switch, onValueChange = onChange)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = enabled && !locked,
                    colors = SwitchDefaults.colors(checkedTrackColor = WishLook.Dark),
                )
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.wish_form_bought), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.wish_form_bought_sub), style = MaterialTheme.typography.bodyMedium, color = WishLook.Muted)
                }
            }
        }
        if (locked) FormHint(stringResource(R.string.gift_item_already_bought_locked))
    }
}

@Composable
private fun DeleteButton(title: String, enabled: Boolean, onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    FormDeleteButton(stringResource(R.string.gift_item_delete), onClick = { confirm = true }, enabled = enabled)
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
        GiftItemFormError.WhereTooLong -> R.string.error_gift_where_too_long
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

/** « Pour quelle occasion ? » (E8): Anniversaire, Noël or Pas précisée, in pills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WishOccasionChoice(selected: GiftOccasion?, enabled: Boolean, onSelect: (GiftOccasion?) -> Unit) {
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(
            GiftOccasion.Birthday to R.string.wish_occasion_birthday,
            GiftOccasion.Christmas to R.string.wish_occasion_christmas,
            null to R.string.wish_occasion_none,
        ).forEach { (occasion, label) ->
            WishPill(stringResource(label), selected = selected == occasion, onClick = { onSelect(occasion) }, enabled = enabled)
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
    ChipGroup {
        targets.forEach { target ->
            FormChip(
                label = if (target.memberId == null) {
                    stringResource(R.string.gift_item_target_me)
                } else {
                    stringResource(R.string.gift_item_target_idea, target.name)
                },
                selected = selected == target.memberId,
                onClick = { onSelect(target.memberId) },
                enabled = enabled,
            )
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipGroup {
            FormChip(
                label = stringResource(R.string.gift_item_co_none),
                selected = selected == null,
                onClick = { onSelect(null) },
                enabled = enabled,
            )
            choices.forEach { choice ->
                FormChip(
                    label = stringResource(R.string.gift_item_co_choice, choice.name),
                    selected = selected == choice.memberId,
                    onClick = { onSelect(choice.memberId) },
                    enabled = enabled,
                )
            }
        }
        FormHint(stringResource(R.string.gift_item_co_hint))
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
