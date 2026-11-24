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
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.isOwner
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.feature.events.comments.CommentsSection
import fr.familia.app.feature.trips.money.StepMoneyTile
import fr.familia.app.feature.trips.phrases.StepPhrasesTile
import fr.familia.app.feature.trips.photos.stepAiSubject
import fr.familia.app.feature.trips.safety.StepSafetyTile

/** « Le mot de l'étape » of [stepId]: closes itself once the word is published, changed or taken back. */
@Composable
fun StepWordRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
    stepId: String,
    onEditStep: () -> Unit,
    onBack: () -> Unit,
    /** « Phrases utiles » of the country of the step (récit 112, règle 44). */
    onOpenPhrases: ((country: String) -> Unit)? = null,
    /** « Sécurité » of the country of the step, with « Prévenir ma famille » (récit 112, règle 43). */
    onOpenSafety: ((country: String) -> Unit)? = null,
    /** « Monnaie » of the trip, from a step outside the euro zone (récit 112, règle 42). */
    onOpenMoney: (() -> Unit)? = null,
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
    val ready = state as? StepWordUiState.Ready
    StepWordScreen(state, actions, onSite = {
        if (ready != null) {
            val aiSubject = remember(ready.step, ready.album, ready.word) { stepAiSubject(ready.step, ready.album, ready.word) }
            StepFilm(container, tripId, stepId, ready.album, ready.trip, currentMember.takeIf { !readOnly }, ready.step.place, aiSubject)
            val lat = ready.step.lat
            val lng = ready.step.lng
            // Règle 45: the weather and the local time first, the anecdote at the bottom.
            if (lat != null && lng != null) WeatherTimeTile(lat, lng, container?.placeWeather)
            // Règles 39, 42, 43 et 44: the phrases, the emergency numbers and the currency of the country
            // are on the page of a step abroad, no longer round buttons of the trip.
            StepPhrasesTile(container, ready.step, onOpenPhrases)
            StepSafetyTile(container, ready.step, onOpenSafety)
            StepMoneyTile(container, ready.step, onOpenMoney)
            PlaceArticleTile(ready.step.place, container?.placeArticles)
            if (lat != null && lng != null) NearbyTile(lat, lng, container?.nearbyPlaces)
            PlaceLinksTile(ready.step.place)
            PlaceAnecdoteTile(ready.step.place, container?.placeAnecdotes)
        }
    }) {
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
            // No function listens to words/*/comments yet: nobody is warned, so no « Ne plus être prévenu ».
            canMute = false,
        )
    }
}

/** Who takes a comment off a word: its author, the author of the word, the admin principal. */
internal fun canRemoveWordComment(commentAuthorId: String, wordAuthorId: String?, meId: String, isOwner: Boolean): Boolean =
    isOwner || commentAuthorId == meId || wordAuthorId == meId
