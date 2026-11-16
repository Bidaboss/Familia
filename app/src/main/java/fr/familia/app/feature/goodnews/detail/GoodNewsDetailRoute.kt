package fr.familia.app.feature.goodnews.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.BirthdayCard
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.share.goodNewsShare
import fr.familia.app.core.share.share
import fr.familia.app.core.share.sharePhoto
import fr.familia.app.core.ui.LocalFeatureFlags
import kotlinx.coroutines.launch
import fr.familia.app.feature.wishes.BirthdayCardsSection

/** Connects a good news to its ViewModel, and goes back once it is removed. */
@Composable
fun GoodNewsDetailRoute(
    container: AppContainer?,
    currentMember: Member,
    postId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: GoodNewsDetailViewModel = viewModel(
        key = "goodNews-$postId",
        factory = viewModelFactory {
            initializer {
                GoodNewsDetailViewModel(container?.goodNewsRepository, container?.memberRepository, currentMember.id, postId)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wishesOn = LocalFeatureFlags.current.isOn(Feature.BirthdayWishes)
    val shareOn = LocalFeatureFlags.current.isOn(Feature.Share)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var wasShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is GoodNewsDetailUiState.Success) wasShown = true
        if (state == GoodNewsDetailUiState.Missing && wasShown) onBack()
    }
    GoodNewsDetailScreen(
        state = state,
        onReact = viewModel::react,
        onEdit = onEdit,
        onRemove = viewModel::remove,
        onHide = viewModel::setHidden,
        onDismissError = viewModel::dismissError,
        onBack = onBack,
        wishes = if (wishesOn) ({ person -> BirthdayCardsSection(container, currentMember, person, BirthdayCard.yearOfNews(postId)) }) else null,
        onShare = (state as? GoodNewsDetailUiState.Success)?.takeIf { shareOn }?.let { shown ->
            {
                // One share at a time: the photo takes a moment to come.
                if (!sharing) {
                    sharing = true
                    scope.launch {
                        try {
                            val news = shown.item.news
                            val photo = if (shown.photoShareable) sharePhoto(container?.photoStorage, news.photoPath(news.photoIds.first())) else null
                            val whose = shown.item.forName ?: shown.item.author?.firstName ?: news.authorName
                            share(context, goodNewsShare(context, news, whose, photo))
                        } finally {
                            sharing = false
                        }
                    }
                }
            }
        },
    )
}
