package fr.familia.app.feature.gifts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.ideasListId
import fr.familia.app.core.model.wishesListId
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.SectionLabel
import fr.familia.app.core.ui.SideBySide
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.core.ui.memberColor
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * The new Cadeaux tab (PRODUIT-119 and 120, maquettes C1 à C5), with the single list (PRODUIT-131:
 * no switch of its own). From the top: the surprises in progress, « À venir »
 * with the next birthday in relief, the occasions, « Mes envies », then the family by household,
 * mine open and the others folded, one compact card per person in their color.
 *
 * Never a count of ideas nor of reservations: only « N envies », what the person sees of their own
 * list. Nothing of a surprise in « Voir comme » (the caller says so).
 */

/** What the new tab leads to. */
internal class GiftsV2Actions(
    val onOpenList: (String) -> Unit,
    val onAllBirthdays: () -> Unit,
    val onOpenMyPage: (() -> Unit)?,
    val onAddWish: (listId: String) -> Unit,
)

internal fun LazyListScope.giftsV2Content(state: GiftsUiState.Success, actions: GiftsV2Actions, showPreparing: Boolean) {
    // A child reads the wishes of the others, never their ideas (PRODUIT, 18/11).
    val adult = state.canPrepareSurprise
    val mine = state.sections.firstOrNull { it.isMe }
    val door: (String) -> Boolean = { id -> adult && surpriseDoor(state, id, showPreparing) }
    if (showPreparing && state.surpriseButton) {
        items(preparingLines(state), key = { "preparing-${it.key}" }) { line -> SurpriseCard(state, line, actions.onOpenList) }
    }
    // A child finds their own wishes first (C5).
    if (!adult && mine != null) item(key = "my-wishes") { MyWishesCard(state, mine, actions, showTitles = true) }
    if (state.moment == null) upcomingV2(state, actions, door)
    if (adult && mine != null && state.moment !is GiftMoment.BirthdayOf) {
        item(key = "my-wishes") { MyWishesCard(state, mine, actions, showTitles = false) }
    }
    val others = state.sections.filter { !it.isMe }
    if (others.isNotEmpty()) {
        item(key = "family-title") { TitleWithRule(stringResource(R.string.gifts_v2_family)) }
        householdBlocks(others, mine?.member?.householdId).forEach { block ->
            item(key = "household-${block.key}") {
                HouseholdSection(state, block, openAtFirst = block.mine || state.moment != null, door = door, onOpenList = actions.onOpenList)
            }
        }
    }
    if (state.archived.isNotEmpty()) item(key = "archived") { ArchivedLink(state, actions.onOpenList) }
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

@Composable
private fun TitleWithRule(text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Box(Modifier.weight(1f).heightIn(min = 1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

/** One surprise in progress (C2): a dark card, never on the phone of its person. */
@Composable
private fun SurpriseCard(state: GiftsUiState.Success, line: PreparingLine, onOpenList: (String) -> Unit) {
    val (title, subtitle, member, target) = when (line) {
        is PreparingLine.OfList -> Quad(line.list.title, stringResource(R.string.gifts_preparing_for, line.member.firstName), line.member, line.list.id)
        is PreparingLine.OfIdeas -> Quad(
            stringResource(R.string.gifts_preparing_ideas_title, line.member.firstName),
            line.birthday?.let { stringResource(R.string.gifts_v2_surprise_birthday, inDays(state.today, it)) }
                ?: stringResource(R.string.gifts_preparing_ideas),
            line.member,
            ideasListId(line.member.id),
        )
    }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).background(Sun, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
                }
                Text(stringResource(R.string.gifts_v2_surprise_label).uppercase(), style = MaterialTheme.typography.labelLarge, color = Sun)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MemberAvatar(member, size = 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                onClick = { onOpenList(target) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseOnSurface, contentColor = MaterialTheme.colorScheme.inverseSurface),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.gifts_v2_open_surprise)) }
        }
    }
}

private data class Quad(val title: String, val subtitle: String, val member: fr.familia.app.core.model.Member, val target: String)

@Composable
private fun inDays(today: LocalDate, date: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date).toInt()
    return if (days <= 0) stringResource(R.string.day_today).replaceFirstChar { it.lowercase() } else pluralStringResource(R.plurals.gifts_in_days, days, days)
}

/** The birthday in relief: the first one in the next 30 days (C2), none out of season (C1). */
internal fun birthdayInRelief(state: GiftsUiState.Success): Birthday? =
    state.birthdays.firstOrNull()?.takeIf { it.date <= state.today.plusDays(UPCOMING_BIRTHDAY_DAYS) }

