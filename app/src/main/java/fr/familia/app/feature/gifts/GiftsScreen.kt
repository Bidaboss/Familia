package fr.familia.app.feature.gifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.nextBirthday
import java.time.LocalDate
import fr.familia.app.core.ui.LoadingState

/** Cadeaux tab: one row per person, grouped by household (récit 91). Stateless. */
@Composable
fun GiftsScreen(
    state: GiftsUiState,
    onOpenList: (listId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** « Voir comme » (PRD, rôles): some lists of this member are kept from the admin. */
    someListsHidden: Boolean = false,
    /** « Mes envies » now live on the member's page (retour d'essai, 17/11): the tab only leads there. */
    onOpenMyPage: (() -> Unit)? = null,
    /** « Préparer une surprise » (récit 63), in the black bar at the top while « Bouton surprise » is on. */
    onOpenSurprisePicker: (() -> Unit)? = null,
    /** « Ajouter une envie » (PRODUIT-119). */
    onAddWish: (listId: String) -> Unit = onOpenList,
    /**
     * The surprises this member prepares (brief Cadeaux et Surprise, §2): one dark tile each at the
     * top, and « Préparer une surprise » for an adult. Null keeps the bar of before.
     */
    surprises: List<SurpriseTile>? = null,
    onOpenSurprise: (String) -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        when (state) {
            GiftsUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
            GiftsUiState.Error -> EmptyState(
                icon = Icons.Outlined.CardGiftcard,
                title = stringResource(R.string.gifts_error_title),
                body = stringResource(R.string.gifts_error_body),
                action = {
                    Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                },
            )
            is GiftsUiState.Success -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = rememberTopListState(preparingLines(state).map { it.key }),
                contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
            ) {
                if (someListsHidden) {
                    item(key = "view-as-hidden") {
                        Text(
                            stringResource(R.string.view_as_gifts_hidden),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Nothing of a surprise in « Voir comme » (récit 63).
                giftsV2Content(
                    state,
                    GiftsV2Actions(onOpenList, onOpenMyPage, onAddWish, onOpenSurprisePicker, surprises, onOpenSurprise),
                    showPreparing = !someListsHidden,
                )
            }
        }
        // « Préparer une surprise », the floating button of the brief (§2, fiche C1), in place of the
        // line of the black bar once the surprises are there.
        val prepare = onOpenSurprisePicker
        if (state is GiftsUiState.Success && surprises != null && !someListsHidden && state.canPrepareSurprise && prepare != null) {
            ExtendedFloatingActionButton(
                onClick = prepare,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.gifts_prepare_surprise), fontWeight = FontWeight.ExtraBold) },
                containerColor = surpriseDark(),
                contentColor = Color.White,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(ClaySpacing.screen),
            )
        }
    }
}

/**
 * One line of « Surprises en préparation » (récit 63): a person whose ideas of the family hold a
 * surprise this member can read, never their own.
 */
internal data class PreparingLine(val member: Member, val birthday: LocalDate?) {
    val key: String get() = "ideas-${member.id}"
}

/**
 * The list of the tab, kept at the very top when the surprises arrive after the rest (retour d'essai,
 * 19/11): a lazy list keeps its first item in place, so a card added above it stayed out of sight.
 */
@Composable
private fun rememberTopListState(topKeys: List<String>): LazyListState {
    val listState = rememberLazyListState()
    LaunchedEffect(topKeys) {
        if (listState.firstVisibleItemIndex <= topKeys.size && listState.firstVisibleItemScrollOffset == 0) {
            listState.scrollToItem(0)
        }
    }
    return listState
}

/** The surprises in progress, nearest birthday first: never this member's own. */
internal fun preparingLines(state: GiftsUiState.Success): List<PreparingLine> =
    state.sections.filter { !it.isMe && it.member.id in state.surprisePreparedFor }
        .map { section -> PreparingLine(section.member, section.member.birthDate?.let { nextBirthday(it, state.today) }) }
        .sortedBy { it.birthday ?: LocalDate.MAX }

@Composable
internal fun ListCard(title: String, subtitle: String, tint: ClayTint, onClick: () -> Unit) {
    ClayCard(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        tint = tint,
        onClick = onClick,
        onClickLabel = stringResource(R.string.gifts_open_list),
        contentPadding = ClaySpacing.card,
    ) {
        ClayCardTitle(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = FamiliaClay.colors.onTintVariant(tint))
        }
    }
}

/** Fictional lists for previews and screenshots. */
internal fun previewGifts(): GiftsUiState.Success {
    fun member(id: String, name: String, profile: MemberProfile = MemberProfile.Adult, parents: List<String> = emptyList()) =
        Member(id, name, null, null, "h1", profile, false, parents, null)
    val me = member("a", "Camille")
    val kid = member("c", "Noé", MemberProfile.ChildWithoutApp, listOf("a"))
    val other = member("b", "Inès")
    fun wishes(id: String, beneficiary: String) =
        GiftList(id, GiftListKind.Wishes, "Mes envies", null, null, beneficiary, emptyList(), beneficiary, "")
    return GiftsUiState.Success(
        today = LocalDate.of(2026, 11, 15),
        birthdays = listOf(Birthday(other, LocalDate.of(2026, 11, 22), 40)),
        canPrepareSurprise = true,
        sections = listOf(
            GiftPersonSection(me, isMe = true, keptByMe = true, wishes = wishes("w-a", "a")),
            GiftPersonSection(kid, isMe = false, keptByMe = true, wishes = null),
            GiftPersonSection(other, isMe = false, keptByMe = false, wishes = wishes("w-b", "b")),
        ),
        archived = emptyList(),
        wishPreviews = mapOf("w-a" to WishPreview(4, listOf("Un livre de cuisine", "Une plante", "Un puzzle")), "w-b" to WishPreview(0, emptyList())),
    )
}

@FamiliaPreviews
@Composable
private fun GiftsScreenPreview() {
    FamiliaTheme { Surface { GiftsScreen(previewGifts(), {}, {}) } }
}

/**
 * What the card of an « Envies » says: « 3 envies : Lego, vélo, livre… » once its ideas are read,
 * « Pas encore d'envie » when it holds none, and « Toute la famille la voit » while it loads.
 */
@Composable
internal fun wishesSubtitle(preview: WishPreview?, empty: Boolean, keptByMe: Boolean): String = when {
    empty || preview?.count == 0 ->
        stringResource(if (keptByMe) R.string.gifts_wishes_empty else R.string.gifts_wishes_none)
    preview == null -> stringResource(R.string.gift_wishes_subtitle)
    else -> {
        val titles = preview.titles.joinToString(", ") + if (preview.count > preview.titles.size) "…" else ""
        pluralStringResource(R.plurals.gifts_wishes_preview, preview.count, preview.count, titles)
    }
}
