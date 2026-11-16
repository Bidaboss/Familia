package fr.familia.app.feature.expenses

import fr.familia.app.core.model.Expense
import fr.familia.app.core.model.ExpenseCategory
import fr.familia.app.core.model.ExpenseContainer
import fr.familia.app.core.model.ExpenseContainerType
import fr.familia.app.core.model.ExpenseSettlement
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.SplitMode
import fr.familia.app.core.model.Transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Fictional trip: households h1 (2 adults, 1 child), h2 (1 adult) and h3 (2 adults). */
class ExpenseMathTest {

    private fun member(id: String, household: String, profile: MemberProfile = MemberProfile.Adult) =
        Member(id, id, null, null, household, profile, isAdmin = false, parentIds = emptyList(), uid = null)

    private val shares = mapOf("h1" to 2.5, "h2" to 1.0, "h3" to 2.0)

    private fun expense(
        id: String,
        cents: Long,
        payer: String,
        households: List<String> = listOf("h1", "h2", "h3"),
        mode: SplitMode = SplitMode.Shares,
        amounts: Map<String, Long> = emptyMap(),
    ) = Expense(
        id = id, title = "Dépense $id", amountCents = cents, date = LocalDate.of(2027, 7, 12),
        payerId = "$payer-adult", payerHouseholdId = payer, category = ExpenseCategory.Groceries,
        mode = mode, householdIds = households, amounts = amounts, authorId = "$payer-adult",
    )

    @Test
    fun `one share per person, children too, added up by household`() {
        val participants = listOf(
            member("a1", "h1"), member("a2", "h1"), member("k1", "h1", MemberProfile.ChildWithApp),
            member("a3", "h2"), member("k2", "h2", MemberProfile.ChildWithoutApp),
        )
        assertEquals(mapOf("h1" to 3.0, "h2" to 2.0), defaultShares(participants))
        assertEquals(mapOf("h1" to 2.5, "h2" to 1.0), householdShares(participants, mapOf("k1" to 0.5, "k2" to 0.0)))
    }

    @Test
    fun `a split by shares adds up to the cent`() {
        // 100,00 € for 5,5 shares: 45,45 + 18,18 + 36,36 = 99,99, and the cent left goes to the largest remainder.
        val split = splitOf(expense("e", 10_000, "h1"), shares)
        assertEquals(10_000L, split.values.sum())
        assertEquals(mapOf("h1" to 4_546L, "h2" to 1_818L, "h3" to 3_636L), split)
    }

    @Test
    fun `equal, typed amounts, and some households only`() {
        assertEquals(mapOf("h1" to 3_334L, "h2" to 3_333L, "h3" to 3_333L), splitOf(expense("e", 10_000, "h1", mode = SplitMode.Equal), shares))
        val typed = expense("t", 5_000, "h2", mode = SplitMode.Amounts, amounts = mapOf("h1" to 1_000L, "h2" to 4_000L), households = listOf("h1", "h2"))
        assertEquals(mapOf("h1" to 1_000L, "h2" to 4_000L), splitOf(typed, shares))
        assertEquals(mapOf("h2" to 1_000L, "h3" to 2_000L), splitOf(expense("s", 3_000, "h3", households = listOf("h2", "h3")), shares))
    }

    @Test
    fun `the balances add up to zero and the transfers settle them`() {
        val expenses = listOf(expense("a", 55_000, "h1"), expense("b", 11_000, "h3"), expense("c", 3_000, "h2", mode = SplitMode.Equal))
        val balances = balancesOf(listOf("h1", "h2", "h3"), expenses, emptyList(), shares)
        assertEquals(0L, balances.sumOf { it.netCents })
        val transfers = transfersOf(balances)
        assertTrue(transfers.size <= 2)
        val after = balances.associate { it.householdId to it.netCents }.toMutableMap()
        transfers.forEach { after.merge(it.fromHouseholdId, it.amountCents, { a, b -> a + b }); after.merge(it.toHouseholdId, -it.amountCents, { a, b -> a + b }) }
        assertTrue(after.values.all { it == 0L })
    }

    @Test
    fun `a reimbursement ticked counts in the balance`() {
        // h1 paid 60 € for h1 and h2 alike: h2 owes it 30 €, then pays it back.
        val expenses = listOf(expense("a", 6_000, "h1", households = listOf("h1", "h2"), mode = SplitMode.Equal))
        val before = balancesOf(listOf("h1", "h2"), expenses, emptyList(), shares)
        assertEquals(listOf(Transfer("h2", "h1", 3_000)), transfersOf(before))
        val settled = balancesOf(listOf("h1", "h2"), expenses, listOf(ExpenseSettlement("s", "h2", "h1", 3_000, "h1-adult")), shares)
        assertTrue(settled.all { it.netCents == 0L })
        assertTrue(transfersOf(settled).isEmpty())
    }

    @Test
    fun `another currency at the rate typed by hand, and one book per container`() {
        assertEquals(9_259L, toEuroCents(10_000, 0.92593))
        assertEquals("trip_t1", ExpenseContainer(ExpenseContainerType.Trip, "t1").bookId)
    }
}
