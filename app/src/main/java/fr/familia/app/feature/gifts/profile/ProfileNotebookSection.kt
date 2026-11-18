package fr.familia.app.feature.gifts.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.feature.members.profile.LocalProfileSection
import fr.familia.app.feature.members.profile.ProfilePrivateBox
import fr.familia.app.feature.members.profile.ProfileSectionLabel
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.feature.gifts.ListCard

/**
 * « Mes cadeaux à offrir » only on my own page, looked at by me (PRD rév. 293): never in « Voir
 * comme » (the notebook is never shown there, PRD).
 */
internal fun showsMyNotebook(currentMemberId: String, memberId: String, viewingAs: Boolean): Boolean =
    currentMemberId == memberId && !viewingAs

@Composable
fun ProfileNotebookSection(currentMember: Member, member: Member, notebookOn: Boolean, onOpenNotebook: () -> Unit) {
    if (!notebookOn) return
    if (!showsMyNotebook(currentMember.id, member.id, LocalViewAs.current.viewed != null)) return
    ProfileNotebookCard(onOpenNotebook)
}

/** The card of the page. Stateless. */
@Composable
fun ProfileNotebookCard(onOpen: () -> Unit) {
    // Inside « Cadeaux » of my new page (P3): the beige box, « Visible par toi seulement ».
    if (LocalProfileSection.current) {
        ProfilePrivateBox {
            ProfileSectionLabel(stringResource(R.string.notebook_entry))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.profile_v2_only_you), style = MaterialTheme.typography.bodyMedium)
            }
            ClayLink(stringResource(R.string.profile_v2_open_notebook), onClick = onOpen)
        }
        return
    }
    ListCard(
        title = stringResource(R.string.notebook_entry),
        subtitle = stringResource(R.string.notebook_on_my_page),
        tint = ClayTint.Household,
        onClick = onOpen,
    )
}
