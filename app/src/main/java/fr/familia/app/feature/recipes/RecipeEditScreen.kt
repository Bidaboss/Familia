package fr.familia.app.feature.recipes

import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp

/** Recipe form. Stateless: every change goes through [onChange]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(
    state: RecipeEditUiState,
    onChange: ((RecipeForm) -> RecipeForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    /** The field « Sa petite histoire », with the switch recipeBook (récit 47). */
    showStory: Boolean = false,
) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val editing = state as? RecipeEditUiState.Editing
    val hasChanges = editing?.hasChanges == true
    BackHandler(enabled = hasChanges) { confirmLeave = true }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.recipe_leave_title)) },
            text = { Text(stringResource(R.string.recipe_leave_body)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.trip_leave_keep)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.member_leave_discard))
                }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing?.isNew == false) R.string.recipe_edit else R.string.recipes_add), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = { if (hasChanges) confirmLeave = true else onBack() }) },
                actions = { HelpButton(ScreenHelp.Recipes) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                RecipeEditUiState.Loading, is RecipeEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                RecipeEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.Restaurant,
                    title = stringResource(R.string.recipe_missing_title),
                    body = stringResource(R.string.recipe_unavailable_body),
                )
                is RecipeEditUiState.Editing -> Form(state, onChange, onSave, showStory)
            }
        }
    }
}

@Composable
private fun Form(state: RecipeEditUiState.Editing, onChange: ((RecipeForm) -> RecipeForm) -> Unit, onSave: () -> Unit, showStory: Boolean) {
    val form = state.form
    val errors = state.errors
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Field(form.title, { v -> onChange { it.copy(title = v) } }, R.string.recipe_field_title, errors, RecipeFormError.TitleMissing, RecipeFormError.TitleTooLong)
        OriginPicker(state, onChange)
        if (form.originMemberId == null) {
            Field(form.origin, { v -> onChange { it.copy(origin = v) } }, R.string.recipe_field_origin, errors, RecipeFormError.OriginTooLong)
        }
        Field(
            form.servings,
            { v -> onChange { it.copy(servings = v.filter(Char::isDigit).take(2)) } },
            R.string.recipe_field_servings,
            errors,
            RecipeFormError.ServingsInvalid,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Field(
            form.ingredients,
            { v -> onChange { it.copy(ingredients = v) } },
            R.string.recipe_field_ingredients,
            errors,
            RecipeFormError.IngredientsMissing,
            RecipeFormError.IngredientsTooLong,
            help = R.string.recipe_field_lines_help,
            singleLine = false,
        )
        Field(
            form.steps,
            { v -> onChange { it.copy(steps = v) } },
            R.string.recipe_field_steps,
            errors,
            RecipeFormError.StepsMissing,
            RecipeFormError.StepsTooLong,
            help = R.string.recipe_field_lines_help,
            singleLine = false,
        )
        Field(form.tags, { v -> onChange { it.copy(tags = v) } }, R.string.recipe_field_tags, errors, help = R.string.recipe_field_tags_help)
        if (showStory) {
            Field(
                form.story,
                { v -> onChange { it.copy(story = v) } },
                R.string.recipe_field_story,
                errors,
                RecipeFormError.StoryTooLong,
                help = R.string.recipe_field_story_help,
                singleLine = false,
            )
        }
        if (state.saveFailed) {
            Text(stringResource(R.string.recipe_save_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            if (state.isSaving) {
                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.member_save))
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValue: (String) -> Unit,
    labelRes: Int,
    errors: Set<RecipeFormError>,
    vararg kinds: RecipeFormError,
    help: Int? = null,
    singleLine: Boolean = true,
    keyboard: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
) {
    val error = kinds.firstOrNull { it in errors }
    val supportingRes = error?.let { errorRes(it) } ?: help
    val supporting: (@Composable () -> Unit)? = if (supportingRes == null) null else {
        { Text(stringResource(supportingRes), color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(stringResource(labelRes)) },
        isError = error != null,
        supportingText = supporting,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
        keyboardOptions = keyboard,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun errorRes(error: RecipeFormError): Int = when (error) {
    RecipeFormError.TitleMissing -> R.string.error_recipe_title_missing
    RecipeFormError.TitleTooLong, RecipeFormError.OriginTooLong -> R.string.error_event_title_too_long
    RecipeFormError.ServingsInvalid -> R.string.error_recipe_servings
    RecipeFormError.IngredientsMissing -> R.string.error_recipe_ingredients_missing
    RecipeFormError.StepsMissing -> R.string.error_recipe_steps_missing
    RecipeFormError.IngredientsTooLong, RecipeFormError.StepsTooLong, RecipeFormError.StoryTooLong -> R.string.error_recipe_too_long
}

/** « De qui vient la recette ? »: a member of the family, or nobody in particular (then a free origin). */
@Composable
private fun OriginPicker(state: RecipeEditUiState.Editing, onChange: ((RecipeForm) -> RecipeForm) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val chosen = state.members.firstOrNull { it.id == state.form.originMemberId }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(chosen?.let { stringResource(R.string.recipe_from, it.firstName) } ?: stringResource(R.string.recipe_pick_origin))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.recipe_pick_origin)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OriginRow(stringResource(R.string.recipe_origin_none), state.form.originMemberId == null) {
                        onChange { it.copy(originMemberId = null) }
                        open = false
                    }
                    state.members.forEach { member ->
                        OriginRow(member.firstName, member.id == state.form.originMemberId) {
                            onChange { it.copy(originMemberId = member.id) }
                            open = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.album_photo_close)) }
            },
        )
    }
}

@Composable
private fun OriginRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

internal fun previewRecipeEdit(isNew: Boolean = false): RecipeEditUiState.Editing = RecipeEditUiState.Editing(
    form = RecipeForm.from(previewRecipes().first()),
    isNew = isNew,
    members = listOf(OriginChoice("a", "Camille"), OriginChoice("b", "Inès")),
    errors = emptySet(),
)

@FamiliaPreviews
@Composable
private fun RecipeEditPreview() {
    FamiliaTheme { Surface { RecipeEditScreen(previewRecipeEdit(), {}, {}, {}) } }
}
