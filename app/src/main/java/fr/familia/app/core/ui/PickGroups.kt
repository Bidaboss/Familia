package fr.familia.app.core.ui

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile

/**
 * [members] by household for [HouseholdMemberPicker] (règle de Kévin, 17/11): [firstHouseholdId]
 * first (mine, usually), then the others by name; in each, the adults then the children, by first
 * name. A member whose household is gone ends in a last group named [othersName].
 */
fun pickGroups(members: List<Member>, family: Family, firstHouseholdId: String? = null, othersName: String = ""): List<PickGroup> =
    pickGroups(members, family.households, firstHouseholdId, othersName)

fun pickGroups(members: List<Member>, households: List<Household>, firstHouseholdId: String? = null, othersName: String = ""): List<PickGroup> {
    val order = compareBy<Member>({ it.profile != MemberProfile.Adult }, { it.firstName.lowercase() })
    val known = households.map { it.id }.toSet()
    val groups = households
        .sortedWith(compareBy({ it.id != firstHouseholdId }, { it.name.lowercase() }))
        .mapNotNull { household ->
            members.filter { it.householdId == household.id }.sortedWith(order).takeIf { it.isNotEmpty() }
                ?.let { PickGroup(household.name, it) }
        }
    val others = members.filter { it.householdId !in known }.sortedWith(order)
    return if (others.isEmpty()) groups else groups + PickGroup(othersName, others)
}

/**
 * The order of a flat list of members (« Viennent : … »): by household as [pickGroups] puts them,
 * [firstHouseholdId] first, then the adults and the children by first name.
 */
fun householdOrder(households: List<Household>, firstHouseholdId: String? = null): Comparator<Member> {
    val rank = households.sortedWith(compareBy({ it.id != firstHouseholdId }, { it.name.lowercase() }))
        .mapIndexed { i, h -> h.id to i }.toMap()
    return compareBy<Member>({ rank[it.householdId] ?: Int.MAX_VALUE }, { it.profile != MemberProfile.Adult }, { it.firstName.lowercase() })
}
