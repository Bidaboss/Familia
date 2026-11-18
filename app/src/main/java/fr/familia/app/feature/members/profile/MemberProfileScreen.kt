package fr.familia.app.feature.members.profile

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ButtonDefaults
import fr.familia.app.core.theme.OnMemberColor
import fr.familia.app.core.ui.memberColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Souvenir
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.feature.souvenirs.BravoSheet
import fr.familia.app.feature.souvenirs.SouvenirKind
import fr.familia.app.feature.souvenirs.SouvenirMedal
import fr.familia.app.feature.souvenirs.nextSouvenirs
import fr.familia.app.feature.souvenirs.previewSouvenirs
import java.time.LocalDate
import fr.familia.app.core.ui.LoadingState

/** A member's page (récit 34): who they are, and the souvenirs they show. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberProfileScreen(
    state: MemberProfileUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSetShown: (key: String, shown: Boolean) -> Unit,
    onDismissBravo: () -> Unit = {},
    onOpenRecipes: (() -> Unit)? = null,
    /** The cards of vœux (récit 36), while their module is on. */
    wishes: (@Composable (Member) -> Unit)? = null,
    /** Pointure, goûts et passions (récit 7 bis), while their module is on. */
    card: (@Composable (Member) -> Unit)? = null,
    /** « Pensez à moi » (récit 43), while its module is on. */
    thinkOfMe: (@Composable (Member) -> Unit)? = null,
    /** « Ses envies » and « Ce que je lui offre » (retour d'essai, 17/11), while Cadeaux is on. */
    gifts: (@Composable (Member) -> Unit)? = null,
    /** What the page organised in sections reads besides the member (récit 66). */
    v2: ProfileV2Ui = ProfileV2Ui(null),
) {
    (state as? MemberProfileUiState.Ready)?.bravo?.let { souvenir ->
        SouvenirKind.of(souvenir.key)?.let { kind ->
            BravoSheet(
                kind = kind,
                souvenir = souvenir,
                onShow = {
                    onSetShown(souvenir.key, true)
                    onDismissBravo()
                },
                onDismiss = onDismissBravo,
            )
        }
    }
    Scaffold(
        topBar = {
            val ready = state as? MemberProfileUiState.Ready
            // PRODUIT-114: the bar takes the color of the band under it, and the first name lives in
            // the page; « Ma fiche » on mine. Never a ⋮ menu.
            TopAppBar(
                title = {
                    val title = if (ready?.isMe == true) stringResource(R.string.profile_v2_my_page) else ""
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                colors = if (ready != null) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = memberColor(ready.member),
                        titleContentColor = OnMemberColor,
                        navigationIconContentColor = OnMemberColor,
                        actionIconContentColor = OnMemberColor,
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors()
                },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    // « Modifier » up on the right, for those who may (DESIGN, 16/11): never from the list.
                    if (ready?.canEdit == true) {
                        TextButton(
                            onClick = onEdit,
                            modifier = Modifier.heightIn(min = 56.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = OnMemberColor),
                        ) {
                            Text(stringResource(R.string.profile_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                MemberProfileUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                MemberProfileUiState.NotFound -> EmptyState(
                    icon = Icons.Outlined.Person,
                    title = stringResource(R.string.member_not_found_title),
                    body = stringResource(R.string.profile_not_found_body),
                )
                is MemberProfileUiState.Ready -> ContentV2(state, v2, onSetShown, onOpenRecipes, wishes, card, thinkOfMe, gifts)
            }
        }
    }
}

/** My souvenirs with their switch, or the ones this member shows (récit 34), without their title. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SouvenirsContent(state: MemberProfileUiState.Ready, onSetShown: (String, Boolean) -> Unit) {
    val member = state.member
    val earned = state.souvenirs.mapNotNull { s -> SouvenirKind.of(s.key)?.let { it to s } }
    if (state.isMe) {
        Text(stringResource(R.string.souvenirs_private_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        earned.forEach { (kind, souvenir) -> MySouvenir(kind, souvenir, onSetShown) }
        val next = nextSouvenirs(state.souvenirs)
        if (next.isNotEmpty()) {
            Text(stringResource(R.string.souvenirs_next), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                next.forEach { SouvenirMedal(it, earnedAt = null, counters = state.counters) }
            }
        }
    } else if (earned.isEmpty()) {
        Text(stringResource(R.string.souvenirs_none_shown, member.firstName), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            earned.forEach { (kind, souvenir) -> SouvenirMedal(kind, souvenir.earnedAt) }
        }
    }
}

/** One of my souvenirs, with the switch « Montrer sur ma fiche ». */
@Composable
private fun MySouvenir(kind: SouvenirKind, souvenir: Souvenir, onSetShown: (String, Boolean) -> Unit) {
    ClayCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SouvenirMedal(kind, souvenir.earnedAt)
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .toggleable(value = souvenir.shown, role = Role.Switch, onValueChange = { onSetShown(souvenir.key, it) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.souvenirs_show), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = souvenir.shown, onCheckedChange = null)
            }
        }
    }
}


internal fun birthdayLabel(date: LocalDate): String = frenchDate(date, "d MMMM")

@FamiliaPreviews
@Composable
private fun MemberProfilePreview() {
    val member = Member("a", "Camille", null, LocalDate.of(1990, 5, 4), "h1", MemberProfile.Adult, true, emptyList(), null, colorIndex = 2)
    FamiliaTheme {
        Surface {
            MemberProfileScreen(
                MemberProfileUiState.Ready(member, "Foyer Martin", isMe = true, canEdit = true, souvenirs = previewSouvenirs()),
                {}, {}, { _, _ -> },
            )
        }
    }
}
