package fr.familia.app.feature.trips.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class SheetGestureTest {
    @Test
    fun aShortDragOrAFlickGoesToTheNextHeight() {
        // E-59: 32 dp, or 600 dp/s, are enough; up is negative.
        assertEquals(2, sheetLevelAfterDrag(1, -32f, 0f))
        assertEquals(0, sheetLevelAfterDrag(1, 32f, 0f))
        assertEquals(1, sheetLevelAfterDrag(0, -10f, -700f))
        assertEquals(1, sheetLevelAfterDrag(2, 5f, 900f))
        // Too small and too slow: it stays where it is.
        assertEquals(1, sheetLevelAfterDrag(1, -20f, -100f))
        // Never past the ends.
        assertEquals(2, sheetLevelAfterDrag(2, -80f, 0f))
        assertEquals(0, sheetLevelAfterDrag(0, 80f, 0f))
    }

    @Test
    fun aTouchOnTheHandleGoesToTheNextHeight() {
        // E-59: low → middle → whole screen, whole screen → middle.
        assertEquals(1, sheetLevelAfterTap(0))
        assertEquals(2, sheetLevelAfterTap(1))
        assertEquals(1, sheetLevelAfterTap(2))
    }

    @Test
    fun theHandleTakesTheWholeWidthOver72Dp() = assertEquals(72f, SHEET_HANDLE_HEIGHT.value, 0f)
}
