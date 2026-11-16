package fr.familia.app.core.whatsnew

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.viewas.LocalViewAs
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable

/** « Quoi de neuf ? » again, from the help screen and the avatar menu: every version, newest first. */
@Serializable
data object WhatsNewDestination

@Composable
fun WhatsNewRoute(
    container: AppContainer?,
    currentMember: Member,
    installedVersion: Long,
    onOpen: (WhatsNewTarget) -> Unit,
    onBack: () -> Unit,
) {
    val configs = remember(container) { container?.appConfigRepository?.observeAppConfig()?.catch { } ?: flowOf(null) }
    val config by configs.collectAsStateWithLifecycle(initialValue = null)
    val child = WhatsNew.isChild(currentMember.profile)
    val history = config?.let { c -> WhatsNew.history(c.whatsNew, installedVersion, child) { c.features[it] == true } }
    // « Voir comme » reads only: « Essayer » would leave the screen of the member looked at.
    val readOnly = LocalViewAs.current.viewed != null
    WhatsNewScreen(history.orEmpty(), child, onOpen = if (readOnly) null else onOpen, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreen(
    history: List<Pair<WhatsNewRelease, List<WhatsNewCard>>>,
    child: Boolean,
    onOpen: ((WhatsNewTarget) -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.whats_new_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.AutoAwesome,
                title = stringResource(R.string.whats_new_empty_title),
                body = stringResource(R.string.whats_new_empty_body),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            history.forEach { (release, cards) ->
                item(key = "v-${release.id}") {
                    Text(
                        stringResource(R.string.whats_new_version, release.versionName),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    )
                }
                items(cards, key = { "c-${release.id}-${it.id}" }) { card ->
                    WhatsNewCardView(card, child, onOpen)
                }
            }
        }
    }
}

@FamiliaPreviews
@Composable
private fun WhatsNewScreenPreview() {
    FamiliaTheme { WhatsNewScreen(listOf(previewWhatsNew to previewWhatsNew.cards), child = false, onOpen = {}, onBack = {}) }
}

@FamiliaPreviews
@Composable
private fun WhatsNewScreenEmptyPreview() {
    FamiliaTheme { WhatsNewScreen(emptyList(), child = true, onOpen = null, onBack = {}) }
}
