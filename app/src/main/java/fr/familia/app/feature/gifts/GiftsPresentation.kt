package fr.familia.app.feature.gifts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.model.ideasListId
import fr.familia.app.core.model.wishesListId
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.SectionLabel
import java.time.temporal.ChronoUnit

/*
 * The Cadeaux tab in the new presentation « R1 bis » (DESIGN-14, spec §4, maquette R8): the next
 * occasion, « Ma liste d'envies », then the envies of each person with their « nouvelles », the
 * birthdays, and the notebook and the past lists at the bottom. With the single list only.
 *
 * Nothing here ever shows a reservation, and the ideas of the family only as one number for all
 * people together: the tab may be on screen in front of anyone.
 */

internal fun LazyListScope.presentationContent(
    state: GiftsUiState.Success,
    onOpenList: (String) -> Unit,
    onSelectMoment: (GiftMoment?) -> Unit,
    onAllBirthdays: () -> Unit,
    onPrepareSurprise: (String) -> Unit,
    onOpenNotebook: (() -> Unit)?,
) {
    state.next?.let { next ->
        item(key = "next-occasion") { NextOccasionCard(state, next, onSeeIdeas = { onSelectMoment(next) }) }
    }
    // Once « Voir les idées » chose an occasion, the chips let the member come back to everyone.
    if (state.moment != null) {
        item(key = "moments") { MomentChoice(state.occasions, state.moment, onSelectMoment) }
    }
    val mine = state.sections.firstOrNull { it.isMe }
    if (mine != null && state.moment !is GiftMoment.BirthdayOf) {
        item(key = "my-wishes") {
            ListCard(
                title = stringResource(R.string.gifts_my_wish_list),
                subtitle = wishesSubtitle(mine.wishes?.let { state.wishPreviews[it.id] }, empty = mine.wishes == null, keptByMe = true),
                tint = ClayTint.Family,
                onClick = { onOpenList(mine.wishes?.id ?: wishesListId(mine.member.id)) },
            )
        }
    }
    val others = state.sections.filter { !it.isMe }
    if (others.isNotEmpty()) {
        item(key = "family-title") {
            SectionLabel(stringResource(R.string.gifts_family_wishes_title), Modifier.padding(top = 16.dp))
        }
        others.forEach { section ->
            item(key = "person-${section.member.id}") {
                PersonWishesRow(
                    section = section,
                    preview = section.wishes?.let { state.wishPreviews[it.id] },
                    fresh = section.wishes?.let { state.newWishes[it.id] } ?: 0,
                    onOpenWishes = { onOpenList(section.wishes?.id ?: wishesListId(section.member.id)) },
                    onOpenIdeas = { onOpenList(section.ideas?.id ?: ideasListId(section.member.id)) },
                )
            }
        }
    }
    if (state.moment == null) birthdaysSection(state, onAllBirthdays, onPrepareSurprise)
    onOpenNotebook?.let { open ->
        item(key = "notebook") {
            Spacer(Modifier.padding(top = 8.dp))
            ListCard(
                title = stringResource(R.string.notebook_entry),
                subtitle = stringResource(R.string.notebook_entry_subtitle),
                tint = ClayTint.Household,
                onClick = open,
            )
        }
    }
    if (state.archived.isNotEmpty()) archivedSection(state.archived, state.today, onOpenList)
}

/**
 * « Prochaine occasion »: when, and how many wish lists are open. Never a count of ideas, read as
 * the ideas for the person named on the card, and never who reserved what.
 */
@Composable
private fun NextOccasionCard(state: GiftsUiState.Success, next: GiftMoment, onSeeIdeas: () -> Unit) {
    val days = ChronoUnit.DAYS.between(state.today, next.day()).toInt()
    val inDays = pluralStringResource(R.plurals.gifts_in_days, days, days)
    val title = when (next) {
        is GiftMoment.Christmas -> stringResource(R.string.gifts_next_christmas, inDays)
        is GiftMoment.BirthdayOf -> stringResource(R.string.gifts_next_birthday, next.member.firstName, inDays)
    }
    val lists = state.sections.count { it.wishes != null }
    val counts = pluralStringResource(R.plurals.gifts_open_wish_lists, lists, lists)
    ClayCard(Modifier.fillMaxWidth(), tint = ClayTint.Birthday, contentPadding = ClaySpacing.card, emphasis = true) {
        SectionLabel(stringResource(R.string.gifts_next_occasion_label), color = FamiliaClay.colors.onTintVariant(ClayTint.Birthday))
        ClayCardTitle(title, style = MaterialTheme.typography.titleLarge)
        Text(counts, style = MaterialTheme.typography.bodyMedium, color = FamiliaClay.colors.onTintVariant(ClayTint.Birthday))
        ClayLink(stringResource(R.string.gifts_see_ideas), onClick = onSeeIdeas)
    }
}

/** One person of the family: their envies, how many are new, and their ideas of the family. */
@Composable
private fun PersonWishesRow(
    section: GiftPersonSection,
    preview: WishPreview?,
    fresh: Int,
    onOpenWishes: () -> Unit,
    onOpenIdeas: () -> Unit,
) {
    ClayCard(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        onClick = onOpenWishes,
        onClickLabel = stringResource(R.string.gifts_open_list),
        contentPadding = ClaySpacing.card,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(section.member)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                ClayCardTitle(section.member.firstName, style = MaterialTheme.typography.titleMedium)
                val count = preview?.count ?: 0
                Text(
                    if (section.wishes == null || count == 0) {
                        stringResource(R.string.gifts_wishes_none)
                    } else {
                        pluralStringResource(R.plurals.gifts_wishes_count, count, count)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (fresh > 0) NewBadge(fresh)
        }
        ClayLink(stringResource(R.string.gifts_ideas_for, section.member.firstName), onClick = onOpenIdeas)
    }
}

/** « 2 nouvelles »: a small pill, never under 13 sp. */
@Composable
private fun NewBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            pluralStringResource(R.plurals.gifts_new_wishes, count, count),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
