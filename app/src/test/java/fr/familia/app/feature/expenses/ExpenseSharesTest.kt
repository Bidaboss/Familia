package fr.familia.app.feature.expenses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseSharesTest {

    @Test
    fun `a share is typed by halves, from 0,5 to 20, with a comma or a dot`() {
        assertEquals(1.5, parseShare("1,5")!!, 0.0)
        assertEquals(2.0, parseShare(" 2 ")!!, 0.0)
        assertEquals(0.5, parseShare("0.5")!!, 0.0)
        assertEquals(20.0, parseShare("20")!!, 0.0)
        for (wrong in listOf("", "0", "0,3", "1,25", "20,5", "21", "-1", "abc", "1,")) assertNull(wrong, parseShare(wrong))
    }

    @Test
    fun `shares are written the French way, and every household needs one`() {
        assertEquals("1,5", shareText(1.5))
        assertEquals("2", shareText(2.0))
        assertEquals(mapOf("h1" to 1.0, "h2" to 0.5), parseShares(mapOf("h1" to "1", "h2" to "0,5", "h9" to "3"), listOf("h1", "h2")))
        assertNull(parseShares(mapOf("h1" to "1"), listOf("h1", "h2")))
    }

    @Test
    fun `a person weighs from 0 to 10 shares, and a household at least half a share`() {
        assertEquals(0.0, parsePersonShare("0")!!, 0.0)
        assertEquals(10.0, parsePersonShare("10")!!, 0.0)
        for (wrong in listOf("", "0,3", "10,5", "-1")) assertNull(wrong, parsePersonShare(wrong))
        val people = listOf(
            fr.familia.app.feature.events.TestFamily.family.members.first { it.id == "adult1" },
            fr.familia.app.feature.events.TestFamily.family.members.first { it.id == "kid1" },
        )
        val h1 = people.first().householdId
        val shares = parsePersonShares(mapOf("adult1" to "1", "kid1" to "0,5"), people, mapOf("h9" to 2.0))!!
        assertEquals(mapOf("adult1" to 1.0, "kid1" to 0.5), shares.memberShares)
        assertEquals(mapOf("h9" to 2.0, h1 to 1.5), shares.households)
        assertNull(parsePersonShares(mapOf("adult1" to "0", "kid1" to "0"), people, emptyMap()))
        assertNull(parsePersonShares(mapOf("adult1" to "1"), people, emptyMap()))
    }
}
