package fr.familia.app.core.album

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The sends of the app run on the main thread, with the screen: never two writers of the list at once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumSendsTest {

    @Test
    fun `the sends of the app run on the main thread`() {
        var looper: Looper? = null
        AlbumSends.shared.launch { looper = Looper.myLooper() }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("$looper", looper == Looper.getMainLooper())
    }

    @Test
    fun `the bar starts again at 0 once every photo is done`() {
        val sends = AlbumSends.shared
        sends.start("trips/test-bar", listOf(1, 2))
        sends.finish("trips/test-bar", 1)
        assertEquals(AlbumSends.Progress(total = 2, done = 1, slots = setOf(2)), sends.current("trips/test-bar"))
        sends.finish("trips/test-bar", 2)
        assertEquals(AlbumSends.Progress(), sends.current("trips/test-bar"))
    }
}
