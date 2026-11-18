package fr.familia.app.feature.goodnews.detail

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Reaction
import fr.familia.app.core.share.ShareButton
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.feature.goodnews.GoodNewsActionFailedDialog
import fr.familia.app.feature.goodnews.GoodNewsCard
import fr.familia.app.feature.goodnews.RemoveGoodNewsDialog
import fr.familia.app.feature.goodnews.previewGoodNewsItems
import java.time.Instant

/** One good news with its photos at full size, and the cards of a birthday under it ([wishes], récit 36). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoodNewsDetailScreen(
    state: GoodNewsDetailUiState,
    onReact: (Reaction) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onHide: (Boolean) -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    wishes: (@Composable (Member) -> Unit)? = null,
    /** « Partager » (récit 31), while its module is on. */
    onShare: (() -> Unit)? = null,
    /** Its comments (récit 67), while the fil d'actualité is on. */
    comments: (@Composable (GoodNews) -> Unit)? = null,
) {
    var removing by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goodnews_detail_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { onShare?.let { ShareButton(onClick = it) } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                GoodNewsDetailUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                GoodNewsDetailUiState.Missing -> EmptyState(
                    icon = Icons.Outlined.Celebration,
                    title = stringResource(R.string.goodnews_missing_title),
                    body = stringResource(R.string.goodnews_missing_body),
                    action = {
                        Button(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_back)) }
                    },
                )
                is GoodNewsDetailUiState.Success -> Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    GoodNewsCard(
                        item = state.item,
                        now = state.now,
                        full = true,
                        onReact = onReact,
                        onEdit = onEdit,
                        onRemove = { removing = true },
                        onHide = onHide,
                    )
                    val person = state.birthdayPerson
                    if (person != null && wishes != null) wishes(person)
                    comments?.invoke(state.item.news)
                }
            }
        }
    }
    if (removing) RemoveGoodNewsDialog(onConfirm = { removing = false; onRemove() }, onDismiss = { removing = false })
    if ((state as? GoodNewsDetailUiState.Success)?.actionFailed == true) GoodNewsActionFailedDialog(onDismissError)
}

@FamiliaPreviews
@Composable
private fun GoodNewsDetailScreenPreview() {
    FamiliaTheme {
        Surface {
            GoodNewsDetailScreen(
                GoodNewsDetailUiState.Success(previewGoodNewsItems()[1], Instant.parse("2026-11-21T16:00:00Z")),
                {}, {}, {}, {}, {}, {},
            )
        }
    }
}
