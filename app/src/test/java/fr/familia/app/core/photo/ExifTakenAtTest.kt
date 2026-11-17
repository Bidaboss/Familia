package fr.familia.app.core.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** The day a photo was taken, read from its EXIF (récit 36, « Il y a N ans »). */
class ExifTakenAtTest {

    private val now = Instant.parse("2026-11-17T12:00:00Z")

    @Test
    fun `the offset the phone wrote is used, else the time of France`() {
        assertEquals(Instant.parse("2025-08-04T16:30:12Z"), exifTakenAt("2025:08:04 18:30:12", "+02:00", now))
        assertEquals(Instant.parse("2025-08-04T16:30:12Z"), exifTakenAt("2025:08:04 18:30:12", null, now))
        assertEquals(Instant.parse("2025-01-04T17:30:12Z"), exifTakenAt("2025:01:04 18:30:12", "bad", now))
    }

    @Test
    fun `no date, a damaged one, a clock never set or a date to come give nothing`() {
        assertNull(exifTakenAt(null, null, now))
        assertNull(exifTakenAt("    :  :     :  :  ", null, now))
        assertNull(exifTakenAt("2000:01:01 00:00:00", null, now))
        assertNull(exifTakenAt("2027:01:01 00:00:00", null, now))
    }
}
