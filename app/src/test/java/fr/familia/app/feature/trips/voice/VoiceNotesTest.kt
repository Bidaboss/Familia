package fr.familia.app.feature.trips.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceNotesTest {
    @Test
    fun aRecordingCountsWholeSecondsBetweenOneAndTwoMinutes() {
        assertEquals(1, voiceSeconds(0))
        assertEquals(1, voiceSeconds(400))
        assertEquals(42, voiceSeconds(41_200))
        assertEquals(120, voiceSeconds(119_500))
        assertEquals(120, voiceSeconds(125_000))
    }

    @Test
    fun theLengthReadsAsMinutesAndSeconds() {
        assertEquals("0:07", voiceDuration(7))
        assertEquals("0:42", voiceDuration(42))
        assertEquals("2:00", voiceDuration(120))
    }
}