/** « À venir »: the birthday in relief, then the other occasions in compact lines. */
private fun LazyListScope.upcomingV2(state: GiftsUiState.Success, actions: GiftsV2Actions, door: (String) -> Boolean) {
    item(key = "upcoming-title") { TitleWithRule(stringResource(R.string.gifts_upcoming_title)) }
    val relief = birthdayInRelief(state)
    relief?.let { birthday -> item(key = "relief") { ReliefBirthday(state, birthday, actions, door) } }
    val until = state.today.plusDays(UPCOMING_BIRTHDAY_DAYS)
    val birthdays = state.birthdays.filter { it != relief && (relief == null || it.date <= until) }
    // No Christmas line any more (PRODUIT-134): only the birthdays. Christmas stays an occasion of the wishes.
    birthdays.sortedBy { it.date }.forEach { birthday ->
        val date = birthday.date
        item(key = "line-${birthday.member.id}") {
            val me = state.sections.any { it.isMe && it.member.id == birthday.member.id }
            CompactLine(
                icon = Icons.Outlined.Cake,
                title = stringResource(R.string.gifts_birthday_of, birthday.member.firstName),
                subtitle = dateLine(state.today, date),
                onClick = {
                    // No more filter by occasion (PRODUIT-134): their wishes, as « Ses envies ».
                    if (me) actions.onOpenMyPage?.invoke() ?: actions.onOpenList(wishesListId(birthday.member.id))
                    else actions.onOpenList(wishesFor(state, birthday.member.id))
                },
            )
        }
    }
    item(key = "birthdays-all") { ClayLink(stringResource(R.string.gifts_all_birthdays), onClick = actions.onAllBirthdays) }
}

/** « Jeudi 27 novembre · dans 9 jours ». */
@Composable
private fun dateLine(today: LocalDate, date: LocalDate): String =
    stringResource(R.string.gifts_v2_date_line, frenchDate(date, "EEEE d MMMM").replaceFirstChar { it.uppercase() }, inDays(today, date))

/** The next birthday in relief (C2): bordered in the person's color, with « Ses envies » and « Surprise » (PRODUIT-133). */
@Composable
private fun ReliefBirthday(state: GiftsUiState.Success, birthday: Birthday, actions: GiftsV2Actions, door: (String) -> Boolean) {
    val member = birthday.member
    val color = memberColor(member)
    val me = state.sections.any { it.isMe && it.member.id == member.id }
    val age = pluralStringResource(R.plurals.birthday_age, birthday.age, birthday.age)
    ClayCard(Modifier.fillMaxWidth().border(BorderStroke(3.dp, color), RoundedCornerShape(28.dp)), contentPadding = ClaySpacing.card, emphasis = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MemberAvatar(member, size = 72.dp)
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.gifts_v2_next_birthday))
                Text(
                    if (me) stringResource(R.string.gifts_v2_my_age, age) else stringResource(R.string.gifts_v2_their_age, member.firstName, age),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(dateLine(state.today, birthday.date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val wishes = { if (me) actions.onOpenMyPage?.invoke() ?: actions.onOpenList(wishesListId(member.id)) else actions.onOpenList(wishesFor(state, member.id)) }
            DarkButton(
                stringResource(if (me) R.string.gift_my_wishes else R.string.gifts_v2_their_wishes),
                Modifier.weight(1f),
                description = if (me) null else stringResource(R.string.gift_wishes_of, member.firstName),
                onClick = wishes,
            )
            if (!me && door(member.id)) {
                OutlineButton(
                    stringResource(R.string.gifts_v2_surprise),
                    Modifier.weight(1f),
                    description = stringResource(R.string.gift_ideas_of, member.firstName),
                ) { actions.onOpenList(ideasFor(state, member.id)) }
            }
        }
    }
}

private fun wishesFor(state: GiftsUiState.Success, memberId: String): String =
    state.sections.firstOrNull { it.member.id == memberId }?.wishes?.id ?: wishesListId(memberId)

/**
 * The « Surprise » of a person's card (PRODUIT-133): only once a surprise is in progress for them,
 * since « Préparer une surprise » starts one; on every card while that button is off, the only way in.
 * Never in « Voir comme »: it would tell that a surprise is there. Pure, for the tests.
 */
internal fun surpriseDoor(state: GiftsUiState.Success, memberId: String, showPreparing: Boolean): Boolean =
    showPreparing && (!state.surpriseButton || memberId in state.surprisePreparedFor)

private fun ideasFor(state: GiftsUiState.Success, memberId: String): String =
    state.sections.firstOrNull { it.member.id == memberId }?.ideas?.id ?: ideasListId(memberId)

