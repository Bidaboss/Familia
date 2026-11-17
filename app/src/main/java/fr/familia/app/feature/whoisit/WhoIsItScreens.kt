package fr.familia.app.feature.whoisit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.familia.app.R
import fr.familia.app.core.model.WhoIsItAnswer
import fr.familia.app.core.model.WhoIsItGuess
import fr.familia.app.core.model.WhoIsItPost
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.photo.keepPhotoAccess
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews

/** What one can do on a photo. */
data class WhoIsItActions(
    val onWho: (String) -> Unit = {},
    val onYear: (String) -> Unit = {},
    val onGuess: () -> Unit = {},
    val onDontKnow: () -> Unit = {},
)

/**
 * The photo, its hint, then either the guess (name and year) or, once guessed, the answer and
 * everyone's guesses. Used by the Accueil card and by the page of a photo. No score anywhere.
 */
@Composable
fun ColumnScope.WhoIsItPostContent(state: WhoIsItPostUiState.Ready, actions: WhoIsItActions) {
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    StorageImage(
        path = state.post.photoPath,
        contentDescription = stringResource(R.string.who_is_it_photo),
        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)),
        contentScale = ContentScale.Fit,
        showLoading = true,
    )
    if (state.post.hint.isNotBlank()) Text(state.post.hint, style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.who_is_it_by, state.post.authorName), style = MaterialTheme.typography.bodyMedium, color = muted)
    if (state.revealed) {
        state.answer?.let { Answer(it) }
        if (state.guesses.isNotEmpty()) {
            Text(stringResource(R.string.who_is_it_guesses), style = MaterialTheme.typography.titleSmall)
            state.guesses.forEach { Text(guessLine(it), style = MaterialTheme.typography.bodyMedium) }
        } else if (state.isAuthor) {
            Text(stringResource(R.string.who_is_it_no_guess_yet), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
    } else if (state.canGuess) {
        OutlinedTextField(
            value = state.who,
            onValueChange = actions.onWho,
            label = { Text(stringResource(R.string.who_is_it_guess_who)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            enabled = !state.sending,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.year,
            onValueChange = actions.onYear,
            label = { Text(stringResource(R.string.who_is_it_guess_year)) },
            singleLine = true,
            isError = state.yearInvalid,
            supportingText = { Text(stringResource(if (state.yearInvalid) R.string.who_is_it_year_invalid else R.string.who_is_it_year_help)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !state.sending,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.failed) {
            Text(stringResource(R.string.who_is_it_guess_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = actions.onGuess,
            enabled = !state.sending && state.who.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.who_is_it_guess)) }
        ClayLink(stringResource(R.string.who_is_it_dont_know), onClick = actions.onDontKnow)
    }
}

@Composable
private fun Answer(answer: WhoIsItAnswer) {
    ClayCardTitle(stringResource(R.string.who_is_it_answer), style = MaterialTheme.typography.titleMedium)
    Text(
        answer.year?.let { stringResource(R.string.who_is_it_answer_year, answer.who, it) } ?: answer.who,
        style = MaterialTheme.typography.bodyLarge,
    )
}

/** « Lou : Mamie Jeanne, vers 1962 », « Noé ne savait pas ». */
@Composable
private fun guessLine(guess: WhoIsItGuess): String = when {
    guess.who.isBlank() -> stringResource(R.string.who_is_it_guess_none, guess.name)
    guess.year != null -> stringResource(R.string.who_is_it_guess_line_year, guess.name, guess.who, guess.year)
    else -> stringResource(R.string.who_is_it_guess_line, guess.name, guess.who)
}

/** The page of one photo, with « Retirer » for its author or the admin principal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhoIsItPostScreen(state: WhoIsItPostUiState, actions: WhoIsItActions, onDelete: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_who_is_it), maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { BackButton(onClick = onBack) }) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                WhoIsItPostUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                WhoIsItPostUiState.Missing -> EmptyState(
                    icon = Icons.Outlined.PhotoAlbum,
                    title = stringResource(R.string.who_is_it_missing_title),
                    body = stringResource(R.string.who_is_it_missing_body),
                )
                is WhoIsItPostUiState.Ready -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
                ) {
                    ClayCard(Modifier.fillMaxWidth(), emphasis = true) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { WhoIsItPostContent(state, actions) }
                    }
                    if (state.canDelete) RemoveButton(onDelete)
                }
            }
        }
    }
}

@Composable
private fun RemoveButton(onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.who_is_it_remove))
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.who_is_it_remove_title)) },
            text = { Text(stringResource(R.string.who_is_it_remove_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.who_is_it_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_keep)) }
            },
        )
    }
}

/** Every photo, newest first, with « Publier une photo ». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhoIsItListScreen(state: WhoIsItListUiState, onOpen: (String) -> Unit, onAdd: (() -> Unit)?, onBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_who_is_it), maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { BackButton(onClick = onBack) }) },
        floatingActionButton = {
            if (onAdd != null) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.who_is_it_add)) },
                    text = { Text(stringResource(R.string.who_is_it_add)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                WhoIsItListUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                WhoIsItListUiState.Error -> EmptyState(
                    icon = Icons.Outlined.PhotoAlbum,
                    title = stringResource(R.string.who_is_it_error_title),
                    body = stringResource(R.string.who_is_it_error_body),
                )
                is WhoIsItListUiState.Ready -> if (state.posts.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.PhotoAlbum,
                        title = stringResource(R.string.who_is_it_empty_title),
                        body = stringResource(R.string.who_is_it_empty_body),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
                    ) {
                        items(state.posts, key = { it.id }) { post -> PostRow(post) { onOpen(post.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostRow(post: WhoIsItPost, onClick: () -> Unit) {
    ClayCard(Modifier.fillMaxWidth(), onClick = onClick, onClickLabel = stringResource(R.string.who_is_it_open)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StorageImage(post.thumbPath, null, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    post.hint.ifBlank { stringResource(R.string.feature_who_is_it) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.who_is_it_by, post.authorName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FamiliaClay.colors.onTintVariant(ClayTint.Plain),
                )
            }
        }
    }
}

data class WhoIsItEditActions(
    val onPhoto: (String?) -> Unit = {},
    val onHint: (String) -> Unit = {},
    val onWho: (String) -> Unit = {},
    val onYear: (String) -> Unit = {},
    val onPublish: () -> Unit = {},
)

/** « Publier une photo »: the photo, a hint, and the answer the family will see after guessing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhoIsItEditScreen(state: WhoIsItEditUiState.Editing, actions: WhoIsItEditActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            keepPhotoAccess(context, listOf(it))
            actions.onPhoto(it.toString())
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.who_is_it_add), maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { BackButton(onClick = onBack) }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.who_is_it_edit_intro), style = MaterialTheme.typography.bodyLarge)
            state.form.photo?.let {
                AsyncImage(
                    model = it,
                    contentDescription = stringResource(R.string.who_is_it_photo),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !state.sending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (state.form.photo == null) R.string.who_is_it_pick_photo else R.string.who_is_it_change_photo))
            }
            if (WhoIsItFormError.PhotoMissing in state.errors) ErrorText(stringResource(R.string.who_is_it_photo_missing))
            OutlinedTextField(
                value = state.form.hint,
                onValueChange = actions.onHint,
                label = { Text(stringResource(R.string.who_is_it_hint)) },
                supportingText = { Text(stringResource(R.string.who_is_it_hint_help)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                enabled = !state.sending,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.form.who,
                onValueChange = actions.onWho,
                label = { Text(stringResource(R.string.who_is_it_answer_who)) },
                isError = WhoIsItFormError.WhoMissing in state.errors,
                supportingText = { Text(stringResource(R.string.who_is_it_answer_who_help)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                enabled = !state.sending,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.form.year,
                onValueChange = actions.onYear,
                label = { Text(stringResource(R.string.who_is_it_answer_year_label)) },
                isError = WhoIsItFormError.YearInvalid in state.errors,
                supportingText = {
                    Text(stringResource(if (WhoIsItFormError.YearInvalid in state.errors) R.string.who_is_it_year_invalid else R.string.who_is_it_year_help))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !state.sending,
                modifier = Modifier.fillMaxWidth(),
            )
            state.failure?.let {
                ErrorText(
                    stringResource(
                        when (it) {
                            WhoIsItSaveFailure.NeedsNetwork -> R.string.who_is_it_needs_network
                            WhoIsItSaveFailure.PhotoUnreadable -> R.string.who_is_it_photo_unreadable
                            WhoIsItSaveFailure.Failed -> R.string.who_is_it_failed
                        },
                    ),
                )
            }
            Button(onClick = actions.onPublish, enabled = !state.sending, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(if (state.sending) R.string.who_is_it_sending else R.string.who_is_it_publish))
            }
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

/** Fictional data for previews and screenshots: no real first name. */
internal fun previewWhoIsIt(revealed: Boolean) = WhoIsItPostUiState.Ready(
    post = WhoIsItPost("w1", "a", "Camille", "Un mariage, au printemps", null),
    answer = if (revealed) WhoIsItAnswer("Mamie Jeanne et Papi Louis", 1961) else null,
    guesses = if (revealed) listOf(WhoIsItGuess("b", "Inès", "Mamie Jeanne", 1965), WhoIsItGuess("c", "Noé", "", null)) else emptyList(),
    isAuthor = false,
    canDelete = false,
    canGuess = true,
)

@FamiliaPreviews
@Composable
private fun WhoIsItGuessPreview() {
    FamiliaTheme { Surface { WhoIsItPostScreen(previewWhoIsIt(revealed = false), WhoIsItActions(), {}, {}) } }
}

@FamiliaPreviews
@Composable
private fun WhoIsItRevealPreview() {
    FamiliaTheme { Surface { WhoIsItPostScreen(previewWhoIsIt(revealed = true), WhoIsItActions(), {}, {}) } }
}
