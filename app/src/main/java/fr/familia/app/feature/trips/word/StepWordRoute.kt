package fr.familia.app.feature.trips.word

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.isOwner
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.feature.events.comments.CommentsSection
import fr.familia.app.core.model.Member

/** « Le mot de l'étape » of [stepId]: closes itself once the word is published, changed or taken back. */
@Composable
fun StepWordRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
    stepId: String,
    onEditStep: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: StepWordViewModel = viewModel(
        key = "step-word-$tripId-$stepId",
        factory = viewModelFactory {
            initializer {
                StepWordViewModel(
                    container?.tripRepository,
                    container?.tripWordRepository,
                    container?.albumPhotoRepository,
                    container?.memberRepository,
                    currentMember.id,
                    tripId,
                    stepId,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onBack() }
    val actions = remember(viewModel, onEditStep, onBack) {
        StepWordActions(
            onText = viewModel::setText,
            onTogglePhoto = viewModel::togglePhoto,
            onPublish = viewModel::publish,
            onRemove = viewModel::remove,
            onHeart = viewModel::toggleHeart,
            onEditStep = onEditStep,
            onRetry = viewModel::retry,
            onBack = onBack,
        )
    }
    val readOnly = LocalViewAs.current.viewed != null
    val wordAuthorId = (state as? StepWordUiState.Ready)?.word?.authorId
    StepWordScreen(state, actions) {
        CommentsSection(
            repository = container?.tripWordRepository?.comments(tripId, stepId),
            container = container,
            currentMember = currentMember,
            key = "word-comments-$tripId-$stepId",
            parentId = stepId,
            readOnly = readOnly,
            // The author of the comment, the author of the word and the admin principal (the rules check the same).
            canRemove = { family, comment, me -> canRemoveWordComment(comment.authorId, wordAuthorId, me.id, family.isOwner(me)) },
            empty = R.string.word_comments_empty,
        )
    }
}

/** Who takes a comment off a word: its author, the author of the word, the admin principal. */
internal fun canRemoveWordComment(commentAuthorId: String, wordAuthorId: String?, meId: String, isOwner: Boolean): Boolean =
    isOwner || commentAuthorId == meId || wordAuthorId == meId
