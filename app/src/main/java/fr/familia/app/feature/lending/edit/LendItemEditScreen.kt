package fr.familia.app.feature.lending.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChildFriendly
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.familia.app.R
import fr.familia.app.core.model.LendCategory
import fr.familia.app.core.photo.PhotoEdit
import fr.familia.app.core.photo.PhotoSourceSheet
import fr.familia.app.core.photo.SinglePhoto
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.form.CategoryTiles
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormLabel
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.feature.feed.FeedTone
import fr.familia.app.feature.lending.lendCategoryLabel

/** The form of a thing to lend (PRD, récit 46), in tiles (récit 78, DESIGN-51). Stateless. */
@Composable
fun LendItemEditScreen(
    state: LendItemEditUiState,
    onChange: ((LendItemForm) -> LendItemForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val editing = state as? LendItemEditUiState.Editing
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        topBar = {
            FamiliaTopBar(
                title = stringResource(if (editing?.isNew == false) R.string.lend_edit_title else R.string.lend_new_title),
                onBack = onBack,
            )
        },
        bottomBar = {
            if (editing != null) {
                StickyActionBar(
                    text = stringResource(if (editing.isNew) R.string.lend_add_item else R.string.lend_save),
                    onClick = onSave,
                    busy = editing.isSaving,
                )
            }
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
                is LendItemEditUiState.Editing -> Form(state, onChange)
            }
        }
    }
}

/** The tiles of DESIGN-51, in their order on the screen. */
private enum class LendPart { Item, Category }

private fun LendFormError.part(): LendPart = when (this) {
    LendFormError.NameMissing -> LendPart.Item
    LendFormError.CategoryMissing -> LendPart.Category
}

@Composable
private fun Form(state: LendItemEditUiState.Editing, onChange: ((LendItemForm) -> LendItemForm) -> Unit) {
    val form = state.form
    val errors = state.errors
    val enabled = !state.isSaving
    // Lilac, the color of Entraide: dark on a light theme, light on a dark one.
    val accent = if (LocalClayColors.current.tintAsStripe) FeedTone.Lilac.bar else FeedTone.Lilac.label
    val scroll = rememberScrollState()
    val tops = remember { mutableStateMapOf<LendPart, Int>() }
    // After a refused « Ajouter l'objet », the page goes up to the first tile to fix.
    LaunchedEffect(errors) {
        val first = errors.map { it.part() }.minOrNull() ?: return@LaunchedEffect
        tops[first]?.let { scroll.animateScrollTo(it) }
    }
    fun Modifier.part(part: LendPart) = onGloballyPositioned { tops[part] = it.positionInParent().y.toInt() }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // The photo is what helps most for a thing: first, and large.
        BigPhoto(stored = state.storedPhoto, edit = form.photo, onEdit = { edit -> onChange { it.copy(photo = edit) } }, enabled = enabled)

        FormTile(Icons.Outlined.Inventory2, stringResource(R.string.lend_tile_item), Modifier.part(LendPart.Item), accent = accent) {
            val nameLabel = stringResource(R.string.lend_field_name)
            FormLabel(nameLabel)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> onChange { it.copy(name = v.take(LEND_NAME_MAX)) } },
                isError = LendFormError.NameMissing in errors,
                singleLine = true,
                enabled = enabled,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = nameLabel },
            )
            if (LendFormError.NameMissing in errors) FormHint(stringResource(R.string.lend_error_name), isError = true)
            val noteLabel = stringResource(R.string.lend_field_note)
            FormLabel(noteLabel)
            OutlinedTextField(
                value = form.note,
                onValueChange = { v -> onChange { it.copy(note = v.take(LEND_NOTE_MAX)) } },
                placeholder = { Text(stringResource(R.string.lend_field_note_hint)) },
                minLines = 2,
                enabled = enabled,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = noteLabel },
            )
        }

        FormTile(Icons.Outlined.Category, stringResource(R.string.lend_field_category), Modifier.part(LendPart.Category), accent = accent) {
            CategoryTiles(
                options = LendCategory.entries,
                selected = form.category,
                onSelect = { category -> onChange { it.copy(category = category) } },
                label = { lendCategoryLabel(it) },
                icon = { it.icon() },
                enabled = enabled,
                selectedColor = FeedTone.Lilac.labelBackground,
                selectedContentColor = FeedTone.Lilac.label,
            )
            if (LendFormError.CategoryMissing in errors) FormHint(stringResource(R.string.lend_error_category), isError = true)
        }

        if (state.saveFailed) {
            FormHint(
                stringResource(R.string.lend_save_failed),
                isError = true,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Text(
            stringResource(R.string.lend_no_money),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

private val PhotoShape = RoundedCornerShape(22.dp)

/**
 * The photo of the thing, 190 dp high, with « Changer » laid on it and « Retirer la photo » under
 * it (asked first). Without a photo, a dashed zone « Ajouter une photo ».
 */
@Composable
private fun BigPhoto(stored: String?, edit: PhotoEdit, onEdit: (PhotoEdit) -> Unit, enabled: Boolean) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val local = (edit as? PhotoEdit.Picked)?.source
    val storedPath = if (edit == PhotoEdit.Unchanged) stored?.let { SinglePhoto.photoPath(it) } else null
    if (local != null || storedPath != null) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().height(190.dp).clip(PhotoShape)) {
                val description = stringResource(R.string.lend_photo_label)
                if (storedPath != null) {
                    StorageImage(storedPath, description, Modifier.fillMaxSize())
                } else {
                    AsyncImage(model = local, contentDescription = description, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Surface(
                    onClick = { choosing = true },
                    enabled = enabled,
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).heightIn(min = 56.dp),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.lend_photo_change), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            TextButton(onClick = { confirmRemove = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.photo_remove))
            }
        }
    } else {
        val dash = MaterialTheme.colorScheme.outline
        Column(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(PhotoShape)
                .drawBehind {
                    drawRoundRect(
                        color = dash,
                        cornerRadius = CornerRadius(22.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                    )
                }
                .clickable(enabled = enabled, role = Role.Button) { choosing = true },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.photo_add), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
    if (choosing) {
        PhotoSourceSheet(onPicked = { onEdit(PhotoEdit.Picked(it)) }, onDismiss = { choosing = false })
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.photo_remove_confirm_title)) },
            text = { Text(stringResource(R.string.photo_remove_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onEdit(if (stored == null) PhotoEdit.Unchanged else PhotoEdit.Removed)
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.photo_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private fun LendCategory.icon(): ImageVector = when (this) {
    LendCategory.Kitchen -> Icons.Outlined.Kitchen
    LendCategory.Diy -> Icons.Outlined.Handyman
    LendCategory.Baby -> Icons.Outlined.ChildFriendly
    LendCategory.Sport -> Icons.Outlined.SportsSoccer
    LendCategory.Travel -> Icons.Outlined.Luggage
    LendCategory.Other -> Icons.Outlined.MoreHoriz
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
