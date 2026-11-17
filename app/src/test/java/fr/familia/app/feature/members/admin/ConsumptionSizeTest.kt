package fr.familia.app.feature.members.admin

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsumptionSizeTest {

    @Test
    fun `in Mo below 1 Go, so a small figure never reads zero`() {
        assertEquals("12 Mo", sizeText(12L * 1024 * 1024, binary = true))
        assertEquals("86 Mo", sizeText(86_000_000, binary = false))
        assertEquals("999 Mo", sizeText(999_999_999, binary = false))
        assertEquals("0 Mo", sizeText(0, binary = false))
    }

    @Test
    fun `in Go with one decimal from 1 Go`() {
        assertEquals("1 Go", sizeText(1_000_000_000, binary = false))
        assertEquals("1,2 Go", sizeText(1_200_000_000, binary = false))
        assertEquals("4,5 Go", sizeText(4_500_000_000, binary = false))
        assertEquals("1,5 Go", sizeText(3L * 512 * 1024 * 1024, binary = true))
    }
}
