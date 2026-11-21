package fr.familia.app.feature.recipes

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.familia.app.R
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.photo.keepPhotoAccess
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormLabel
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.PeopleRow
import fr.familia.app.core.ui.form.PersonChoice
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp

/** Recipe form. Stateless: every change goes through [onChange]. */
@Composable
fun RecipeEditScreen(
    state: RecipeEditUiState,
    onChange: ((RecipeForm) -> RecipeForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    /** The field « Sa petite histoire », with the switch recipeBook (récit 47). */
    showStory: Boolean = false,
    /** The photos of a new recipe (retour de Kévin, 17/11); null hides them. */
    photos: RecipeEditPhotoActions? = null,
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
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(if (editing?.isNew == false) R.string.recipe_edit else R.string.recipes_add), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = { if (hasChanges) confirmLeave = true else onBack() }) },
                actions = { HelpButton(ScreenHelp.Recipes) },
            )
        },
        // The single black button at the bottom (refonte lot 4, maquette « Une recette »).
        bottomBar = {
            if (editing != null) {
                val sending = editing.sendingPhoto
                StickyActionBar(
                    text = when {
                        sending != null -> stringResource(R.string.recipe_edit_sending_photo, sending, editing.photos.size)
                        editing.isNew -> stringResource(R.string.recipe_add_confirm)
                        else -> stringResource(R.string.member_save)
                    },
                    onClick = onSave,
                    enabled = !editing.isSaving,
                    busy = editing.isSaving && sending == null,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                RecipeEditUiState.Loading, is RecipeEditUiState.Done -> LoadingState(Modifier.align(Alignment.Center))
                RecipeEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.Restaurant,
                    title = stringResource(R.string.recipe_missing_title),
                    body = stringResource(R.string.recipe_unavailable_body),
                )
                is RecipeEditUiState.Editing -> Form(state, onChange, showStory, photos)
            }
        }
    }
}

