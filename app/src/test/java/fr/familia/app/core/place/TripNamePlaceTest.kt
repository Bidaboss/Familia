package fr.familia.app.core.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Récit 124: the photo of a trip from its name, only for a sure place. */
class TripNamePlaceTest {
    @Test
    fun theWordsAroundThePlaceInTheNameOfATripAreLeftOut() {
        assertEquals(listOf("Les Cyclades", "Cyclades"), tripNameCandidates("Les Cyclades"))
        assertEquals(listOf("Cyclades"), tripNameCandidates("Voyage aux Cyclades 2026"))
        assertEquals(listOf("Bretagne"), tripNameCandidates("Vacances en Bretagne été 2027"))
        assertEquals(listOf("Rome"), tripNameCandidates("Week-end à Rome"))
        assertEquals(listOf("Écosse"), tripNameCandidates("Road trip en Écosse (août)"))
        assertEquals(listOf("Lisbonne"), tripNameCandidates("Séjour à Lisbonne 2026-27"))
        assertEquals(listOf("Japon"), tripNameCandidates("Japon 🇯🇵"))
    }

    @Test
    fun aPlaceThatStartsLikeAWordIsKeptWhole() {
        assertEquals(listOf("Lesbos"), tripNameCandidates("Lesbos"))
        assertEquals(listOf("Tournai"), tripNameCandidates("Tournai"))
        assertEquals(listOf("L'Île de Ré", "Île de Ré"), tripNameCandidates("L'Île de Ré"))
    }

    @Test
    fun aNameWithoutAPlaceGivesNothingToLookFor() {
        assertEquals(emptyList<String>(), tripNameCandidates("Été"))
        assertEquals(emptyList<String>(), tripNameCandidates("Circuit 2026"))
        assertEquals(emptyList<String>(), tripNameCandidates("  "))
    }

    @Test
    fun onlyThePageOfTheNameItselfIsSure() {
        assertTrue(isSureNameMatch("Les Cyclades", "Cyclades"))
        assertTrue(isSureNameMatch("Ecosse", "Écosse"))
        assertTrue(isSureNameMatch("Ile de Ré", "Île de Ré"))
        assertFalse(isSureNameMatch("Paris", "Paris Saint-Germain Football Club"))
        assertFalse(isSureNameMatch("Mamie", "Grand-mère"))
        assertFalse(isSureNameMatch("Lesbos", "Bos"))
    }

    @Test
    fun aSummaryGivesAPlaceOnlyForAPageWithAPosition() {
        val cyclades = """{"type":"standard","title":"Cyclades","titles":{"normalized":"Cyclades"},"coordinates":{"lat":37.0,"lon":25.0}}"""
        assertEquals(NamedPlace("Cyclades", 37.0, 25.0), parseSummaryPlace(cyclades))
        // A word, a person: no position.
        assertNull(parseSummaryPlace("""{"type":"standard","title":"Grand-mère"}"""))
        assertNull(parseSummaryPlace("""{"type":"disambiguation","title":"Ré","coordinates":{"lat":46.2,"lon":-1.4}}"""))
        assertNull(parseSummaryPlace("""{"type":"standard","title":"Nulle part","coordinates":{"lat":120.0,"lon":0.0}}"""))
        assertNull(parseSummaryPlace("pas du json"))
    }
}
