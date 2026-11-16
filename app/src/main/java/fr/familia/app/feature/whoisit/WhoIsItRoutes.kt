package fr.familia.app.feature.whoisit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.FamiliaApplication
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.feature.home.HomeChip
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable

@Serializable
data object WhoIsItDestination

@Serializable
data class WhoIsItPostDestination(val postId: String)

@Serializable
data object WhoIsItEditDestination

@Composable
private fun rememberMemory(): WhoIsItMemory {
    val context = LocalContext.current
    return remember(context) { WhoIsItPreferences(context.applicationContext) }
}

@Composable
private fun postViewModel(container: AppContainer?, currentMember: Member, postId: String): WhoIsItPostViewModel {
    val memory = rememberMemory()
    val viewingAs = LocalViewAs.current.viewed != null
    val isOwner = LocalFeatureFlags.current.isOwner
    return viewModel(
        key = "whoIsIt-$postId-${currentMember.id}-$viewingAs",
        factory = viewModelFactory {
            initializer {
                WhoIsItPostViewModel(container?.whoIsItRepository, currentMember.id, currentMember.firstName, postId, isOwner, canWrite = !viewingAs, memory = memory)
            }
        },
    )
}

private fun actionsOf(viewModel: WhoIsItPostViewModel) =
    WhoIsItActions(onWho = viewModel::setWho, onYear = viewModel::setYear, onGuess = viewModel::guess, onDontKnow = viewModel::dontKnow)

@Composable
fun WhoIsItListRoute(container: AppContainer?, onOpen: (String) -> Unit, onAdd: () -> Unit, onBack: () -> Unit) {
    val viewModel: WhoIsItListViewModel = viewModel(factory = viewModelFactory { initializer { WhoIsItListViewModel(container?.whoIsItRepository) } })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val viewingAs = LocalViewAs.current.viewed != null
    WhoIsItListScreen(state, onOpen, onAdd = if (viewingAs) null else onAdd, onBack = onBack)
}

/** A removed photo closes its page. */
@Composable
fun WhoIsItPostRoute(container: AppContainer?, currentMember: Member, postId: String, onBack: () -> Unit) {
    val viewModel = postViewModel(container, currentMember, postId)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is WhoIsItPostUiState.Ready) seen = true
        if (seen && state == WhoIsItPostUiState.Missing) onBack()
    }
    WhoIsItPostScreen(state, actionsOf(viewModel), onDelete = viewModel::delete, onBack = onBack)
}

/** The published photo opens in place of the form. */
@Composable
fun WhoIsItEditRoute(container: AppContainer?, currentMember: Member, onPublished: (String) -> Unit, onBack: () -> Unit) {
    val online = (LocalContext.current.applicationContext as? FamiliaApplication)?.networkMonitor?.isOnline ?: flowOf(true)
    val viewModel: WhoIsItEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                WhoIsItEditViewModel(
                    container?.whoIsItRepository,
                    container?.photoUploader,
                    currentMember.id,
                    currentMember.firstName,
                    savedState = createSavedStateHandle(),
                    isOnline = online,
                    storage = container?.photoStorage,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) { (state as? WhoIsItEditUiState.Done)?.let { onPublished(it.postId) } }
    val editing = state as? WhoIsItEditUiState.Editing ?: return
    val actions = WhoIsItEditActions(
        onPhoto = viewModel::setPhoto,
        onHint = viewModel::setHint,
        onWho = viewModel::setWho,
        onYear = viewModel::setYear,
        onPublish = viewModel::publish,
    )
    WhoIsItEditScreen(editing, actions, onBack)
}

// --- The Accueil (DESIGN-11: a chip that opens its card under the row) ----------------------------

@Composable
private fun homeViewModel(container: AppContainer?, currentMember: Member): WhoIsItHomeViewModel {
    val memory = rememberMemory()
    val viewingAs = LocalViewAs.current.viewed != null
    return viewModel(
        key = "whoIsItHome-${currentMember.id}-$viewingAs",
        factory = viewModelFactory { initializer { WhoIsItHomeViewModel(container?.whoIsItRepository, currentMember.id, memory, canDismiss = !viewingAs) } },
    )
}

/** Only while a photo of someone else waits to be guessed. */
@Composable
fun WhoIsItHomeChip(container: AppContainer?, currentMember: Member, selected: Boolean, onToggle: () -> Unit) {
    val postId by homeViewModel(container, currentMember).postId.collectAsStateWithLifecycle()
    if (postId == null) return
    HomeChip(label = stringResource(R.string.feature_who_is_it), onClick = onToggle, selected = selected)
}

/** The photo to guess, then its answer, with « Ne plus me montrer ». */
@Composable
fun WhoIsItHomeCard(container: AppContainer?, currentMember: Member, modifier: Modifier = Modifier) {
    val home = homeViewModel(container, currentMember)
    val postId by home.postId.collectAsStateWithLifecycle()
    val id = postId ?: return
    val viewModel = postViewModel(container, currentMember, id)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state as? WhoIsItPostUiState.Ready ?: return
    ClayCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WhoIsItPostContent(ready, actionsOf(viewModel))
            if (home.canDismiss) ClayLink(stringResource(R.string.who_is_it_dismiss), onClick = { home.dismiss(id) })
        }
    }
}
