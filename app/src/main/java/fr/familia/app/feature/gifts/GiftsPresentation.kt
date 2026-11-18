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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.model.Birthday
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
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * The Cadeaux tab in the new presentation « R1 bis » (DESIGN-14, spec §4, maquette R8): the next
 * occasion, « Ma liste d'envies », then the envies of each person with their « nouvelles », the
 * birthdays, and the past lists at the bottom. With the single list only. The surprises in progress
 * come first and the next occasion leads « À venir » (PRD rév. 291 and 292); the notebook is on my page (rév. 293).
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
    onOpenMyPage: (() -> Unit)? = null,
    /** « Surprises en préparation » (récit 63): false while presenting and in « Voir comme ». */
    showPreparing: Boolean = false,
) {
    // At the very top of the tab (PRD rév. 291).
    if (showPreparing) preparingSection(state, onOpenList)
    // One block « À venir » (PRD rév. 292): the next occasion first, then the birthdays and Christmas.
    upcomingSection(state, onSelectMoment, onAllBirthdays, onPrepareSurprise, onOpenList)
    // Once « Voir les idées » chose an occasion, the chips let the member come back to everyone.
    if (state.moment != null) {
        item(key = "moments") { MomentChoice(state.occasions, state.moment, onSelectMoment) }
    }
    val mine = state.sections.firstOrNull { it.isMe }
    if (mine != null && state.moment !is GiftMoment.BirthdayOf) {
        item(key = "my-wishes") {
            if (onOpenMyPage != null) {
                MyWishesOnPageCard(onOpenMyPage)
            } else {
                ListCard(
                    title = stringResource(R.string.gifts_my_wish_list),
                    subtitle = wishesSubtitle(mine.wishes?.let { state.wishPreviews[it.id] }, empty = mine.wishes == null, keptByMe = true),
                    tint = ClayTint.Family,
                    onClick = { onOpenList(mine.wishes?.id ?: wishesListId(mine.member.id)) },
                )
            }
        }
    }
    val others = state.sections.filter { !it.isMe }
    if (others.isNotEmpty()) {
        item(key = "family-title") {
            SectionLabel(stringResource(R.string.gifts_family_wishes_title), Modifier.padding(top = 16.dp))
        }
        householdRuns(others).forEach { (name, section) ->
            if (name != null) item(key = "household-${section.member.id}") { HouseholdLabel(name) }
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
    if (state.archived.isNotEmpty()) archivedSection(state.archived, state.today, onOpenList)
}

/** What comes after the next occasion in « À venir »: a birthday, or Christmas, by date. */
internal sealed interface Upcoming {
    val date: LocalDate

    data class OfBirthday(val birthday: Birthday) : Upcoming {
        override val date: LocalDate get() = birthday.date
    }

    data class OfChristmas(val year: Int) : Upcoming {
        override val date: LocalDate get() = LocalDate.of(year, 12, 25)
    }
}

/**
 * The cards under the next occasion (PRD rév. 292): the next birthdays and Christmas by date, the
 * next occasion itself left out since it is the first card. Pure, for the tests.
 */
internal fun upcomingAfterNext(state: GiftsUiState.Success): List<Upcoming> {
    val next = state.next
    val birthdays = state.birthdays
        .filterNot { next is GiftMoment.BirthdayOf && it.member.id == next.member.id && it.date == next.date }
        .map { Upcoming.OfBirthday(it) }
    val christmas = LocalDate.of(state.today.year, 12, 25).let { if (it > state.today) it else it.plusYears(1) }
    val noel = if (next is GiftMoment.Christmas || next == null) emptyList() else listOf(Upcoming.OfChristmas(christmas.year))
    return (birthdays + noel).sortedBy { it.date }
}

/** « À venir »: the next occasion in relief, then what follows by date, and « Tous les anniversaires ». */
internal fun LazyListScope.upcomingSection(
    state: GiftsUiState.Success,
    onSelectMoment: (GiftMoment?) -> Unit,
    onAllBirthdays: () -> Unit,
    onPrepareSurprise: (String) -> Unit,
    onOpenList: (String) -> Unit,
) {
    item(key = "upcoming-title") {
        Text(
            stringResource(R.string.gifts_upcoming_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
    }
    state.next?.let { next ->
        item(key = "next-occasion") { NextOccasionCard(state, next, onSeeIdeas = { onSelectMoment(next) }) }
    }
    // Once « Voir les idées » chose an occasion, its people come first: the rest waits.
    if (state.moment != null) return
    upcomingAfterNext(state).forEach { line ->
        when (line) {
            is Upcoming.OfBirthday -> item(key = "birthday-${line.birthday.member.id}") {
                BirthdayCard(state, line.birthday, emphasis = false, onPrepareSurprise, onOpenList)
            }
            is Upcoming.OfChristmas -> item(key = "christmas-${line.year}") {
                ChristmasCard(state, onSeeIdeas = { onSelectMoment(GiftMoment.Christmas(line.year)) }, year = line.year)
            }
        }
    }
    item(key = "birthdays-all") {
        ClayLink(stringResource(R.string.gifts_all_birthdays), onClick = onAllBirthdays)
    }
}

/** Christmas among the birthdays, when it is not the next occasion. */
@Composable
private fun ChristmasCard(state: GiftsUiState.Success, onSeeIdeas: () -> Unit, year: Int) {
    val days = ChronoUnit.DAYS.between(state.today, LocalDate.of(year, 12, 25)).toInt()
    ClayCard(Modifier.fillMaxWidth(), tint = ClayTint.Birthday, contentPadding = ClaySpacing.card) {
        ClayCardTitle(
            stringResource(R.string.gifts_next_christmas, pluralStringResource(R.plurals.gifts_in_days, days, days)),
            style = MaterialTheme.typography.titleMedium,
        )
        ClayLink(stringResource(R.string.gifts_see_ideas), onClick = onSeeIdeas)
    }
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
        // The person celebrated reads « Ton anniversaire »: « Voir les idées » shows only their envies.
        is GiftMoment.BirthdayOf -> if (state.sections.any { it.isMe && it.member.id == next.member.id }) {
            stringResource(R.string.gifts_next_my_birthday, inDays)
        } else {
            stringResource(R.string.gifts_next_birthday, next.member.firstName, inDays)
        }
    }
    val count = nextOccasionCount(state, next)
    val counts = when {
        count == 0 -> stringResource(R.string.gifts_next_no_wish)
        next is GiftMoment.Christmas -> pluralStringResource(R.plurals.gifts_open_wish_lists, count, count)
        state.sections.any { it.isMe && it.member.id == (next as? GiftMoment.BirthdayOf)?.member?.id } -> pluralStringResource(R.plurals.gifts_next_my_wishes, count, count)
        else -> pluralStringResource(R.plurals.gifts_next_their_wishes, count, count)
    }
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

/**
 * Each section with the name of its household when it starts a new one, else null: the tab shows
 * the name once above each household (retour d'essai, 17/11). Without a known household: « Autres », as in Famille.
 */
internal fun householdRuns(sections: List<GiftPersonSection>): List<Pair<HouseholdHeading?, GiftPersonSection>> =
    sections.mapIndexed { index, section ->
        val starts = index == 0 || sections[index - 1].householdName != section.householdName
        (if (starts) HouseholdHeading(section.householdName) else null) to section
    }

/** The name of a household above its people; null for « Autres ». */
internal data class HouseholdHeading(val name: String?)

@Composable
internal fun HouseholdLabel(heading: HouseholdHeading) {
    SectionLabel(heading.name ?: stringResource(R.string.members_other_household), Modifier.padding(top = 12.dp))
}
