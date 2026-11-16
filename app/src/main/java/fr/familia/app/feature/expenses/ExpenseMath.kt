package fr.familia.app.feature.expenses

import fr.familia.app.core.model.Expense
import fr.familia.app.core.model.ExpenseSettlement
import fr.familia.app.core.model.HouseholdBalance
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.SplitMode
import fr.familia.app.core.model.Transfer
import kotlin.math.roundToLong

/**
 * The arithmetic of the accounts (PRD rév. 193, récit 16 bis). Pure functions on whole cents:
 * every split adds up exactly to the expense, so the balances always add up to zero.
 */

/** One share per adult, a half per child, for each household of [participants]. */
fun defaultShares(participants: List<Member>): Map<String, Double> =
    participants.groupBy { it.householdId }
        .mapValues { (_, members) -> members.sumOf { if (it.profile == MemberProfile.Adult) 1.0 else 0.5 } }

/**
 * What each household of [expense] should pay, in cents. [shares] are the shares of the book;
 * a household with no share left counts for one in [SplitMode.Shares], so it is never free.
 */
fun splitOf(expense: Expense, shares: Map<String, Double>): Map<String, Long> {
    val households = expense.householdIds.distinct()
    if (households.isEmpty()) return emptyMap()
    return when (expense.mode) {
        SplitMode.Amounts -> households.associateWith { expense.amounts[it] ?: 0L }
        SplitMode.Equal -> spread(expense.amountCents, households.associateWith { 1.0 })
        SplitMode.Shares -> spread(expense.amountCents, households.associateWith { (shares[it] ?: 0.0).takeIf { s -> s > 0 } ?: 1.0 })
    }
}

/**
 * Spreads [total] cents in proportion to [weights], by the largest remainder: each one gets its
 * rounded-down part, then the cents left go one by one to the largest remainders (in the order
 * of the ids on a tie, so the result never depends on the order of a map).
 */
internal fun spread(total: Long, weights: Map<String, Double>): Map<String, Long> {
    val sum = weights.values.sum()
    if (sum <= 0.0) return weights.mapValues { 0L }
    val exact = weights.mapValues { (_, w) -> total * w / sum }
    val floors = exact.mapValues { (_, v) -> kotlin.math.floor(v).toLong() }
    var left = total - floors.values.sum()
    val order = exact.keys.sortedWith(compareByDescending<String> { exact.getValue(it) - floors.getValue(it) }.thenBy { it })
    val result = floors.toMutableMap()
    for (id in order) {
        if (left <= 0) break
        result[id] = result.getValue(id) + 1
        left--
    }
    return result
}

/**
 * The balance of each household of [households]: what it paid, what it should have paid, and the
 * reimbursements already ticked. A household that appears only in an expense is counted too.
 */
fun balancesOf(
    households: Collection<String>,
    expenses: List<Expense>,
    settlements: List<ExpenseSettlement>,
    shares: Map<String, Double>,
): List<HouseholdBalance> {
    val paid = mutableMapOf<String, Long>()
    val owed = mutableMapOf<String, Long>()
    for (expense in expenses) {
        paid.merge(expense.payerHouseholdId, expense.amountCents, { a, b -> a + b })
        splitOf(expense, shares).forEach { (id, cents) -> owed.merge(id, cents, { a, b -> a + b }) }
    }
    val sent = settlements.groupBy { it.fromHouseholdId }.mapValues { (_, s) -> s.sumOf { it.amountCents } }
    val received = settlements.groupBy { it.toHouseholdId }.mapValues { (_, s) -> s.sumOf { it.amountCents } }
    val ids = (households + paid.keys + owed.keys + sent.keys + received.keys).distinct().sorted()
    return ids.map { HouseholdBalance(it, paid[it] ?: 0, owed[it] ?: 0, sent[it] ?: 0, received[it] ?: 0) }
}

/**
 * « Qui rembourse qui »: few transfers that settle every balance. The household that owes the most
 * pays the one owed the most, as much as it can, and so on; it never takes more than one transfer
 * less than the households involved. A balance under one cent is settled.
 */
fun transfersOf(balances: List<HouseholdBalance>): List<Transfer> {
    val debtors = balances.filter { it.netCents < 0 }.map { it.householdId to -it.netCents }.toMutableList()
    val creditors = balances.filter { it.netCents > 0 }.map { it.householdId to it.netCents }.toMutableList()
    val transfers = mutableListOf<Transfer>()
    while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
        debtors.sortWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
        creditors.sortWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
        val (from, owes) = debtors.first()
        val (to, due) = creditors.first()
        val amount = minOf(owes, due)
        transfers += Transfer(from, to, amount)
        if (owes == amount) debtors.removeAt(0) else debtors[0] = from to owes - amount
        if (due == amount) creditors.removeAt(0) else creditors[0] = to to due - amount
    }
    return transfers
}

/** [original] cents of another currency, in euro cents at [rate] euros for one unit. */
fun toEuroCents(original: Long, rate: Double): Long = (original * rate).roundToLong()
