package fr.familia.app.feature.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.feature.month.monthName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import java.time.YearMonth

/** « Photos du fil » of one month (récit 71), from Albums. */
@Serializable
data class FeedPhotosDestination(val year: Int, val month: Int)

data class FeedPhotosUiState(val loading: Boolean = true, val photos: List<FeedPhoto> = emptyList())

/** The photos of the fil of [month], read live; nothing is written here. */
class FeedPhotosViewModel(goodNews: GoodNewsRepository?, memberId: String, month: YearMonth) : ViewModel() {
    val uiState: StateFlow<FeedPhotosUiState> =
        if (goodNews == null) {
            MutableStateFlow(FeedPhotosUiState(loading = false)) // Firebase not configured (previews).
        } else {
            val from = month.atDay(1).atStartOfDay(FAMILY_ZONE).toInstant()
            val to = month.plusMonths(1).atDay(1).atStartOfDay(FAMILY_ZONE).toInstant()
            goodNews.observeBetween(from, to)
                .map { news -> FeedPhotosUiState(false, feedPhotoAlbums(news, memberId).firstOrNull { it.month == month }?.photos.orEmpty()) }
                .catch { e -> if (e is CancellationException) throw e else emit(FeedPhotosUiState(loading = false)) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedPhotosUiState())
        }
}

@Composable
fun FeedPhotosRoute(container: AppContainer?, currentMember: Member, month: YearMonth, onBack: () -> Unit, onOpenNews: (String) -> Unit) {
    val viewModel: FeedPhotosViewModel = viewModel(
        key = "feedPhotos-$month",
        factory = viewModelFactory { initializer { FeedPhotosViewModel(container?.goodNewsRepository, currentMember.id, month) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FeedPhotosScreen(state, month, onBack, onOpenNews)
}

/** A grid of 3: each photo opens its Bonne nouvelle, with its comments. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedPhotosScreen(state: FeedPhotosUiState, month: YearMonth, onBack: () -> Unit, onOpenNews: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.albums_feed_screen_title, monthName(month).lowercase(), month.year)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        val open = stringResource(R.string.albums_feed_open)
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.photos.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.albums_feed_empty),
                    body = stringResource(R.string.albums_subtitle),
                    action = { ClayLink(stringResource(R.string.empty_go_back), onClick = onBack) },
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.photos.chunked(3).forEachIndexed { line, row ->
                        item(key = "line-$line") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { photo ->
                                    StorageImage(
                                        photo.thumbnailPath,
                                        stringResource(R.string.albums_feed_photo, photo.authorName),
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(role = Role.Button, onClickLabel = open) { onOpenNews(photo.newsId) },
                                    )
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
