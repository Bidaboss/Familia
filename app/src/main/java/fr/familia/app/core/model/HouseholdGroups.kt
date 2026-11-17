package fr.familia.app.core.model

/**
 * A household and its members, adults first then children, each sorted by first name. [household]
 * is null for the "Autres" group: members whose household is missing, so nobody silently disappears.
 */
data class HouseholdGroup(val household: Household?, val members: List<Member>)

private val memberOrder = compareBy<Member>({ it.profile != MemberProfile.Adult }, { it.firstName.lowercase() })

/**
 * The family by household, as the Famille tab shows it and every list of members follows (règle du
 * 17/11): households in name order; inside each, adults then children, by first name. Members whose
 * household does not exist come last, in a group without household ("Autres"). Empty groups are left out.
 */
fun householdGroups(family: Family): List<HouseholdGroup> {
    val known = family.households.map { it.id }.toSet()
    val households = family.households
        .sortedBy { it.name.lowercase() }
        .map { household -> HouseholdGroup(household, family.members.filter { it.householdId == household.id }.sortedWith(memberOrder)) }
    val others = HouseholdGroup(null, family.members.filter { it.householdId !in known }.sortedWith(memberOrder))
    return (households + others).filter { it.members.isNotEmpty() }
}
