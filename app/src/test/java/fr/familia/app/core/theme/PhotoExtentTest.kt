package fr.familia.app.core.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoExtentTest {

    @Test
    fun `the whole screen is the default, and each choice is read back from its key`() {
        assertEquals(PhotoExtent.Full, PhotoExtent.fromKey(null))
        assertEquals(PhotoExtent.Full, PhotoExtent.fromKey("unknown"))
        PhotoExtent.entries.forEach { assertEquals(it, PhotoExtent.fromKey(it.key)) }
    }
}
