package fr.familia.app.feature.joinme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.JoinMe
import fr.familia.app.core.model.Member
import fr.familia.app.core.place.DirectionsButton
import fr.familia.app.core.place.Place
import fr.familia.app.core.place.PlaceLines
import fr.familia.app.core.place.StreetViewButton
import fr.familia.app.core.place.directionsUri
import fr.familia.app.core.place.streetViewUri
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.LocalClayCardTint
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.goodnews.postedLabel
import java.time.Instant
import java.time.ZoneId

/**
 * What « Rejoignez-moi » (récit 33) puts at the top of « À faire pour moi » on the Accueil (PRD rév. 205):
 * the places I was sent in the last 24 h, with « Itinéraire », and the one I am sharing, with
 * « Ne plus partager ». [hasShares] is false while there is none; the block then does not show for it.
 */
class JoinMeHome(
    val state: JoinMeHomeUiState.Ready,
    val stopFailed: Boolean,
    val onStop: (String) -> Unit,
) {
    val hasShares: Boolean get() = state.received.isNotEmpty() || state.sent.isNotEmpty()
}

/** Follows my shares for the Accueil, behind Feature.Places; null until they are read. */
@Composable
fun rememberJoinMeHome(container: AppContainer?, currentMember: Member): JoinMeHome? {
    val viewModel: JoinMeHomeViewModel = viewModel(
        key = "joinMeHome",
        factory = viewModelFactory { initializer { JoinMeHomeViewModel(container?.joinMeRepository, currentMember.id) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val stopFailed by viewModel.stopFailed.collectAsStateWithLifecycle()
    val ready = state as? JoinMeHomeUiState.Ready ?: return null
    return JoinMeHome(ready, stopFailed, viewModel::stop)
}

/** The shares, without a card: the home screen puts them at the top of its « À faire » block. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JoinMeHomeContent(
    state: JoinMeHomeUiState.Ready,
    stopFailed: Boolean,
    onStop: (String) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val tint = LocalClayCardTint.current
    val received = state.received
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        received.forEachIndexed { index, share ->
            if (index > 0) HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.joinme_from, share.authorName, postedLabel(share.createdAt, state.now)),
                    style = MaterialTheme.typography.labelLarge,
                    color = FamiliaClay.colors.onTintVariant(tint),
                )
                PlaceLines(share.place)
                share.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                // Side by side when they fit, one under the other at a large font size.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DirectionsButton(directionsUri(share.place))
                    StreetViewButton(streetViewUri(share.place))
                }
            }
        }
        state.sent.forEach { share ->
            if (received.isNotEmpty()) HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.joinme_mine, formatTime(share.expireAt.atZone(zone).toLocalTime())),
                    style = MaterialTheme.typography.labelLarge,
                    color = FamiliaClay.colors.onTintVariant(tint),
                )
                PlaceLines(share.place)
                ClayLink(stringResource(R.string.joinme_stop), onClick = { onStop(share.id) })
            }
        }
        if (stopFailed) {
            Text(stringResource(R.string.joinme_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Fictional shares for the previews and the screenshot tests. */
internal fun previewJoinMe(now: Instant): JoinMeHomeUiState.Ready = JoinMeHomeUiState.Ready(
    received = listOf(
        JoinMe(
            id = "j1", authorId = "camille", authorName = "Camille", recipientIds = listOf("me"),
            place = Place(46.4915, -1.7931, name = "Plage des Sables", address = "1 promenade de la Mer, 85100 Les Sables"),
            note = "On est près du poste de secours", createdAt = now.minusSeconds(600), expireAt = now.plus(JoinMe.LIFETIME).minusSeconds(600),
        ),
    ),
    sent = listOf(
        JoinMe(
            id = "j2", authorId = "me", authorName = "Moi", recipientIds = listOf("camille"),
            place = Place(46.5, -1.8, address = "3 rue des Pins, 85100 Les Sables"),
            note = null, createdAt = now.minusSeconds(3_600), expireAt = now.plus(JoinMe.LIFETIME).minusSeconds(3_600),
        ),
    ),
    now = now,
)

@FamiliaPreviews
@Composable
private fun JoinMeHomeCardPreview() {
    FamiliaTheme {
        Surface { ClayCard { JoinMeHomeContent(previewJoinMe(Instant.parse("2026-11-21T16:00:00Z")), false, {}) } }
    }
}
