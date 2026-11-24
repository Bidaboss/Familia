package fr.familia.app.feature.trips.phrases

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.TripStep
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PhraseBookTest {
    @Test
    fun everyLanguageHasTenCompletePhrases() {
        PhraseLanguage.entries.forEach { language ->
            val phrases = PhraseBook.phrases(language)
            assertEquals(language.name, PhraseBook.COUNT, phrases.size)
            phrases.forEach { phrase ->
                assertTrue(language.name + " " + phrase, phrase.french.isNotBlank() && phrase.local.isNotBlank() && phrase.pronunciation.isNotBlank())
            }
            // The same ten sentences in every language, in the same order.
            assertEquals(PhraseBook.phrases(PhraseLanguage.Spanish).map { it.french }, phrases.map { it.french })
        }
    }

    @Test
    fun franceHasNoPhrases() {
        assertNull(PhraseBook.languageOf("FR"))
        assertNull(PhraseBook.languageOf(null))
        assertNull(PhraseBook.languageOf("JP"))
    }

    @Test
    fun countriesMapToTheirLanguage() {
        assertEquals(PhraseLanguage.Spanish, PhraseBook.languageOf("es"))
        assertEquals(PhraseLanguage.Portuguese, PhraseBook.languageOf("BR"))
        assertEquals(PhraseLanguage.German, PhraseBook.languageOf("AT"))
    }

    @Test
    fun abroadKeepsOrderOnceEachAndDropsFranceAndUnknown() {
        assertEquals(listOf("ES", "PT"), PhraseBook.abroad(listOf("fr", "ES", "es", "JP", "PT", "FR")))
    }

    @Test
    fun stepsAbroadAreFoundFromTheirPositionOnlyOnce() = runBlocking {
        fun step(id: String, lat: Double?, lng: Double?) = TripStep(
            id = id, type = StepType.Activity, place = id, departDate = LocalDate.of(2027, 7, 1),
            departTime = null, arriveDate = null, arriveTime = null, note = null, lat = lat, lng = lng,
        )
        var asked = 0
        val found = TripCountries.abroad(listOf(step("a", 41.38, 2.17), step("b", 41.381, 2.171), step("c", null, null), step("d", 45.76, 4.83))) { lat, _ ->
            asked++
            if (lat > 44) "FR" else "ES"
        }
        assertEquals(listOf("ES"), found)
        // The second step is on the same tenth of a degree as the first, and the third has no point.
        assertEquals(2, asked)
    }

    @Test
    fun `the page of a step abroad shows the first phrases of its language, nothing in France`() {
        val spain = stepPhrases("es")!!
        assertEquals("ES", spain.country)
        assertEquals(PhraseLanguage.Spanish, spain.language)
        assertEquals(PhraseBook.phrases(PhraseLanguage.Spanish).take(STEP_PHRASES_SHOWN), spain.phrases)
        assertEquals(PhraseLanguage.Portuguese, stepPhrases("BR")?.language)
        assertNull(stepPhrases("FR"))
        assertNull(stepPhrases(null))
        // A country abroad without a table yet (Japan) shows nothing rather than a wrong language.
        assertNull(stepPhrases("JP"))
    }
}
