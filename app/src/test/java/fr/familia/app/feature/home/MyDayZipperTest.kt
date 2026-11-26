package fr.familia.app.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** La fermeture éclair de « Ma journée » (brief §7, PR 8 du brief): its thresholds and its V. */
class MyDayZipperTest {

    @Test
    fun `an opening let go past 35 percent goes on, below it comes back`() {
        assertEquals(1f, Zipper.settle(from = 0f, at = 0.36f))
        assertEquals(0f, Zipper.settle(from = 0f, at = 0.34f))
        assertEquals(Zipper.SETTLE_MS, Zipper.settleMs(0f, 1f))
        assertEquals(Zipper.BACK_MS, Zipper.settleMs(0f, 0f))
    }

    @Test
    fun `a closing let go above 65 percent goes on, below it stays open`() {
        assertEquals(0f, Zipper.settle(from = 1f, at = 0.64f))
        assertEquals(1f, Zipper.settle(from = 1f, at = 0.66f))
        assertEquals(Zipper.SETTLE_MS, Zipper.settleMs(1f, 0f))
        assertEquals(Zipper.BACK_MS, Zipper.settleMs(1f, 1f))
    }

    @Test
    fun `the V is 0,28 of the depth on each side, then reaches the whole screen`() {
        assertEquals(0f, Zipper.halfWidth(0f, 800f, 400f), 0.01f)
        assertEquals(0.28f * 0.4f * 800f, Zipper.halfWidth(0.4f, 800f, 400f), 0.01f)
        assertEquals(0.28f * 800f + 400f, Zipper.halfWidth(1f, 800f, 400f), 0.01f)
    }

    @Test
    fun `the badge hides at 0 and stops at 9+`() {
        assertEquals(null, Zipper.badge(0))
        assertEquals("3", Zipper.badge(3))
        assertEquals("9+", Zipper.badge(12))
    }
}
