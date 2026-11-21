package fr.familia.app.feature.events.bring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.ui.form.FormTileHeading
import fr.familia.app.core.ui.form.FormOutlineButton
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.BringItem
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.recipes.RecipePickerDialog

/**
 * « Qui apporte quoi » inside an event (PRD, récit 4 bis), with its own ViewModel. Set
 * [onOpenRecipe] while the Recettes module is on: a line may then join a recipe of the book.
 */
@Composable
fun BringSection(
    container: AppContainer?,
    currentMember: Member,
    eventId: String,
    canManageEvent: Boolean,
    onOpenRecipe: ((recipeId: String) -> Unit)? = null,
) {
    val viewModel: BringViewModel = viewModel(
        key = "bring-$eventId-$canManageEvent",
        factory = viewModelFactory {
            initializer { BringViewModel(container?.bringRepository, container?.memberRepository, currentMember.id, eventId, canManageEvent) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BringContent(
        state = state,
        onAdd = viewModel::add,
        onTake = viewModel::take,
        onRelease = viewModel::release,
        onDelete = viewModel::delete,
        onDismissError = viewModel::dismissError,
        recipePicker = if (onOpenRecipe != null) recipePicker(container) else null,
        onOpenRecipe = onOpenRecipe,
    )
}

private fun recipePicker(
    container: AppContainer?,
): @Composable (onPick: (id: String, title: String) -> Unit, onDismiss: () -> Unit) -> Unit = { onPick, onDismiss ->
    RecipePickerDialog(container, onPick = { recipe -> onPick(recipe.id, recipe.title) }, onDismiss = onDismiss)
}

/** Stateless: every change goes through the callbacks. */
@Composable
fun BringContent(
    state: BringUiState,
    onAdd: (label: String, take: Boolean, recipeId: String?) -> Boolean,
    onTake: (String) -> Unit,
    onRelease: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismissError: () -> Unit,
    /** « Joindre une recette »: shows the picker, which gives back the recipe's id and title. */
    recipePicker: (@Composable (onPick: (id: String, title: String) -> Unit, onDismiss: () -> Unit) -> Unit)? = null,
    /** « Voir la recette » on a line that has one. */
    onOpenRecipe: ((recipeId: String) -> Unit)? = null,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var takeIt by rememberSaveable { mutableStateOf(true) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var recipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var recipeTitle by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    val tooLong = label.trim().length > BringItem.MAX_LABEL
    val add: () -> Unit = {
        if (onAdd(label, takeIt, recipeId)) {
            label = ""
            recipeId = null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormTileHeading(Icons.Outlined.Restaurant, stringResource(R.string.bring_title))
        if (!state.isLoading && state.rows.isEmpty()) {
            Text(
                stringResource(R.string.bring_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.rows.forEach { row ->
            BringLine(row, enabled = !state.isBusy, onTake, onRelease, onAskDelete = { deleting = row.item.id }, onOpenRecipe = onOpenRecipe)
        }

        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text(stringResource(R.string.bring_add_label)) },
            placeholder = { Text(stringResource(R.string.bring_add_hint)) },
            isError = tooLong,
            supportingText = if (tooLong) ({ Text(stringResource(R.string.bring_too_long)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = takeIt, role = Role.Checkbox) { takeIt = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = takeIt, onCheckedChange = null)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.bring_add_take), style = MaterialTheme.typography.bodyLarge)
        }
        if (recipePicker != null) {
            val joined = recipeId
            if (joined == null) {
                TextButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.bring_join_recipe))
                }
            } else {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.bring_recipe_joined, recipeTitle), style = MaterialTheme.typography.bodyLarge)
                }
                // A written label for every action, never an icon alone.
                TextButton(onClick = { recipeId = null }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.bring_recipe_remove))
                }
            }
            if (picking) {
                recipePicker(
                    { id, title ->
                        recipeId = id
                        recipeTitle = title
                        // The recipe names the line when nothing is written yet.
                        if (label.isBlank()) label = title.take(BringItem.MAX_LABEL)
                        picking = false
                    },
                    { picking = false },
                )
            }
        }
        FormOutlineButton(stringResource(R.string.bring_add), onClick = add, enabled = label.isNotBlank() && !tooLong && !state.isBusy, icon = Icons.Outlined.Add)
    }

    val id = deleting
    if (id != null) {
        val name = state.rows.firstOrNull { it.item.id == id }?.item?.label.orEmpty()
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.bring_delete_title, name)) },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(id) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.bring_delete_confirm)) } },
            dismissButton = { TextButton(onClick = { deleting = null }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (state.failed) {
        AlertDialog(
            onDismissRequest = onDismissError,
            text = { Text(stringResource(R.string.bring_failed)) },
            confirmButton = { TextButton(onClick = onDismissError) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BringLine(
    row: BringRow,
    enabled: Boolean,
    onTake: (String) -> Unit,
    onRelease: (String) -> Unit,
    onAskDelete: () -> Unit,
    onOpenRecipe: ((recipeId: String) -> Unit)?,
) {
    val item = row.item
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    row.isMine -> stringResource(R.string.bring_by_me)
                    item.takerId != null -> stringResource(R.string.bring_by, item.takerName.orEmpty())
                    else -> stringResource(R.string.bring_free)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.takerId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    item.takerId == null -> OutlinedButton(
                        onClick = { onTake(item.id) },
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.bring_take)) }
                    row.isMine -> TextButton(onClick = { onRelease(item.id) }, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.bring_release_mine))
                    }
                    row.canRelease -> TextButton(onClick = { onRelease(item.id) }, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.bring_release))
                    }
                }
                val recipe = item.recipeId
                if (recipe != null && onOpenRecipe != null) {
                    TextButton(onClick = { onOpenRecipe(recipe) }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.bring_open_recipe))
                    }
                }
            }
        }
        if (row.canDelete) {
            IconButton(onClick = onAskDelete, enabled = enabled, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.bring_delete, item.label))
            }
        }
    }
}

/** Fictional data for previews and screenshots. */
internal fun previewBring(): BringUiState = BringUiState(
    rows = listOf(
        BringRow(BringItem("b1", "L'entrée", "me", "me", "Camille"), isMine = true, canRelease = true, canDelete = true),
        BringRow(BringItem("b2", "Le dessert", "ines", "ines", "Inès", recipeId = "r1"), isMine = false, canRelease = false, canDelete = false),
        BringRow(BringItem("b3", "Le pain", "me", null, null), isMine = false, canRelease = false, canDelete = true),
    ),
    isLoading = false,
)

@FamiliaPreviews
@Composable
private fun BringContentPreview() {
    FamiliaTheme { Surface { BringContent(previewBring(), { _, _, _ -> true }, {}, {}, {}, {}, recipePicker = { _, _ -> }, onOpenRecipe = {}) } }
}
