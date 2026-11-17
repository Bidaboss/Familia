package fr.familia.app.feature.gifts.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.wishesListId
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.feature.gifts.WishPreview
import fr.familia.app.feature.gifts.keepsListsOf
import fr.familia.app.feature.gifts.wishPreview
import fr.familia.app.feature.gifts.wishesSubtitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * « Mes envies » of a member on their page (retour d'essai, 17/11): how many and the first titles,
 * then the whole list. Only what the person sees of it: never a surprise idea nor a reservation,
 * so the page may be shown in front of them, at the party too.
 */
class ProfileWishesViewModel(gifts: GiftRepository?, memberId: String) : ViewModel() {
    val preview: StateFlow<WishPreview?> = (gifts?.observeItems(wishesListId(memberId), hiddenFromMe = true) ?: flowOf(emptyList()))
        .map { wishPreview(it) }
        // A list nobody wrote to yet, or a read refused: an empty list, never an error on the page.
        .catch { e ->
            if (e is CancellationException) throw e
            emit(WishPreview(0, emptyList()))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun ProfileWishesSection(
    container: AppContainer?,
    currentMember: Member,
    member: Member,
    onOpenList: (listId: String) -> Unit,
    onAddWish: (listId: String) -> Unit,
) {
    val viewModel: ProfileWishesViewModel = viewModel(
        key = "profile-wishes-${member.id}",
        factory = viewModelFactory { initializer { ProfileWishesViewModel(container?.giftRepository, member.id) } },
    )
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val listId = wishesListId(member.id)
    ProfileWishesContent(
        preview = preview,
        member = member,
        isMe = member.id == currentMember.id,
        keptByMe = keepsListsOf(currentMember, member),
        // « Voir comme » writes nothing: no « Ajouter une envie » there.
        mayAdd = keepsListsOf(currentMember, member) && LocalViewAs.current.viewed == null,
        onOpen = { onOpenList(listId) },
        onAdd = { onAddWish(listId) },
    )
}

/** The card of the page. Stateless. */
@Composable
fun ProfileWishesContent(
    preview: WishPreview?,
    member: Member,
    isMe: Boolean,
    keptByMe: Boolean,
    mayAdd: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
) {
    ClayCard(Modifier.fillMaxWidth(), contentPadding = ClaySpacing.card) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ClayCardTitle(if (isMe) stringResource(R.string.gift_my_wishes) else stringResource(R.string.gift_wishes_of, member.firstName))
            Text(
                wishesSubtitle(preview, empty = false, keptByMe = keptByMe),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ClayLink(stringResource(R.string.profile_wishes_open), onClick = onOpen)
            if (mayAdd) ClayLink(stringResource(R.string.gift_add_wish), onClick = onAdd)
        }
    }
}
