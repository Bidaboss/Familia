package fr.familia.app.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme

/*
 * The common picker of members (règle de l'admin, 17/11): every list of members is sorted by household,
 * and where members are picked, ticking a household ticks all of its members. A household is half
 * ticked when only some of its members are. Each screen keeps its own choice of who may be picked
 * (with the app, me aside…) and its own order of households; the picker only shows and toggles.
 */

/** One household of the picker: its name and the members that may be picked in it, in order. */
data class PickGroup(val name: String, val members: List<Member>)

/** The household box: on when all its members are picked, half when some are, off when none. */
fun householdState(selected: Set<String>, memberIds: Collection<String>): ToggleableState = when {
    memberIds.isEmpty() || memberIds.none { it in selected } -> ToggleableState.Off
    memberIds.all { it in selected } -> ToggleableState.On
    else -> ToggleableState.Indeterminate
}

/** Ticking a household: all of its members when one is missing, none when all were already picked. */
fun toggleHousehold(selected: Set<String>, memberIds: Collection<String>): Set<String> =
    if (memberIds.all { it in selected }) selected - memberIds.toSet() else selected + memberIds

fun toggleMember(selected: Set<String>, memberId: String): Set<String> =
    if (memberId in selected) selected - memberId else selected + memberId

/**
 * The households of [groups], each with a box that picks all its members, then one box per member.
 * Rows are 56 dp high and read as checkboxes. Stateless: [onChange] receives the new selection.
 */
@Composable
fun HouseholdMemberPicker(
    groups: List<PickGroup>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (Member) -> String = { it.firstName },
    /** Always picked and not untickable (the parents of a child…): shown ticked and greyed. */
    locked: Set<String> = emptySet(),
) {
    Column(modifier) {
        groups.forEach { group ->
            val ids = group.members.map { it.id }
            val free = ids.filter { it !in locked }
            val state = householdState(selected + locked, ids)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(top = 4.dp)
                    .triStateToggleable(state = state, enabled = enabled && free.isNotEmpty(), role = Role.Checkbox) { onChange(toggleHousehold(selected, free)) }
                    .semantics { heading() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TriStateCheckbox(state = state, onClick = null, enabled = enabled && free.isNotEmpty())
                Spacer(Modifier.width(16.dp))
                Text(group.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            group.members.forEach { member ->
                val isLocked = member.id in locked
                val checked = isLocked || member.id in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(start = 32.dp)
                        .toggleable(value = checked, enabled = enabled && !isLocked, role = Role.Checkbox) { onChange(toggleMember(selected, member.id)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled && !isLocked)
                    Spacer(Modifier.width(16.dp))
                    Text(label(member), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@FamiliaPreviews
@Composable
private fun HouseholdMemberPickerPreview() {
    fun m(id: String, name: String, home: String, profile: MemberProfile = MemberProfile.Adult) =
        Member(id, name, null, null, home, profile, isAdmin = false, parentIds = emptyList(), uid = null)
    var picked by remember { mutableStateOf(setOf("a", "b", "n")) }
    FamiliaTheme {
        Surface {
            HouseholdMemberPicker(
                groups = listOf(
                    PickGroup("Les Martin", listOf(m("a", "Camille", "h1"), m("b", "Jules", "h1"), m("c", "Nina", "h1", MemberProfile.ChildWithApp))),
                    PickGroup("Les Bernard", listOf(m("n", "Noé", "h2"), m("l", "Lou", "h2"))),
                ),
                selected = picked,
                onChange = { picked = it },
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
