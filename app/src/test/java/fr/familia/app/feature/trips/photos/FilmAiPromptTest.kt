package fr.familia.app.feature.trips.photos

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.model.TripWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** « Faire un vrai film avec une IA » (récit 108, règle 33). Fictional names only. */
class FilmAiPromptTest {
    private fun day(d: Int) = LocalDate.of(2027, 7, d)
    private val trip = Trip("t", "Corse", day(10), day(20), emptyList(), emptyList(), "", "")
    private fun step(id: String, place: String, d: Int) = TripStep(id, StepType.Lodging, place, day(d), null, null, null, null)
    private var second = 0L
    private fun photo(stepId: String?, slot: Int) = AlbumPhoto("trips/t", "lou", "Lou", slot, Instant.ofEpochSecond(++second), stepId = stepId)
    private fun word(stepId: String, text: String) = TripWord(stepId, text, emptyList(), "lou", "Lou", emptySet(), null)

    @Test
    fun `the prompt follows the criteria and the order of the trip, with the words`() {
        val steps = listOf(step("b", "Bonifacio, Corse", 15), step("a", "Ajaccio", 10))
        val photos = listOf(photo("b", 1), photo("a", 2), photo("a", 3), photo(null, 4))
        val subject = tripAiSubject(trip, steps, photos, mapOf("a" to word("a", "Bien arrivés,\nla mer est bonne")))
        val prompt = filmAiPrompt(subject, FilmAiOptions(FilmAmbiance.RoadTrip, FilmAiLength.S30, music = false, voiceOver = true))
        assertEquals(
            listOf(
                "Tu es monteur vidéo. Crée un film vertical (1080 × 1920, format story) de 30 s pour le voyage « Corse ».",
                "Ambiance : Road trip — énergique et libre : cuts rythmés, plans de route, indie-pop ou rock léger, texte de lieux en gros.",
                "Musique : non. Voix off : oui, lue à partir des commentaires.",
                "Affiche les commentaires en bas des photos.",
                "Ordre des séquences (une par destination) :",
                "1. Ajaccio : 2 photos — commentaire : « Bien arrivés, la mer est bonne »",
                "2. Bonifacio : 1 photo",
                "Garde l'ordre chronologique, des transitions douces, et termine par le titre du voyage.",
            ),
            prompt.lines(),
        )
        // The photos of no step stay out: the film goes by destination.
        assertEquals(3, subject.photos.size)
    }

    @Test
    fun `a step makes the prompt of one destination`() {
        val s = step("a", "Ajaccio", 10)
        val subject = stepAiSubject(s, listOf(photo("a", 1), photo("b", 2)), null)
        val prompt = filmAiPrompt(subject, FilmAiOptions())
        assertTrue(prompt.startsWith("Tu es monteur vidéo. Crée un film vertical (1080 × 1920, format story) de 1 min pour la destination « Ajaccio »."))
        assertTrue(prompt.contains("Ambiance : Souvenir"))
        assertTrue(prompt.contains("Musique : oui, adaptée au style. Voix off : non."))
        assertTrue(prompt.endsWith("termine par le titre de la destination."))
    }

    @Test
    fun `at most 20 photos are exported, at least one per destination`() {
        val steps = (1..3).map { step("s$it", "Ville $it", 10 + it) }
        val photos = steps.flatMap { s -> (1..20).map { photo(s.id, it) } } + listOf(photo("lone", 1))
        val lone = step("lone", "Calvi", 19)
        val subject = tripAiSubject(trip, steps + lone, photos, emptyMap())
        val export = filmAiExportPhotos(subject)
        assertTrue(export.size <= FILM_AI_MAX_PHOTOS)
        (steps + lone).forEach { s -> assertTrue(export.any { it.stepId == s.id }) }
    }

    @Test
    fun `eight ambiances, as in the prototype`() {
        assertEquals(
            listOf("Souvenir", "Road trip", "Cinéma", "Carnet de voyage", "Drôle", "Documentaire", "Fête de famille", "Zen & nature"),
            FilmAmbiance.entries.map { it.label },
        )
    }
}