/** The tiles of the maquette: the photo, the recipe, its ingredients, its steps, its story. */
@Composable
private fun Form(
    state: RecipeEditUiState.Editing,
    onChange: ((RecipeForm) -> RecipeForm) -> Unit,
    showStory: Boolean,
    photos: RecipeEditPhotoActions?,
) {
    val form = state.form
    val errors = state.errors
    val scroll = rememberScrollState()
    // Where each tile starts, to bring the first one in error into view (recette commune n° 3).
    val tops = remember { mutableStateMapOf<RecipeTile, Int>() }
    LaunchedEffect(errors) {
        val first = RecipeTile.entries.firstOrNull { tile -> errors.any { it.tile == tile } } ?: return@LaunchedEffect
        tops[first]?.let { scroll.animateScrollTo(it) }
    }
    fun Modifier.top(tile: RecipeTile) = onGloballyPositioned { tops[tile] = it.positionInParent().y.toInt() }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (photos != null && state.isNew) Photos(state, photos)

        FormTile(Icons.Outlined.Restaurant, stringResource(R.string.recipe_tile_main), Modifier.top(RecipeTile.Main)) {
            Field(form.title, { v -> onChange { it.copy(title = v) } }, R.string.recipe_field_title, errors, RecipeFormError.TitleMissing, RecipeFormError.TitleTooLong)
            FormLabel(stringResource(R.string.recipe_pick_origin), Modifier.padding(top = 4.dp))
            OriginRounds(state, onChange)
            if (form.originMemberId == null) {
                Field(form.origin, { v -> onChange { it.copy(origin = v) } }, R.string.recipe_field_origin, errors, RecipeFormError.OriginTooLong)
            }
            Servings(form.servings, isError = RecipeFormError.ServingsInvalid in errors) { v -> onChange { it.copy(servings = v) } }
            Field(form.tags, { v -> onChange { it.copy(tags = v) } }, R.string.recipe_field_tags, errors, help = R.string.recipe_field_tags_help)
        }

        FormTile(Icons.AutoMirrored.Outlined.FormatListBulleted, stringResource(R.string.recipe_field_ingredients), Modifier.top(RecipeTile.Ingredients)) {
            Field(
                form.ingredients,
                { v -> onChange { it.copy(ingredients = v) } },
                R.string.recipe_field_ingredients,
                errors,
                RecipeFormError.IngredientsMissing,
                RecipeFormError.IngredientsTooLong,
                help = R.string.recipe_field_lines_help,
                singleLine = false,
                showLabel = false,
            )
        }

        FormTile(Icons.AutoMirrored.Outlined.Article, stringResource(R.string.recipe_field_steps), Modifier.top(RecipeTile.Steps)) {
            Field(
                form.steps,
                { v -> onChange { it.copy(steps = v) } },
                R.string.recipe_field_steps,
                errors,
                RecipeFormError.StepsMissing,
                RecipeFormError.StepsTooLong,
                help = R.string.recipe_field_lines_help,
                singleLine = false,
                showLabel = false,
            )
        }

        if (showStory) {
            FormTile(Icons.Outlined.HistoryEdu, stringResource(R.string.recipe_field_story), Modifier.top(RecipeTile.Story)) {
                Field(
                    form.story,
                    { v -> onChange { it.copy(story = v) } },
                    R.string.recipe_field_story,
                    errors,
                    RecipeFormError.StoryTooLong,
                    help = R.string.recipe_field_story_help,
                    singleLine = false,
                    showLabel = false,
                )
            }
        }
        if (state.saveFailed) {
            Surface(shape = RoundedCornerShape(16.dp), color = LocalClayColors.current.card) {
                FormHint(stringResource(R.string.recipe_save_failed), isError = true, modifier = Modifier.padding(14.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The tiles that can hold an error, top to bottom. */
private enum class RecipeTile { Main, Ingredients, Steps, Story }

private val RecipeFormError.tile: RecipeTile
    get() = when (this) {
        RecipeFormError.TitleMissing, RecipeFormError.TitleTooLong, RecipeFormError.OriginTooLong, RecipeFormError.ServingsInvalid -> RecipeTile.Main
        RecipeFormError.IngredientsMissing, RecipeFormError.IngredientsTooLong -> RecipeTile.Ingredients
        RecipeFormError.StepsMissing, RecipeFormError.StepsTooLong -> RecipeTile.Steps
        RecipeFormError.StoryTooLong -> RecipeTile.Story
    }

/**
 * « Parts » with « − » and « + » (maquette): still optional, so « − » from 1 leaves it empty, shown
 * « — ». At most [Recipe.MAX_SERVINGS], the limit of the rules.
 */
@Composable
private fun Servings(value: String, isError: Boolean, onValue: (String) -> Unit) {
    val n = value.toIntOrNull()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FormLabel(stringResource(R.string.recipe_field_servings), Modifier.weight(1f))
        OutlinedIconButton(
            onClick = { onValue(if (n == null || n <= 1) "" else (n - 1).toString()) },
            enabled = n != null,
            modifier = Modifier.size(56.dp),
        ) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.recipe_servings_less)) }
        Text(
            n?.toString() ?: "—",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 32.dp),
            textAlign = TextAlign.Center,
        )
        OutlinedIconButton(
            onClick = { onValue(((n ?: 0) + 1).coerceAtMost(Recipe.MAX_SERVINGS).toString()) },
            enabled = (n ?: 0) < Recipe.MAX_SERVINGS,
            modifier = Modifier.size(56.dp),
        ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.recipe_servings_more)) }
    }
    if (isError) FormHint(stringResource(R.string.error_recipe_servings), isError = true)
}

/** What the photos of a new recipe do. */
class RecipeEditPhotoActions(
    val onAdd: (List<String>) -> Unit,
    val onRemove: (String) -> Unit,
)

/**
 * « Photos du plat », on a new recipe: up to [RECIPE_PHOTOS] chosen with the Android photo picker
 * (no permission to ask), sent right after the recipe is saved.
 */
