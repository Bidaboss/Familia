package fr.familia.app.core.ui

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.householdGroups

/**
 * [members] by household for [HouseholdMemberPicker] (règle de Kévin, 17/11), in the order of
 * [householdGroups]: households by name, adults then children by first name, members whose household
 * is gone last, in a group named [othersName]. [firstHouseholdId] (mine, usually) comes first.
 */
fun pickGroups(members: List<Member>, family: Family, firstHouseholdId: String? = null, othersName: String = ""): List<PickGroup> =
    pickGroups(members, family.households, firstHouseholdId, othersName)

fun pickGroups(members: List<Member>, households: List<Household>, firstHouseholdId: String? = null, othersName: String = ""): List<PickGroup> =
    householdGroups(Family(households, members))
        .sortedBy { it.household == null || it.household.id != firstHouseholdId }
        .map { PickGroup(it.household?.name ?: othersName, it.members) }

/**
 * The order of a flat list of members (« Viennent : … »): the order of [pickGroups], with
 * [firstHouseholdId] first.
 */
fun householdOrder(households: List<Household>, firstHouseholdId: String? = null): Comparator<Member> =
    Comparator { a, b ->
        val order = pickGroups(listOf(a, b), households, firstHouseholdId).flatMap { it.members }
        order.indexOf(a) - order.indexOf(b)
    }
