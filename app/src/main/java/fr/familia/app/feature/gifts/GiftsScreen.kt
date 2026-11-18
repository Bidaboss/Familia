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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.Button
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
import fr.familia.app.core.model.nextBirthday
import fr.familia.app.feature.presentation.LocalPresenting
import java.time.LocalDate
import fr.familia.app.core.ui.LoadingState

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
    /** « Voir comme » (PRD, rôles): some lists of this member are kept from the admin. */
    someListsHidden: Boolean = false,
    /** The occasion chosen at the top of the tab (récit 9 ter), null for everyone. */
    onSelectMoment: (GiftMoment?) -> Unit = {},
    /** « Mes envies » now live on the member's page (retour d'essai, 17/11): the tab only leads there. */
    onOpenMyPage: (() -> Unit)? = null,
    /** « Préparer une surprise » (récit 63): the one button, while « Bouton surprise » is on. */
    onOpenSurprisePicker: (() -> Unit)? = null,
    /** « Ajouter une envie » on the new tab (PRODUIT-119). */
    onAddWish: (listId: String) -> Unit = onOpenList,
) {
    // Mode présentation (récit 48): the tab may be on screen at the party, in front of the person.
    val presenting = LocalPresenting.current
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
                // Room at the bottom so the add button never hides the last list.
                // Two buttons stacked when « Bouton surprise » meets the lists by occasion.
                contentPadding = PaddingValues(
                    start = ClaySpacing.screen, end = ClaySpacing.screen, top = 16.dp,
                    bottom = if (state.surpriseButton && !state.singleList) 168.dp else 96.dp,
                ),
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
                // The new tab (PRODUIT-119 and 120, C1 à C5): nothing of a surprise while presenting nor
                // in « Voir comme », as below.
                if (state.tabV2) {
                    giftsV2Content(
                        state,
                        GiftsV2Actions(onOpenList, onSelectMoment, onAllBirthdays, onOpenMyPage, onAddWish),
                        showPreparing = !presenting && !someListsHidden,
                    )
                    return@LazyColumn
                }
                // At the very top of the tab (PRD rév. 291), never while presenting nor in « Voir comme »
                // (récit 63): nothing of a surprise on screen.
                if (!state.presentation && !presenting && !someListsHidden) preparingSection(state, onOpenList)
                if (state.presentation) {
                    presentationContent(
                        state, onOpenList, onSelectMoment, onAllBirthdays, onPrepareSurprise, onOpenMyPage,
                        showPreparing = !presenting && !someListsHidden,
                    )
                    return@LazyColumn
                }
                // Right under the surprises (PRD rév. 292), the next birthday in relief. Once an occasion is
                // chosen, its people come first: the birthdays wait.
                if (state.moment == null) birthdaysSection(state, onAllBirthdays, onPrepareSurprise, onOpenList)
                // « Mes cadeaux à offrir » lives on my own page (PRD rév. 293), not in the tab.
                if (state.occasions.isNotEmpty()) {
                    item(key = "moments") { MomentChoice(state.occasions, state.moment, onSelectMoment) }
                }
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
        val surpriseButton = (state as? GiftsUiState.Success)
            // On the new tab, not even the button while presenting (C3): it would say a surprise may exist.
            ?.takeIf { it.surpriseButton && it.canPrepareSurprise && !someListsHidden && !(it.tabV2 && presenting) }
            ?.let { onOpenSurprisePicker }
        val v2 = (state as? GiftsUiState.Success)?.tabV2 == true
        Column(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // With the single list (récit 9 ter), nobody makes a list any more: each person has theirs.
            if (state is GiftsUiState.Success && !state.singleList) {
                ExtendedFloatingActionButton(
                    onClick = onNewList,
                    // The text of an extended button is hidden from TalkBack (Material 3): the icon says it.
                    icon = { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.gifts_new_list)) },
                    text = { Text(stringResource(R.string.gifts_new_list)) },
                )
            }
            surpriseButton?.let { open ->
                ExtendedFloatingActionButton(
                    onClick = open,
                    icon = {
                        Icon(
                            if (v2) Icons.Outlined.AutoAwesome else Icons.Outlined.CardGiftcard,
                            contentDescription = stringResource(R.string.gifts_prepare_surprise),
                            tint = if (v2) V2_SUN else LocalContentColor.current,
                        )
                    },
                    text = { Text(stringResource(R.string.gifts_prepare_surprise)) },
                    // Dark on the new tab (C2).
                    containerColor = if (v2) MaterialTheme.colorScheme.inverseSurface else FloatingActionButtonDefaults.containerColor,
                    contentColor = if (v2) MaterialTheme.colorScheme.inverseOnSurface else contentColorFor(FloatingActionButtonDefaults.containerColor),
                )
            }
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

