package fr.familia.app.core.viewas

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.isOwner
import fr.familia.app.core.theme.FamiliaTheme
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf

/**
 * « Voir comme » (PRD, rôles): the admin principal picks a member in Administration, and the app
 * shows itself as that member sees it, under a banner « Tu vois l'appli comme … » with « Quitter ».
 * The admin stays signed in with their own account, and nothing can be written ([ViewAsContainer]).
 * [content] is the whole app, given a container and the member it is for.
 */
@Composable
fun ViewAsHost(
    container: AppContainer?,
    me: Member,
    content: @Composable (container: AppContainer?, member: Member) -> Unit,
) {
    var viewedId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewedName by rememberSaveable { mutableStateOf("") }
    val families = remember(container) { container?.memberRepository?.observeFamily()?.catch { } ?: flowOf(null) }
    val family by families.collectAsStateWithLifecycle(initialValue = null)
    // After a rotation or a return to the app, the family is read again: until then the admin's own
    // app must not show (their own Accueil and Cadeaux), only the banner and a wait.
    if (viewedId != null && family == null && container != null) {
        ViewAsWaiting(viewedName, onQuit = { viewedId = null })
        return
    }
    // Only the admin principal, read from the live family; a member removed meanwhile ends it.
    val owner = family?.isOwner(me) == true
    val viewed = family?.members?.firstOrNull { it.id == viewedId }?.takeIf { owner && it.id != me.id }
    if (viewed == null || container == null) {
        val control = ViewAsControl(
            start = if (owner && container != null) {
                { member ->
                    viewedName = member.firstName
                    viewedId = member.id
                }
            } else {
                null
            },
        )
        CompositionLocalProvider(LocalViewAs provides control) { content(container, me) }
        return
    }
    val context = LocalContext.current
    val readOnly = stringResource(R.string.view_as_read_only)
    val viewContainer = remember(container, context, me.id, viewed.id) {
        val main = Handler(Looper.getMainLooper())
        val guard = ViewAsGuard { main.post { Toast.makeText(context.applicationContext, readOnly, Toast.LENGTH_SHORT).show() } }
        ViewAsContainer(container, guard, me.id, viewed.id)
    }
    val control = ViewAsControl(viewed = viewed, stop = { viewedId = null }, readerId = me.id)
    // Back on the first screen would close Familia, and opening it again shows the admin's own app:
    // here it only says where « Quitter » is. The screens' own back comes first (added after this one).
    val backHint = stringResource(R.string.view_as_back_hint)
    BackHandler { Toast.makeText(context, backHint, Toast.LENGTH_SHORT).show() }
    Column(Modifier.fillMaxSize()) {
        ViewAsBanner(viewed.firstName, onQuit = { viewedId = null }, modifier = Modifier.statusBarsPadding())
        // A new app for each member looked at: its own screens, back stack and state.
        key(viewed.id) {
            // The banner already sits under the status bar: the app's own bars do not pad it again.
            Column(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
                CompositionLocalProvider(LocalViewAs provides control) { content(viewContainer, viewed) }
            }
        }
    }
}

/** While the family is read again in « Voir comme »: the banner, and nothing of the admin's own app. */
@Composable
private fun ViewAsWaiting(firstName: String, onQuit: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ViewAsBanner(firstName, onQuit = onQuit, modifier = Modifier.statusBarsPadding())
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
fun ViewAsBanner(firstName: String, onQuit: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().then(modifier).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.view_as_banner, firstName),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            TextButton(onClick = onQuit, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.view_as_quit)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ViewAsBannerPreview() {
    FamiliaTheme { ViewAsBanner("Camille", onQuit = {}) }
}