/** A birthday after the one in relief: an icon, the day, a chevron. */
@Composable
private fun CompactLine(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ClayCard(Modifier.fillMaxWidth().heightIn(min = 56.dp), onClick = onClick, onClickLabel = stringResource(R.string.gifts_v2_open), contentPadding = ClaySpacing.card) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** « Mes envies »: a yellow edge, how many the family sees, « Ajouter une envie » and « Voir ». */
@Composable
private fun MyWishesCard(state: GiftsUiState.Success, mine: GiftPersonSection, actions: GiftsV2Actions, showTitles: Boolean) {
    val listId = mine.wishes?.id ?: wishesListId(mine.member.id)
    val preview = mine.wishes?.let { state.wishPreviews[it.id] }
    val count = preview?.count ?: 0
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
        if (showTitles) preview?.titles?.forEach { Text("· $it", style = MaterialTheme.typography.bodyLarge) }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DarkButton(stringResource(R.string.gifts_v2_add_wish), Modifier.weight(1f)) { actions.onAddWish(wishesListId(mine.member.id)) }
            OutlineButton(stringResource(R.string.gifts_v2_see), description = stringResource(R.string.gifts_v2_see_my_wishes)) {
                actions.onOpenMyPage?.invoke() ?: actions.onOpenList(listId)
            }
        }
    }
}

/** A household: open, its people one card each; folded, one line with their avatars. */
@Composable
private fun HouseholdSection(state: GiftsUiState.Success, block: HouseholdBlock, openAtFirst: Boolean, door: (String) -> Boolean, onOpenList: (String) -> Unit) {
    var open by rememberSaveable(block.key, openAtFirst) { mutableStateOf(openAtFirst) }
    val name = block.name ?: stringResource(R.string.members_other_household)
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.between)) {
        ClayCard(
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            onClick = { open = !open },
            onClickLabel = stringResource(if (open) R.string.gifts_v2_fold else R.string.gifts_v2_unfold, name),
            contentPadding = ClaySpacing.card,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    block.sections.take(4).forEachIndexed { index, section ->
                        MemberAvatar(section.member, Modifier.offset(x = (index * 22).dp), size = 40.dp)
                    }
                    Box(Modifier.size(width = (40 + 22 * (block.sections.take(4).size - 1)).dp, height = 40.dp))
                }
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                Icon(if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, contentDescription = null)
            }
        }
        if (open) block.sections.forEach { PersonCard(state, it, door, onOpenList) }
    }
}

/**
 * One person (PRODUIT-119): a thick edge of their color, their avatar, first name and birthday, the
 * pill « 6 envies » and two buttons. Never how many ideas nor reservations (PRODUIT-120).
 */
@Composable
private fun PersonCard(state: GiftsUiState.Success, section: GiftPersonSection, door: (String) -> Boolean, onOpenList: (String) -> Unit) {
    val member = section.member
    val count = section.wishes?.let { state.wishPreviews[it.id]?.count } ?: 0
    EdgedCard(memberColor(member)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MemberAvatar(member, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(member.firstName, style = MaterialTheme.typography.titleMedium)
                member.birthDate?.let {
                    Text(
                        stringResource(R.string.gifts_v2_birthday_on, frenchDate(it, "d MMMM")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (count > 0) {
                Surface(shape = RoundedCornerShape(50), color = Peach.copy(alpha = 0.3f)) {
                    Text(
                        pluralStringResource(R.plurals.gifts_wishes_count, count, count),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        SideBySide(Modifier.padding(top = 4.dp)) { item ->
            Button(
                onClick = { onOpenList(section.wishes?.id ?: wishesListId(member.id)) },
                colors = ButtonDefaults.buttonColors(containerColor = Peach.copy(alpha = 0.3f), contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = item.heightIn(min = 56.dp).labelled(stringResource(R.string.gift_wishes_of, member.firstName)),
            ) { Text(stringResource(R.string.gifts_v2_their_wishes)) }
            if (door(member.id)) {
                OutlineButton(stringResource(R.string.gifts_v2_surprise), item, description = stringResource(R.string.gift_ideas_of, member.firstName)) {
                    onOpenList(section.ideas?.id ?: ideasListId(member.id))
                }
            }
        }
    }
}

private fun Modifier.labelled(label: String): Modifier = semantics { contentDescription = label }

/** A card with a thick edge of [edge] on its left. */
@Composable
private fun EdgedCard(edge: Color, content: @Composable () -> Unit) {
    ClayCard(Modifier.fillMaxWidth(), contentPadding = ClaySpacing.card) {
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
private fun DarkButton(text: String, modifier: Modifier = Modifier, description: String? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface),
        modifier = modifier.heightIn(min = 56.dp).then(if (description != null) Modifier.labelled(description) else Modifier),
    ) { Text(text) }
}

@Composable
private fun OutlineButton(text: String, modifier: Modifier = Modifier, description: String? = null, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp).then(if (description != null) Modifier.labelled(description) else Modifier),
    ) { Text(text) }
}

/** « Listes archivées (2) », discreet at the bottom; it opens them in place. */
@Composable
private fun ArchivedLink(state: GiftsUiState.Success, onOpenList: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { open = !open }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.gifts_v2_archived, state.archived.size), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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

/** The sparkle of « Préparer une surprise » on the dark button. */
internal val V2_SUN: Color get() = Sun
private val Ink = Color(0xFF2B1D10)

/** The peach of « Ses envies » (C2). */
private val Peach: Color
    @Composable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFCC8556) else Color(0xFFF0A878)
