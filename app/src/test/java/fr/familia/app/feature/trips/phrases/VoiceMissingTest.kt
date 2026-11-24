package fr.familia.app.feature.trips.phrases

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Règle 44: a missing voice is said, once the voices were looked at. */
class VoiceMissingTest {
    @Test
    fun `nothing is said before the voices are known, then only for a missing one`() {
        assertFalse(voiceMissing(checked = false, available = emptySet(), language = PhraseLanguage.Spanish))
        assertTrue(voiceMissing(checked = true, available = setOf(PhraseLanguage.Italian), language = PhraseLanguage.Spanish))
        assertFalse(voiceMissing(checked = true, available = setOf(PhraseLanguage.Spanish), language = PhraseLanguage.Spanish))
    }
}
