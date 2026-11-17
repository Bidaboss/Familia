package fr.familia.app.feature.gifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.ideasListId
import fr.familia.app.core.model.wishesListId
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.dayShortLabel
import fr.familia.app.core.model.Birthday
import fr.familia.app.feature.presentation.LocalPresenting
import java.time.LocalDate

/** Cadeaux tab: the lists of the family, grouped by person, the member first. Stateless. */
@Composable
fun GiftsScreen(
    state: GiftsUiState,
    onOpenList: (listId: String) -> Unit,
    onNewList: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onAllBirthdays: () -> Unit = {},
    onPrepareSurprise: (memberId: String) -> Unit = {},
    onOpenNotebook: (() -> Unit)? = null,
    /** « Voir comme » (PRD, rôles): some lists of this member are kept from the admin. */
    someListsHidden: Boolean = false,
    /** The occasion chosen at the top of the tab (récit 9 ter), null for everyone. */
    onSelectMoment: (GiftMoment?) -> Unit = {},
    /** « Mes envies » now live on the member's page (retour d'essai, 17/11): the tab only leads there. */
    onOpenMyPage: (() -> Unit)? = null,
) {
    // Mode présentation (récit 48): the tab may be on screen at the party, in front of the person.
    val presenting = LocalPresenting.current
    Box(modifier.fillMaxSize()) {
        when (state) {
            GiftsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
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
                // Room at the bottom so the add button never hides the last list.
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
                if (state.presentation) {
                    presentationContent(state, onOpenList, onSelectMoment, onAllBirthdays, onPrepareSurprise, onOpenNotebook, onOpenMyPage)
                    return@LazyColumn
                }
                onOpenNotebook?.let { open ->
                    item(key = "notebook") {
                        ListCard(
                            title = stringResource(R.string.notebook_entry),
                            subtitle = stringResource(R.string.notebook_entry_subtitle),
                            tint = ClayTint.Household,
                            onClick = open,
                        )
                    }
                }
                if (state.occasions.isNotEmpty()) {
                    item(key = "moments") { MomentChoice(state.occasions, state.moment, onSelectMoment) }
                }
                // Once an occasion is chosen, its people come first: the birthdays wait.
                if (state.moment == null) birthdaysSection(state, onAllBirthdays, onPrepareSurprise, onOpenList)
                state.sections.filter { it.keptByMe }.forEach { section ->
                    personSection(section, state.today, state.wishPreviews, onOpenList, state.singleList, presenting, onOpenMyPage)
                }
                // The others under their household, as in the Famille tab.
                householdRuns(state.sections.filter { !it.keptByMe }).forEach { (name, section) ->
                    if (name != null) item(key = "household-${section.member.id}") { HouseholdLabel(name) }
                    personSection(section, state.today, state.wishPreviews, onOpenList, state.singleList, presenting, onOpenMyPage)
                }
                if (state.archived.isNotEmpty()) archivedSection(state.archived, state.today, onOpenList)
            }
        }
        // With the single list (récit 9 ter), nobody makes a list any more: each person has theirs.
        if (state is GiftsUiState.Success && !state.singleList) {
            ExtendedFloatingActionButton(
                onClick = onNewList,
                // The text of an extended button is hidden from TalkBack (Material 3): the icon says it.
                icon = { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.gifts_new_list)) },
                text = { Text(stringResource(R.string.gifts_new_list)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

private fun LazyListScope.personSection(
    section: GiftPersonSection,
    today: LocalDate,
    previews: Map<String, WishPreview>,
    onOpenList: (String) -> Unit,
    singleList: Boolean = false,
    presenting: Boolean = false,
    onOpenMyPage: (() -> Unit)? = null,
) {
    item(key = "person-${section.member.id}") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MemberAvatar(section.member)
            Spacer(Modifier.width(12.dp))
            Text(
                if (section.isMe) stringResource(R.string.gifts_section_me) else section.member.firstName,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
    // « Mes envies » always shows for the member and the children whose lists they keep, even empty.
    val wishes = section.wishes
    if (section.isMe && onOpenMyPage != null) {
        item(key = "wishes-${section.member.id}") { MyWishesOnPageCard(onOpenMyPage) }
    } else if (wishes != null || section.keptByMe) {
        item(key = "wishes-${section.member.id}") {
            val title = if (section.isMe) {
                stringResource(R.string.gift_my_wishes)
            } else {
                stringResource(R.string.gift_wishes_of, section.member.firstName)
            }
            ListCard(
                title = title,
                subtitle = wishesSubtitle(wishes?.let { previews[it.id] }, empty = wishes == null, keptByMe = section.keptByMe),
                tint = ClayTint.Family,
                onClick = { onOpenList(wishes?.id ?: wishesListId(section.member.id)) },
            )
        }
    }
    // « Idées de la famille » (récit 9 ter): for everyone but oneself, even before the first idea.
    if (singleList && !section.isMe) {
        item(key = "ideas-${section.member.id}") {
            val ideas = section.ideas
            // Never how many: the tab may be shown in front of the person (demo of the 21st).
            ListCard(
                title = stringResource(R.string.gift_ideas_title),
                subtitle = stringResource(R.string.gift_ideas_subtitle, section.member.firstName),
                tint = ClayTint.Birthday,
                onClick = { onOpenList(ideas?.id ?: ideasListId(section.member.id)) },
            )
        }
    }
    items(shownLists(section.lists, presenting), key = { "list-${it.id}" }) { list ->
        ListCard(
            title = list.title,
            subtitle = listSubtitle(list, section.member.firstName, today),
            tint = if (list.kind == GiftListKind.Surprise) ClayTint.Birthday else ClayTint.Plain,
            onClick = { onOpenList(list.id) },
        )
    }
}

/** « Mes envies » are on the member's page now: one card, well in sight for a child, leads there. */
@Composable
internal fun MyWishesOnPageCard(onOpenMyPage: () -> Unit) {
    ListCard(
        title = stringResource(R.string.gift_my_wishes),
        subtitle = stringResource(R.string.gifts_my_wishes_on_page),
        tint = ClayTint.Family,
        onClick = onOpenMyPage,
    )
}

/** While presenting, a surprise in progress stays out of sight: its title names the occasion. */
internal fun shownLists(lists: List<GiftList>, presenting: Boolean): List<GiftList> =
    if (presenting) lists.filter { it.kind != GiftListKind.Surprise || it.revealed } else lists

/** The next birthdays first (carte DESIGN-4), each with « Préparer une surprise » for the adults. */
internal fun LazyListScope.birthdaysSection(
    state: GiftsUiState.Success,
    onAllBirthdays: () -> Unit,
    onPrepareSurprise: (String) -> Unit,
    onOpenList: (String) -> Unit,
) {
    item(key = "birthdays-title") {
        Text(
            stringResource(R.string.gifts_birthdays_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
    }
    // The next birthday is the main point of the tab: the only card in full relief (DESIGN-11).
    items(state.birthdays, key = { "birthday-${it.member.id}" }) { birthday ->
        ClayCard(
            Modifier.fillMaxWidth(),
            tint = ClayTint.Birthday,
            contentPadding = ClaySpacing.card,
            emphasis = birthday == state.birthdays.first(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(birthday.member)
                Spacer(Modifier.width(12.dp))
                Column {
                    ClayCardTitle(
                        stringResource(R.string.gifts_birthday_of, birthday.member.firstName),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            R.string.birthday_when_age,
                            dayShortLabel(birthday.date, state.today),
                            pluralStringResource(R.plurals.birthday_age, birthday.age, birthday.age),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FamiliaClay.colors.onTintVariant(ClayTint.Birthday),
                    )
                }
            }
            val isMine = state.sections.any { it.isMe && it.member.id == birthday.member.id }
            // A surprise already prepared for them (retour d'essai du 17/11). Their own phone never gets
            // it, nor « Voir comme » them: the server, and listsSeenBy, keep a surprise list from its person.
            // Nor while the mode présentation is on (récit 48): the tab may be on screen at the party.
            val surprises = if (isMine || LocalPresenting.current) emptyList() else surprisesFor(state, birthday.member.id)
            if (surprises.isNotEmpty()) {
                ClayLink(
                    pluralStringResource(R.plurals.gifts_surprises_in_progress, surprises.size, surprises.size),
                    onClick = { onOpenList(surprises.first().id) },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (state.canPrepareSurprise && !isMine) {
                ClayLink(
                    stringResource(R.string.gifts_prepare_surprise),
                    onClick = { onPrepareSurprise(birthday.member.id) },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    item(key = "birthdays-all") {
        ClayLink(stringResource(R.string.gifts_all_birthdays), onClick = onAllBirthdays)
    }
}

/**
 * The surprise lists for [memberId] not revealed yet, nearest first, as this member sees them. Empty
 * with the single list, where a surprise is an idea of the family and the tab never says how many.
 */
internal fun surprisesFor(state: GiftsUiState.Success, memberId: String): List<GiftList> =
    state.sections.firstOrNull { it.member.id == memberId && !it.isMe }
        ?.lists.orEmpty()
        .filter { it.kind == GiftListKind.Surprise && !it.revealed && it.beneficiaryId == memberId }

internal fun LazyListScope.archivedSection(archived: List<Pair<GiftList, String>>, today: LocalDate, onOpenList: (String) -> Unit) {
    item(key = "archived-title") {
        Text(
            stringResource(R.string.gifts_archived_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 24.dp).semantics { heading() },
        )
    }
    items(archived, key = { "archived-${it.first.id}" }) { (list, name) ->
        ListCard(
            title = list.title,
            subtitle = stringResource(R.string.gifts_archived_for, name, listSubtitle(list, name, today)),
            tint = ClayTint.Plain,
            onClick = { onOpenList(list.id) },
        )
    }
}

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
    fun list(id: String, kind: GiftListKind, title: String, beneficiary: String, occasion: GiftOccasion?, date: LocalDate?) =
        GiftList(id, kind, title, occasion, date, beneficiary, emptyList(), "a", "Camille")
    val christmas = LocalDate.of(2026, 12, 25)
    return GiftsUiState.Success(
        today = LocalDate.of(2026, 11, 15),
        birthdays = listOf(Birthday(other, LocalDate.of(2026, 11, 22), 40)),
        canPrepareSurprise = true,
        sections = listOf(
            GiftPersonSection(me, isMe = true, keptByMe = true, wishes = list("w-a", GiftListKind.Wishes, "Mes envies", "a", null, null),
                lists = listOf(list("l1", GiftListKind.Own, "Noël 2026", "a", GiftOccasion.Christmas, christmas))),
            GiftPersonSection(kid, isMe = false, keptByMe = true, wishes = null, lists = emptyList()),
            GiftPersonSection(other, isMe = false, keptByMe = false, wishes = list("w-b", GiftListKind.Wishes, "Mes envies", "b", null, null),
                lists = listOf(list("l2", GiftListKind.Surprise, "Les 40 ans d'Inès", "b", GiftOccasion.Birthday, LocalDate.of(2027, 2, 3)))),
        ),
        archived = emptyList(),
        wishPreviews = mapOf("w-a" to WishPreview(4, listOf("Un livre de cuisine", "Une plante", "Un puzzle")), "w-b" to WishPreview(0, emptyList())),
    )
}

@FamiliaPreviews
@Composable
private fun GiftsScreenPreview() {
    FamiliaTheme { Surface { GiftsScreen(previewGifts(), {}, {}, {}) } }
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

/** « Tout le monde », « Noël 2026 », « Anniversaire de … »: what the tab prepares (récit 9 ter). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MomentChoice(moments: List<GiftMoment>, selected: GiftMoment?, onSelect: (GiftMoment?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf(null) + moments).forEach { moment ->
            val label = when (moment) {
                null -> stringResource(R.string.gifts_moment_all)
                is GiftMoment.Christmas -> stringResource(R.string.gifts_moment_christmas, moment.year)
                is GiftMoment.BirthdayOf -> stringResource(R.string.gifts_birthday_of, moment.member.firstName)
            }
            FilterChip(
                selected = selected == moment,
                onClick = { onSelect(moment) },
                label = { Text(label) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}
