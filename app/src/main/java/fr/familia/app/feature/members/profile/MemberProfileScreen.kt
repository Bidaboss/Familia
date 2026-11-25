package fr.familia.app.feature.members.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.LocalPhotoTheme
import fr.familia.app.core.theme.PhotoBackdrop
import fr.familia.app.core.theme.PhotoScroll
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.feature.souvenirs.BravoSheet
import fr.familia.app.feature.souvenirs.SouvenirKind
import fr.familia.app.feature.souvenirs.previewSouvenirs
import java.time.LocalDate

/**
 * A member's page (brief Famille-recettes, F6 à F8), lying on the photo of the theme like Famille.
 * « Ma fiche » or « Fiche de Hugo » at the top, never a ⋮ menu, and « Modifier » on the right for
 * those who may. Stateless.
 */
@Composable
fun MemberProfileScreen(
    state: MemberProfileUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDismissBravo: () -> Unit = {},
    /** The cards of vœux (récit 36), while their module is on. */
    wishes: (@Composable (Member) -> Unit)? = null,
    v2: ProfileV2Ui = ProfileV2Ui(null),
) {
    (state as? MemberProfileUiState.Ready)?.bravo?.let { souvenir ->
        // The Réussites are public now (règle 8): the sheet only says bravo.
        SouvenirKind.of(souvenir.key)?.let { kind -> BravoSheet(kind = kind, souvenir = souvenir, onShow = onDismissBravo, onDismiss = onDismissBravo) }
    }
    val photo = LocalPhotoTheme.current
    val scroll = remember { PhotoScroll() }
    Box(Modifier.fillMaxSize().then(if (photo == null) Modifier.themeBackground() else Modifier)) {
        if (photo != null) PhotoBackdrop(photo, scroll)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                val ready = state as? MemberProfileUiState.Ready
                FamiliaTopBar(
                    title = {
                        val title = when {
                            ready == null -> ""
                            ready.isMe -> stringResource(R.string.profile_v2_my_page)
                            else -> stringResource(R.string.profile_v2_page_title, ready.member.firstName)
                        }
                        Text(title, style = onPhotoStyle(MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    },
                    navigationIcon = { BackButton(onClick = onBack) },
                    actions = {
                        // One « Modifier » for the whole page (règle 3), for those who may.
                        if (ready?.canEdit == true) {
                            Surface(
                                onClick = onEdit,
                                shape = MaterialTheme.shapes.extraLarge,
                                color = Color.Transparent,
                                modifier = Modifier.heightIn(min = 56.dp).ficheCard(28.dp),
                            ) {
                                Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.profile_edit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ficheInk())
                                }
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
                    is MemberProfileUiState.Ready -> ContentV2(state, v2, wishes, scrollConnection = scroll.connection.takeIf { photo != null })
                }
            }
        }
    }
}

@FamiliaPreviews
@Composable
private fun MemberProfilePreview() {
    val member = Member("a", "Camille", null, LocalDate.of(1990, 5, 4), "h1", MemberProfile.Adult, true, emptyList(), null, colorIndex = 2, nickname = "Mamie")
    FamiliaTheme {
        Surface {
            MemberProfileScreen(
                MemberProfileUiState.Ready(member, "Foyer Martin", isMe = true, canEdit = true, souvenirs = previewSouvenirs()),
                {}, {},
            )
        }
    }
}
