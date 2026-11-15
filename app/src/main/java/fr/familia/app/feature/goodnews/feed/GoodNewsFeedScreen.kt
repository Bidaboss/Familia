package fr.familia.app.feature.goodnews.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Reaction
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.goodnews.GoodNewsActionFailedDialog
import fr.familia.app.feature.goodnews.GoodNewsCard
import fr.familia.app.feature.goodnews.RemoveGoodNewsDialog
import fr.familia.app.feature.goodnews.previewGoodNewsItems
import java.time.Instant

/** All the good news, newest first (PRD, récit 32). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoodNewsFeedScreen(
    state: GoodNewsFeedUiState,
    onPublish: () -> Unit,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onReact: (String, Reaction) -> Unit,
    onRemove: (String) -> Unit,
    onHide: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
) {
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.goodnews_title)) }, navigationIcon = { BackButton(onClick = onBack) })
        },
        floatingActionButton = {
            if (state is GoodNewsFeedUiState.Success) {
                ExtendedFloatingActionButton(
                    onClick = onPublish,
                    icon = { Icon(Icons.Outlined.AddAPhoto, contentDescription = null) },
                    text = { Text(stringResource(R.string.goodnews_publish)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                GoodNewsFeedUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                GoodNewsFeedUiState.Error -> EmptyState(
                    icon = Icons.Outlined.Celebration,
                    title = stringResource(R.string.goodnews_error_title),
                    body = stringResource(R.string.goodnews_error_body),
                    action = {
                        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_retry)) }
                    },
                )
                is GoodNewsFeedUiState.Success ->
                    if (state.items.isEmpty()) {
                        EmptyState(
                            icon = Icons.Outlined.Celebration,
                            title = stringResource(R.string.goodnews_empty_title),
                            body = stringResource(R.string.goodnews_empty_body),
                        )
                    } else {
                        Feed(state, onOpen, onEdit, onReact, { removing = it }, onHide, onLoadMore)
                    }
            }
        }
    }
    removing?.let { id ->
        RemoveGoodNewsDialog(onConfirm = { removing = null; onRemove(id) }, onDismiss = { removing = null })
    }
    if ((state as? GoodNewsFeedUiState.Success)?.actionFailed == true) GoodNewsActionFailedDialog(onDismissError)
}

@Composable
private fun Feed(
    state: GoodNewsFeedUiState.Success,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onReact: (String, Reaction) -> Unit,
    onAskRemove: (String) -> Unit,
    onHide: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    // The bottom padding keeps the last card above the « Publier » button.
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.items, key = { it.news.id }) { item ->
            val id = item.news.id
            GoodNewsCard(
                item = item,
                now = state.now,
                full = false,
                onReact = { onReact(id, it) },
                onOpen = { onOpen(id) },
                onEdit = { onEdit(id) },
                onRemove = { onAskRemove(id) },
                onHide = { onHide(id) },
            )
        }
        if (state.canLoadMore) {
            item(key = "more") {
                OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.goodnews_load_more))
                }
            }
        }
    }
}

@FamiliaPreviews
@Composable
private fun GoodNewsFeedScreenPreview() {
    FamiliaTheme {
        Surface {
            GoodNewsFeedScreen(
                GoodNewsFeedUiState.Success(previewGoodNewsItems(), canLoadMore = true, now = Instant.parse("2026-11-21T16:00:00Z")),
                {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {},
            )
        }
    }
}
