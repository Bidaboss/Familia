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
}
