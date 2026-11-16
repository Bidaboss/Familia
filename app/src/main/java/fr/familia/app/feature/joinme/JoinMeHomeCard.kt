package fr.familia.app.feature.joinme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
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
import fr.familia.app.core.place.directionsUri
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.goodnews.postedLabel
import java.time.Instant
import java.time.ZoneId

/**
 * The « Rejoignez-moi » card of the home screen (récit 33): the places I was sent in the last
 * 24 h, with « Itinéraire », the one I am sharing, with « Ne plus partager », and the button that
 * sends mine. Placed first by the home screen while it has something received.
 */
@Composable
fun JoinMeHomeCard(container: AppContainer?, currentMember: Member, onSend: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: JoinMeHomeViewModel = viewModel(
        key = "joinMeHome",
        factory = viewModelFactory { initializer { JoinMeHomeViewModel(container?.joinMeRepository, currentMember.id) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val stopFailed by viewModel.stopFailed.collectAsStateWithLifecycle()
    JoinMeHomeContent(state, stopFailed, onSend, viewModel::stop, modifier)
}

@Composable
fun JoinMeHomeContent(
    state: JoinMeHomeUiState,
    stopFailed: Boolean,
    onSend: () -> Unit,
    onStop: (String) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val ready = state as? JoinMeHomeUiState.Ready
    val received = ready?.received.orEmpty()
    val tint = if (received.isEmpty()) ClayTint.Plain else ClayTint.Household
    ClayCard(modifier = modifier.fillMaxWidth(), tint = tint) {
        Text(stringResource(R.string.joinme_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (ready == null) return@ClayCard
        if (received.isEmpty() && ready.sent.isEmpty()) {
            Text(
                stringResource(R.string.joinme_home_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = FamiliaClay.colors.onTintVariant(tint),
            )
        }
        received.forEachIndexed { index, share ->
            if (index > 0) HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.joinme_from, share.authorName, postedLabel(share.createdAt, ready.now)),
                    style = MaterialTheme.typography.labelLarge,
                    color = FamiliaClay.colors.onTintVariant(tint),
                )
                PlaceLines(share.place)
                share.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                DirectionsButton(directionsUri(share.place))
            }
        }
        ready.sent.forEach { share ->
            if (received.isNotEmpty()) HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.joinme_mine, formatTime(share.expireAt.atZone(zone).toLocalTime())),
                    style = MaterialTheme.typography.labelLarge,
                    color = FamiliaClay.colors.onTintVariant(tint),
                )
                PlaceLines(share.place)
                TextButton(onClick = { onStop(share.id) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.joinme_stop))
                }
            }
        }
        if (stopFailed) {
            Text(stringResource(R.string.joinme_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        // Tonal: the one filled button of the Accueil is « Répondre » of the question of the week.
        FilledTonalButton(onClick = onSend, modifier = Modifier.heightIn(min = 56.dp)) {
            Icon(Icons.Outlined.MyLocation, contentDescription = null)
            Text(stringResource(R.string.joinme_send_open), modifier = Modifier.padding(start = 8.dp))
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
        Surface { JoinMeHomeContent(previewJoinMe(Instant.parse("2026-11-21T16:00:00Z")), false, {}, {}) }
    }
}
