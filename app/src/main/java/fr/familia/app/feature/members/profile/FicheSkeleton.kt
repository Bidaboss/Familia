package fr.familia.app.feature.members.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.ui.LoadingState
import kotlinx.coroutines.delay

/** Tag of the page's skeleton, for the tests. */
internal const val FICHE_SKELETON_TAG = "fiche_skeleton"

/** After this, a page still loading also says why (slow network, no network), like every screen. */
private const val SKELETON_HINT_MS = 2_500L

/**
 * The page while it loads (retour de l'admin, 25/11): its own shapes on its photo, the round of the photo and the
 * glass tiles, so it opens as itself and never on another screen or a lone wheel.
 */
@Composable
internal fun FicheSkeleton(modifier: Modifier = Modifier) {
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(SKELETON_HINT_MS); hint = true }
    val label = stringResource(R.string.profile_loading)
    Column(
        modifier
            .fillMaxSize()
            .testTag(FICHE_SKELETON_TAG)
            .semantics { contentDescription = label }
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 24.dp).size(112.dp).clip(CircleShape).ficheCard(56.dp))
        Box(Modifier.width(180.dp).height(28.dp).ficheCard(14.dp))
        Box(Modifier.width(120.dp).height(18.dp).ficheCard(9.dp))
        if (hint) {
            LoadingState()
        } else {
            Box(Modifier.fillMaxWidth().height(96.dp).ficheCard())
            Box(Modifier.fillMaxWidth().height(160.dp).ficheCard())
            Box(Modifier.fillMaxWidth().height(128.dp).ficheCard())
        }
    }
}
