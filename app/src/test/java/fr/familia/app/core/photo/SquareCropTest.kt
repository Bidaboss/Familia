package fr.familia.app.core.photo

import org.junit.Assert.assertEquals
import org.junit.Test

class SquareCropTest {

    @Test
    fun `a landscape picture is centered on its middle square`() {
        assertEquals(PixelSquare(left = 100, top = 0, side = 200), SquareCrop(400, 200).sourceSquare())
    }

    @Test
    fun `a portrait picture is centered on its middle square`() {
        assertEquals(PixelSquare(left = 0, top = 150, side = 300), SquareCrop(300, 600).sourceSquare())
    }

    @Test
    fun `dragging never shows outside the picture`() {
        val crop = SquareCrop(400, 200).transform(panX = 10f, panY = 10f)
        assertEquals(PixelSquare(left = 0, top = 0, side = 200), crop.sourceSquare())
        assertEquals(0f, crop.offsetY, 0f)
        val other = SquareCrop(400, 200).transform(panX = -10f, panY = 0f)
        assertEquals(PixelSquare(left = 200, top = 0, side = 200), other.sourceSquare())
    }

    @Test
    fun `zooming keeps the center and shrinks the square`() {
        val crop = SquareCrop(400, 400).transform(0f, 0f, zoomChange = 2f)
        assertEquals(PixelSquare(left = 100, top = 100, side = 200), crop.sourceSquare())
    }

    @Test
    fun `zoom stays between 1 and the maximum`() {
        assertEquals(1f, SquareCrop(400, 400).transform(0f, 0f, 0.1f).zoom, 0f)
        assertEquals(SquareCrop.MAX_ZOOM, SquareCrop(400, 400).transform(0f, 0f, 100f).zoom, 0f)
    }

    @Test
    fun `zooming out pulls a moved picture back over the frame`() {
        val moved = SquareCrop(400, 400).transform(0f, 0f, 2f).transform(panX = 0.5f, panY = 0f)
        assertEquals(0.5f, moved.offsetX, 1e-4f)
        val back = moved.transform(0f, 0f, 0.5f)
        assertEquals(0f, back.offsetX, 0f)
    }

    @Test
    fun `sample size keeps at least the wanted side`() {
        assertEquals(1, PhotoProcessing.sampleSize(3000, 2000, 2048))
        assertEquals(2, PhotoProcessing.sampleSize(4096, 3072, 2048))
        assertEquals(4, PhotoProcessing.sampleSize(9000, 6000, 2048))
    }
}