/**
 * « Surprises en préparation » (récit 63): one line per person with a surprise this member can read,
 * never their own (the server and listsSeenBy keep it from them). With the single list, a surprise is
 * an idea of the family kept from its person. The caller hides it while presenting and in « Voir comme ».
 */
internal fun LazyListScope.preparingSection(state: GiftsUiState.Success, onOpenList: (String) -> Unit) {
    val lines = preparingLines(state)
    if (!state.surpriseButton || lines.isEmpty()) return
    item(key = "preparing-title") {
        Text(
            stringResource(R.string.gifts_preparing_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp).semantics { heading() },
        )
    }
    items(lines, key = { "preparing-${it.key}" }) { line ->
        when (line) {
            is PreparingLine.OfList -> ListCard(
                title = line.list.title,
                subtitle = stringResource(R.string.gifts_preparing_for, line.member.firstName),
                tint = ClayTint.Birthday,
                onClick = { onOpenList(line.list.id) },
            )
            is PreparingLine.OfIdeas -> ListCard(
                title = stringResource(R.string.gifts_preparing_ideas_title, line.member.firstName),
                subtitle = line.birthday?.let { stringResource(R.string.gifts_preparing_ideas_birthday, dayShortLabel(it, state.today)) }
                    ?: stringResource(R.string.gifts_preparing_ideas),
                tint = ClayTint.Birthday,
                onClick = { onOpenList(ideasListId(line.member.id)) },
            )
        }
    }
}

/** One line of « Surprises en préparation ». */
internal sealed interface PreparingLine {
    val key: String

    data class OfList(val list: GiftList, val member: Member) : PreparingLine {
        override val key: String get() = list.id
    }

    data class OfIdeas(val member: Member, val birthday: LocalDate?) : PreparingLine {
        override val key: String get() = "ideas-${member.id}"
    }
}

/** The surprises in progress, nearest first: never this member's own. */
internal fun preparingLines(state: GiftsUiState.Success): List<PreparingLine> {
    val others = state.sections.filter { !it.isMe }
    val lists = others.flatMap { section -> surprisesFor(state, section.member.id).map { PreparingLine.OfList(it, section.member) } }
    val ideas = others.filter { it.member.id in state.surprisePreparedFor }
        .map { section -> PreparingLine.OfIdeas(section.member, section.member.birthDate?.let { nextBirthday(it, state.today) }) }
    return (lists + ideas).sortedBy { line ->
        when (line) {
            is PreparingLine.OfList -> line.list.date ?: LocalDate.MAX
            is PreparingLine.OfIdeas -> line.birthday ?: LocalDate.MAX
        }
    }
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
        BirthdayCard(state, birthday, emphasis = birthday == state.birthdays.first(), onPrepareSurprise, onOpenList)
    }
    item(key = "birthdays-all") {
        ClayLink(stringResource(R.string.gifts_all_birthdays), onClick = onAllBirthdays)
    }
}

/** One birthday of the tab, with what this member may do for it. */
@Composable
internal fun BirthdayCard(
    state: GiftsUiState.Success,
    birthday: Birthday,
    emphasis: Boolean,
    onPrepareSurprise: (String) -> Unit,
    onOpenList: (String) -> Unit,
) {
    ClayCard(
        Modifier.fillMaxWidth(),
        tint = ClayTint.Birthday,
        contentPadding = ClaySpacing.card,
        emphasis = emphasis,
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
        } else if (!isMine && !LocalPresenting.current && birthday.member.id in state.surprisePreparedFor) {
            // With the single list, a surprise is an idea of the family: the phrase alone, no number.
            ClayLink(
                stringResource(R.string.gifts_surprise_prepared),
                onClick = { onOpenList(ideasListId(birthday.member.id)) },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // With « Bouton surprise » (récit 63), the one button of the tab replaces this link.
        if (state.canPrepareSurprise && !isMine && !state.surpriseButton) {
            ClayLink(
                stringResource(R.string.gifts_prepare_surprise),
                onClick = { onPrepareSurprise(birthday.member.id) },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
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
