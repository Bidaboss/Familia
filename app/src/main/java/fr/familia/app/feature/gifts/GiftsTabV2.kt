package fr.familia.app.feature.gifts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.ideasListId
import fr.familia.app.core.model.wishesListId
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.SectionLabel
import fr.familia.app.core.ui.memberColor
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * The Cadeaux tab by people (récit 91, variant A): one row per person, grouped by household, and a
 * single gesture, touching the row opens the person's list of wishes. From the top: the black surprise bar,
 * « Mes envies », « Bientôt » (the birthdays of the next 30 days), « Toute la famille », then
 * « Cadeaux reçus » and « Listes archivées ».
 *
 * Never a count of ideas nor of reservations: only « N envies », what the person sees of their own
 * list. Nothing of a surprise in « Voir comme » (the caller says so).
 */

/** What the tab leads to. */
internal class GiftsV2Actions(
    val onOpenList: (String) -> Unit,
    val onOpenMyPage: (() -> Unit)?,
    val onAddWish: (listId: String) -> Unit,
    val onOpenSurprisePicker: (() -> Unit)?,
)

internal fun LazyListScope.giftsV2Content(state: GiftsUiState.Success, actions: GiftsV2Actions, showPreparing: Boolean) {
    val mine = state.sections.firstOrNull { it.isMe }
    val lines = if (showPreparing && state.surpriseButton) preparingLines(state) else emptyList()
    val canPrepare = showPreparing && state.surpriseButton && state.canPrepareSurprise && actions.onOpenSurprisePicker != null
    if (lines.isNotEmpty() || canPrepare) {
        item(key = "surprise-bar") { SurpriseBar(lines, canPrepare, actions.onOpenSurprisePicker, actions.onOpenList) }
    }
    if (mine != null) item(key = "my-wishes") { MyWishesCard(state, mine, actions) }
    val soon = birthdaysSoon(state)
    if (soon.isNotEmpty()) {
        item(key = "soon-title") { Title(stringResource(R.string.gifts_v2_soon)) }
        soon.forEach { birthday ->
            item(key = "soon-${birthday.member.id}") { SoonRow(state, birthday, actions.onOpenList) }
        }
    }
    val others = state.sections.filter { !it.isMe }
    if (others.isNotEmpty()) {
        item(key = "family-title") { Title(stringResource(R.string.gifts_v2_family)) }
        householdBlocks(others, mine?.member?.householdId).forEach { block ->
            item(key = "household-${block.key}-label") {
                SectionLabel(block.name ?: stringResource(R.string.members_other_household))
            }
            block.sections.forEach { section ->
                item(key = "person-${section.member.id}") {
                    val fresh = section.wishes?.id?.let { state.newWishes[it] } ?: 0
                    PersonRow(state, section, fresh, actions.onOpenList)
                }
            }
        }
    }
    if (state.archived.isNotEmpty()) item(key = "archived") { ArchivedRow(state, actions.onOpenList) }
}

/** The people of one household, in the order of the Famille tab; [mine] opens on its own. */
internal data class HouseholdBlock(val key: String, val name: String?, val sections: List<GiftPersonSection>, val mine: Boolean)

/** The households of [others], mine first, the rest in the order of the Famille tab. Pure, for the tests. */
internal fun householdBlocks(others: List<GiftPersonSection>, myHouseholdId: String?): List<HouseholdBlock> {
    val groups = LinkedHashMap<String, MutableList<GiftPersonSection>>()
    others.forEach { section -> groups.getOrPut(section.member.householdId ?: "") { mutableListOf() } += section }
    val blocks = groups.map { (id, sections) ->
        HouseholdBlock(id.ifEmpty { "other" }, sections.first().householdName, sections, mine = id.isNotEmpty() && id == myHouseholdId)
    }
    return blocks.filter { it.mine } + blocks.filterNot { it.mine }
}

/** The birthdays of the next 30 days, nearest first, never this member's own. Pure, for the tests. */
internal fun birthdaysSoon(state: GiftsUiState.Success): List<Birthday> {
    val until = state.today.plusDays(UPCOMING_BIRTHDAY_DAYS)
    val mineId = state.sections.firstOrNull { it.isMe }?.member?.id
    return state.birthdays.filter { it.member.id != mineId && it.date <= until }.sortedBy { it.date }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
private fun inDays(today: LocalDate, date: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date).toInt()
    return if (days <= 0) stringResource(R.string.day_today).replaceFirstChar { it.lowercase() } else pluralStringResource(R.plurals.gifts_in_days, days, days)
}

/**
 * The black bar at the top: « n surprises en préparation » (one opens it, several unfold) and, when
 * the button is on, « Préparer une surprise ». Never on the phone of the person concerned.
 */
@Composable
private fun SurpriseBar(lines: List<PreparingLine>, canPrepare: Boolean, onPrepare: (() -> Unit)?, onOpenList: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.fillMaxWidth()) {
        Column {
            if (lines.isNotEmpty()) {
                BarRow(
                    text = pluralStringResource(R.plurals.gifts_v2_surprises_bar, lines.size, lines.size),
                    onClick = { if (lines.size == 1) onOpenList(lines.first().target()) else open = !open },
                )
                if (open) lines.forEach { line -> BarLine(line, onOpenList) }
            }
            if (canPrepare && onPrepare != null) BarRow(text = stringResource(R.string.gifts_prepare_surprise), onClick = onPrepare)
        }
    }
}

