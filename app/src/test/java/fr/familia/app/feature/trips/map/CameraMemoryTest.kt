package fr.familia.app.feature.trips.map

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraMemoryTest {
    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any) = true
    }

    @Test
    fun `nothing is kept before the camera first rests`() {
        assertNull(CameraMemory().camera)
        assertNull(with(CameraMemory.Saver) { scope.save(CameraMemory()) })
    }

    @Test
    fun `the last rest comes back after the screen is saved and restored`() {
        val memory = CameraMemory()
        memory.keep(2.35, 48.85, 9.0, 0.0, 0.0)
        memory.keep(-1.55, 47.21, 10.5, 12.0, 60.0)
        val saved = with(CameraMemory.Saver) { scope.save(memory) }!!
        val back = CameraMemory.Saver.restore(saved)!!
        assertArrayEquals(doubleArrayOf(-1.55, 47.21, 10.5, 12.0, 60.0), back.camera, 0.0)
    }

    @Test
    fun `a saved state of the wrong size is forgotten`() {
        assertNull(CameraMemory.Saver.restore(doubleArrayOf(1.0, 2.0))!!.camera)
    }
}
