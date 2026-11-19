package fr.familia.app.feature.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.R
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.RecipeRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.RecipeComment
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.ui.ClayCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A comment, and whether the current member may remove it (its author, the recipe's, or the admin). */
data class CommentRow(val comment: RecipeComment, val canRemove: Boolean)

data class RecipeCommentsUiState(
    val comments: List<CommentRow> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val problem: CommentProblem? = null,
) {
    val canSend: Boolean get() = draft.isNotBlank() && draft.trim().length <= RecipeComment.MAX_TEXT && !isSending
}

enum class CommentProblem { SendFailed, RemoveFailed }

/**
 * The comments under a recipe (PRD, récit 38): every member reads them and writes their own. The
 * draft is kept in [savedState], so a comment half written survives Android stopping the app.
 */
class RecipeCommentsViewModel(
    private val recipes: RecipeRepository?,
    members: MemberRepository?,
    private val currentMemberId: String,
    private val recipeId: String,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val draft = savedState.getStateFlow(DRAFT, "")
    private val sending = MutableStateFlow(false)
    private val problem = MutableStateFlow<CommentProblem?>(null)
    private var me: Member? = null

    val uiState: StateFlow<RecipeCommentsUiState> =
        if (recipes == null || members == null) {
            MutableStateFlow(RecipeCommentsUiState()) // Firebase not configured (previews).
        } else {
            combine(recipes.observeComments(recipeId), recipes.observeRecipe(recipeId), members.observeFamily(), draft, combine(sending, problem) { isSending, issue -> isSending to issue }) {
                    list, recipe, family, text, (isSending, issue) ->
                val current = family.members.firstOrNull { it.id == currentMemberId }
                me = current
                val manages = recipe != null && current != null && canManageRecipe(recipe, current, family)
                RecipeCommentsUiState(
                    comments = list.map { CommentRow(it, manages || it.authorId == currentMemberId) },
                    draft = text,
                    isSending = isSending,
                    problem = issue,
                )
            }
                .catch { e -> if (e is CancellationException) throw e else emit(RecipeCommentsUiState()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeCommentsUiState())
        }

    fun type(text: String) {
        savedState[DRAFT] = text.take(RecipeComment.MAX_TEXT)
        problem.value = null
    }

    /** Sends the draft; offline, Firestore keeps it and sends it later. */
    fun send() {
        val repo = recipes ?: return
        val author = me ?: return
        val state = uiState.value
        if (!state.canSend) return
        val text = state.draft.trim()
        sending.value = true
        viewModelScope.launch {
            try {
                withTimeoutOrNull(writeTimeoutMillis) { repo.addComment(recipeId, text, author) }
                savedState[DRAFT] = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem.value = CommentProblem.SendFailed
            } finally {
                sending.value = false
            }
        }
    }

    fun remove(row: CommentRow) {
        val repo = recipes ?: return
        if (!row.canRemove) return
        viewModelScope.launch {
            try {
                withTimeoutOrNull(writeTimeoutMillis) { repo.deleteComment(recipeId, row.comment.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem.value = CommentProblem.RemoveFailed
            }
        }
    }

    private companion object {
        const val DRAFT = "recipe_comment_draft"
    }
}

/** The « Commentaires » part of a recipe: the comments, then a field and « Publier ». */
@Composable
fun RecipeCommentsSection(state: RecipeCommentsUiState, onType: (String) -> Unit, onSend: () -> Unit, onRemove: (CommentRow) -> Unit) {
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.comments.isEmpty()) {
            Text(stringResource(R.string.recipe_comments_empty), style = MaterialTheme.typography.bodyLarge, color = muted)
        }
        state.comments.forEach { row ->
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Text(row.comment.authorName, style = MaterialTheme.typography.titleSmall)
                Text(row.comment.text, style = MaterialTheme.typography.bodyLarge)
                if (row.canRemove) {
                    TextButton(onClick = { removing = row.comment.id }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.recipe_comment_remove))
                    }
                }
            }
        }
        state.problem?.let {
            Text(
                stringResource(if (it == CommentProblem.SendFailed) R.string.recipe_comment_send_failed else R.string.recipe_comment_remove_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedTextField(
            value = state.draft,
            onValueChange = onType,
            label = { Text(stringResource(R.string.recipe_comment_field)) },
            supportingText = fr.familia.app.feature.events.comments.commentCount(state.draft.length, RecipeComment.MAX_TEXT),
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = onSend, enabled = state.canSend, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.recipe_comment_send))
        }
    }
    val target = state.comments.firstOrNull { it.comment.id == removing }
    if (target != null) {
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.recipe_comment_remove_title)) },
            text = { Text(target.comment.text) },
            confirmButton = {
                TextButton(onClick = { removing = null; onRemove(target) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.recipe_remove_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_keep)) }
            },
        )
    }
}

/** Fictional comments for previews and screenshots. */
internal fun previewRecipeComments(): RecipeCommentsUiState = RecipeCommentsUiState(
    comments = listOf(
        CommentRow(RecipeComment("c1", "Je la fais avec un peu moins de sucre, c'est parfait.", "b", "Inès", null), canRemove = false),
        CommentRow(RecipeComment("c2", "Avec des poires aussi, c'est très bon !", "a", "Camille", null), canRemove = true),
    ),
)