@Composable
private fun BarRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(Sun, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun BarLine(line: PreparingLine, onOpenList: (String) -> Unit) {
    val member = line.member()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onOpenList(line.target()) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MemberAvatar(member, size = 40.dp)
        Text(stringResource(R.string.gifts_preparing_for, member.firstName), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

private fun PreparingLine.target(): String = when (this) {
    is PreparingLine.OfList -> list.id
    is PreparingLine.OfIdeas -> ideasListId(member.id)
}

private fun PreparingLine.member() = when (this) {
    is PreparingLine.OfList -> member
    is PreparingLine.OfIdeas -> member
}

/** « Mes envies »: a yellow edge, how many the family sees, « Ajouter une envie » and « Ma fiche ». */
@Composable
private fun MyWishesCard(state: GiftsUiState.Success, mine: GiftPersonSection, actions: GiftsV2Actions) {
    val listId = mine.wishes?.id ?: wishesListId(mine.member.id)
    val count = mine.wishes?.let { state.wishPreviews[it.id]?.count } ?: 0
    EdgedCard(Sun) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MemberAvatar(mine.member, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.gift_my_wishes), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(
                    if (count == 0) stringResource(R.string.gifts_v2_my_wishes_none) else pluralStringResource(R.plurals.gifts_v2_my_wishes_line, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { actions.onAddWish(listId) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.gifts_v2_add_wish)) }
            OutlinedButton(
                onClick = { actions.onOpenMyPage?.invoke() ?: actions.onOpenList(listId) },
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.gifts_v2_my_page)) }
        }
    }
}

private fun wishesFor(state: GiftsUiState.Success, memberId: String): String =
    state.sections.firstOrNull { it.member.id == memberId }?.wishes?.id ?: wishesListId(memberId)

/** A birthday of the next 30 days: the person, « dans 9 jours »; it opens their list of wishes. */
@Composable
private fun SoonRow(state: GiftsUiState.Success, birthday: Birthday, onOpenList: (String) -> Unit) {
    val member = birthday.member
    val name = member.firstName
    ClayCard(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        onClick = { onOpenList(wishesFor(state, member.id)) },
        onClickLabel = stringResource(R.string.gifts_v2_open_wishes, name),
        contentPadding = ClaySpacing.card,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MemberAvatar(member, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.gifts_birthday_of, name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(shape = RoundedCornerShape(50), color = Sun) {
                Text(
                    inDays(state.today, birthday.date),
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * One person (récit 91): an edge of their color, avatar, first name, « 6 envies » and, when there
 * are some, the black pill « 2 nouvelles ». Never how many ideas nor reservations. One gesture:
 * it opens their list of wishes (the page of the person is one link away from it).
 */
@Composable
private fun PersonRow(state: GiftsUiState.Success, section: GiftPersonSection, fresh: Int, onOpenList: (String) -> Unit) {
    val member = section.member
    val count = section.wishes?.let { state.wishPreviews[it.id]?.count } ?: 0
    EdgedCard(memberColor(member), onClick = { onOpenList(wishesFor(state, member.id)) }, onClickLabel = stringResource(R.string.gifts_v2_open_wishes, member.firstName)) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MemberAvatar(member, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(member.firstName, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (count > 0) pluralStringResource(R.plurals.gifts_wishes_count, count, count) else stringResource(R.string.gifts_next_no_wish),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (fresh > 0) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
                    Text(
                        pluralStringResource(R.plurals.gifts_new_wishes, fresh, fresh),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** A card with a thick edge of [edge] on its left; with [onClick], the whole card is one button. */
@Composable
private fun EdgedCard(edge: Color, onClick: (() -> Unit)? = null, onClickLabel: String? = null, content: @Composable () -> Unit) {
    ClayCard(Modifier.fillMaxWidth(), onClick = onClick, onClickLabel = onClickLabel, contentPadding = ClaySpacing.card) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRoundRect(edge, Offset.Zero, Size(6.dp.toPx(), size.height), CornerRadius(3.dp.toPx())) }
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun PlainRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    ClayCard(Modifier.fillMaxWidth().heightIn(min = 56.dp), onClick = onClick, contentPadding = ClaySpacing.card) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** « Listes archivées (2) », a plain line at the bottom; it opens them in place. */
@Composable
private fun ArchivedRow(state: GiftsUiState.Success, onOpenList: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlainRow(Icons.Outlined.Inventory2, stringResource(R.string.gifts_v2_archived, state.archived.size)) { open = !open }
        if (open) {
            state.archived.forEach { (list, name) ->
                ListCard(
                    title = list.title,
                    subtitle = stringResource(R.string.gifts_archived_for, name, listSubtitle(list, name, state.today)),
                    tint = fr.familia.app.core.theme.ClayTint.Plain,
                    onClick = { onOpenList(list.id) },
                )
            }
        }
    }
}

/** The yellow of « Mes envies » and of the surprise pictogram, and the ink drawn on it. */
private val Sun = Color(0xFFF5C451)
private val Ink = Color(0xFF2B1D10)
