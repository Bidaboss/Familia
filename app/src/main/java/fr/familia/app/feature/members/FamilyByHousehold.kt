package fr.familia.app.feature.members

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.HouseholdGroup
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.nextBirthday
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.MemberAvatar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Famille tab « par foyer » (PRD récit 64, PRODUIT-92 et 94, Kévin le 18/11): me first, then
 * the others of my household, then the other households, each folded on one line.
 */
data class FamilyTabLayout(
    val me: Member?,
    /** My household, without me; null when I have none (« Autres »). */
    val mine: HouseholdGroup?,
    /** The other households, in the order of [groups], « Autres » last, never with me. */
    val others: List<HouseholdGroup>,
)

/** Pure, so the tests check the order without Compose. [groups] come already sorted (householdGroups). */
fun familyTabLayout(groups: List<HouseholdGroup>, meId: String): FamilyTabLayout {
    val me = groups.firstNotNullOfOrNull { group -> group.members.firstOrNull { it.id == meId } }
    val mineGroup = groups.firstOrNull { group -> group.household != null && group.members.any { it.id == meId } }
    val mine = mineGroup?.let { it.copy(members = it.members.filter { m -> m.id != meId }) }
    val others = groups
        .filter { it !== mineGroup }
        .map { it.copy(members = it.members.filter { m -> m.id != meId }) }
        .filter { it.members.isNotEmpty() }
    return FamilyTabLayout(me, mine, others)
}

private val BIRTHDAY = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)

/**
 * Adds the tab « par foyer » to a list. [row] draws one member as the classic tab does (same
 * clicks and rights); [open] holds the households unfolded by the reader.
 */
internal fun LazyListScope.familyByHousehold(
    layout: FamilyTabLayout,
    today: LocalDate,
    onOpenMine: ((String) -> Unit)?,
    householdName: @Composable (HouseholdGroup) -> String,
    header: @Composable (HouseholdGroup) -> Unit,
    row: @Composable (Member) -> Unit,
    open: List<String>,
    onToggle: (String) -> Unit,
) {
    layout.me?.let { me ->
        item(key = "me") { MeCard(me, today, onOpenMine) }
    }
    layout.mine?.let { mine ->
        item(key = "mine-header") { header(mine) }
        items(mine.members, key = { "mine-${it.id}" }) { row(it) }
    }
    if (layout.others.isNotEmpty()) {
        item(key = "others-title") {
            Text(
                stringResource(R.string.members_other_households),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 24.dp, bottom = 4.dp)
                    .semantics { heading() },
            )
        }
    }
    layout.others.forEach { group ->
        val key = group.household?.id ?: "other"
        val unfolded = key in open
        item(key = "fold-$key") {
            FoldedHousehold(group, householdName(group), unfolded) { onToggle(key) }
        }
        if (unfolded) items(group.members, key = { "other-$key-${it.id}" }) { row(it) }
    }
}

@Composable
private fun MeCard(me: Member, today: LocalDate, onOpen: ((String) -> Unit)?) {
    ClayCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
        emphasis = true,
        contentPadding = ClaySpacing.card,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(me, size = 88.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    me.firstName,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                me.birthDate?.let { born ->
                    Text(
                        stringResource(R.string.members_me_birthday, nextBirthday(born, today).format(BIRTHDAY)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (onOpen != null) {
            ClayLink(stringResource(R.string.members_me_open), onClick = { onOpen(me.id) }, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** One household of the others, folded: its name, the small photos of its members, and a chevron. */
@Composable
private fun FoldedHousehold(group: HouseholdGroup, name: String, unfolded: Boolean, onToggle: () -> Unit) {
    val count = group.members.size
    val state = if (unfolded) {
        stringResource(R.string.members_household_unfolded)
    } else {
        pluralStringResource(R.plurals.members_household_folded, count, count)
    }
    val action = stringResource(if (unfolded) R.string.members_household_fold else R.string.members_household_unfold)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = action, onClick = onToggle)
                .heightIn(min = 56.dp)
                .padding(horizontal = ClaySpacing.screen, vertical = 8.dp)
                .clearAndSetSemantics {
                    contentDescription = name
                    stateDescription = state
                    heading()
                    onClick(label = action) { onToggle(); true }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (!unfolded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        group.members.take(MAX_FACES).forEach { MemberAvatar(it, size = 32.dp) }
                        if (count > MAX_FACES) {
                            Text(
                                "+${count - MAX_FACES}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        }
                    }
                }
            }
            Icon(if (unfolded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        }
    }
}

private const val MAX_FACES = 6