@Composable
private fun Photos(state: RecipeEditUiState.Editing, actions: RecipeEditPhotoActions) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(RECIPE_PHOTOS)) { uris ->
        keepPhotoAccess(context, uris)
        if (uris.isNotEmpty()) actions.onAdd(uris.map { it.toString() })
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    if (state.photos.isEmpty()) {
        // No photo yet: the dashed box of the maquette.
        if (state.canAddPhotos) AddPhotoBox(onClick = pick)
        return
    }
    FormTile(Icons.Outlined.PhotoLibrary, stringResource(R.string.recipe_photos_title)) {
        state.photos.forEachIndexed { index, uri ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = uri,
                    contentDescription = stringResource(R.string.recipe_edit_photo, index + 1, state.photos.size),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                if (!state.isSaving) {
                    OutlinedButton(onClick = { actions.onRemove(uri) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.capsule_edit_photo_remove, index + 1))
                    }
                }
            }
        }
        if (state.canAddPhotos) {
            val left = RECIPE_PHOTOS - state.photos.size
            OutlinedButton(
                onClick = pick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.capsule_edit_add_photos, left))
            }
        }
    }
}

/** « Ajouter une photo du plat » in a dashed box, at the top of a new recipe. */
@Composable
private fun AddPhotoBox(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val outline = colors.outline
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .clip(shape)
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Outlined.AddAPhoto, contentDescription = null, tint = colors.onSurfaceVariant)
        Text(stringResource(R.string.recipe_edit_add_photo), style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
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
    /** False when the tile already says it. */
    showLabel: Boolean = true,
) {
    val error = kinds.firstOrNull { it in errors }
    val label = stringResource(labelRes)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showLabel) FormLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            isError = error != null,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 4,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = keyboard,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
        (error?.let { errorRes(it) } ?: help)?.let { FormHint(stringResource(it), isError = error != null) }
    }
}

private fun errorRes(error: RecipeFormError): Int = when (error) {
    RecipeFormError.TitleMissing -> R.string.error_recipe_title_missing
    RecipeFormError.TitleTooLong, RecipeFormError.OriginTooLong -> R.string.error_event_title_too_long
    RecipeFormError.ServingsInvalid -> R.string.error_recipe_servings
    RecipeFormError.IngredientsMissing -> R.string.error_recipe_ingredients_missing
    RecipeFormError.StepsMissing -> R.string.error_recipe_steps_missing
    RecipeFormError.IngredientsTooLong, RecipeFormError.StepsTooLong, RecipeFormError.StoryTooLong -> R.string.error_recipe_too_long
}

/**
 * « De qui vient la recette ? »: a member of the family, by household, or nobody in particular (then
 * a free origin). Rounds, as everywhere else (refonte lot 4), instead of a dialog of radio buttons.
 */
@Composable
private fun OriginRounds(state: RecipeEditUiState.Editing, onChange: ((RecipeForm) -> RecipeForm) -> Unit) {
    val chosen = state.form.originMemberId
    val pick: (String) -> Unit = { key -> onChange { it.copy(originMemberId = key.takeIf { k -> k != NOBODY }) } }
    PeopleRow(
        choices = listOf(
            PersonChoice(NOBODY, stringResource(R.string.recipe_origin_none), selected = chosen == null, icon = Icons.Outlined.PersonOff),
        ),
        multiple = false,
        onToggle = pick,
    )
    if (state.groups.isEmpty()) {
        PeopleRow(
            choices = state.members.map { PersonChoice(it.id, it.firstName, selected = it.id == chosen, memberId = it.id) },
            multiple = false,
            onToggle = pick,
        )
    }
    // By household (règle de Kévin, 17/11): one choice, so no box for the household.
    state.groups.forEach { group ->
        if (group.name.isNotBlank()) FormHint(group.name, modifier = Modifier.semantics { heading() })
        PeopleRow(
            choices = group.members.map { PersonChoice(it.id, it.firstName, selected = it.id == chosen, memberId = it.id) },
            multiple = false,
            onToggle = pick,
        )
    }
}

/** The key of « Personne de la famille » among the rounds; the others are member ids. */
private const val NOBODY = ""

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
