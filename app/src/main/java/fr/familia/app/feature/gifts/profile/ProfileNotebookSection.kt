package fr.familia.app.feature.gifts.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.feature.gifts.ListCard
import fr.familia.app.feature.presentation.LocalPresenting

/**
 * « Mes cadeaux à offrir » only on my own page, looked at by me (PRD rév. 293): never in « Voir
 * comme » (the notebook is never shown there, PRD), never in presentation mode, since what I give
 * may be on screen in front of the person.
 */
internal fun showsMyNotebook(currentMemberId: String, memberId: String, viewingAs: Boolean, presenting: Boolean): Boolean =
    currentMemberId == memberId && !viewingAs && !presenting

@Composable
fun ProfileNotebookSection(currentMember: Member, member: Member, notebookOn: Boolean, onOpenNotebook: () -> Unit) {
    if (!notebookOn) return
    if (!showsMyNotebook(currentMember.id, member.id, LocalViewAs.current.viewed != null, LocalPresenting.current)) return
    ProfileNotebookCard(onOpenNotebook)
}

/** The card of the page. Stateless. */
@Composable
fun ProfileNotebookCard(onOpen: () -> Unit) {
    ListCard(
        title = stringResource(R.string.notebook_entry),
        subtitle = stringResource(R.string.notebook_on_my_page),
        tint = ClayTint.Household,
        onClick = onOpen,
    )
}
