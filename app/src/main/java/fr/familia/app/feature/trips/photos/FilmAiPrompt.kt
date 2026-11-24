package fr.familia.app.feature.trips.photos

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.albumPhotoOrder
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.model.TripWord
import fr.familia.app.feature.trips.detail.placeName
import fr.familia.app.feature.trips.detail.tripTimeline

/**
 * « Faire un vrai film avec une IA » (récit 108, règle 33): a prompt to copy into any AI, made from
 * the criteria chosen (ambiance, length, music, voice-over), with the photos and the words of the
 * steps to send along. Nothing goes to an AI from the app itself: the person pastes or shares.
 */
enum class FilmAmbiance(val label: String, val look: String) {
    Souvenir("Souvenir", "nostalgique et chaleureux : grain doux, tons dorés, fondus lents, piano ou guitare douce"),
    RoadTrip("Road trip", "énergique et libre : cuts rythmés, plans de route, indie-pop ou rock léger, texte de lieux en gros"),
    Cinema("Cinéma", "film de cinéma : bandes noires 2,35:1, étalonnage contrasté, ralentis, musique orchestrale, titres sobres"),
    Carnet("Carnet de voyage", "carnet manuscrit : photos façon polaroïd, cartes animées du trajet, annotations à la main, folk"),
    Drole("Drôle", "comique : montage rapide, zooms, bruitages, sous-titres décalés, musique malicieuse"),
    Documentaire("Documentaire", "documentaire : voix off posée, chiffres et lieux à l'écran, plans longs, musique ambiante"),
    Fete("Fête de famille", "joyeux et festif : couleurs vives, stickers, visages en gros plan, hits à chanter"),
    Zen("Zen & nature", "contemplatif : paysages, très lents fondus, sons de nature, peu de texte"),
}

enum class FilmAiLength(val label: String) { S30("30 s"), M1("1 min"), M2("2 min") }

/** The criteria of the prompt; the defaults are those of the prototype. */
data class FilmAiOptions(
    val ambiance: FilmAmbiance = FilmAmbiance.Souvenir,
    val length: FilmAiLength = FilmAiLength.M1,
    val music: Boolean = true,
    val voiceOver: Boolean = false,
)

/** One sequence of the film: a destination, its photos and its word. */
data class FilmAiSequence(val place: String, val photos: List<AlbumPhoto>, val comment: String?)

/** What the film is of: a step (« la destination « Ajaccio » ») or the whole trip. */
data class FilmAiSubject(val isTrip: Boolean, val name: String, val sequences: List<FilmAiSequence>) {
    val photos: List<AlbumPhoto> get() = sequences.flatMap { it.photos }
}

/** The sequences of the trip, in the order of « Le trajet », each with its photos oldest first and its word. */
fun tripAiSubject(trip: Trip, steps: List<TripStep>, photos: List<AlbumPhoto>, words: Map<String, TripWord>): FilmAiSubject {
    val ordered = photos.sortedWith(albumPhotoOrder).reversed()
    val sequences = tripTimeline(trip, steps).rows.mapNotNull { row ->
        val own = ordered.filter { it.stepId == row.step.id }
        if (own.isEmpty()) null else FilmAiSequence(placeName(row.step), own, words[row.step.id]?.text)
    }
    return FilmAiSubject(isTrip = true, name = trip.title, sequences = sequences)
}

/** The one sequence of a step. */
fun stepAiSubject(step: TripStep, photos: List<AlbumPhoto>, word: TripWord?): FilmAiSubject {
    val own = photos.filter { it.stepId == step.id }.sortedWith(albumPhotoOrder).reversed()
    return FilmAiSubject(isTrip = false, name = placeName(step), sequences = listOf(FilmAiSequence(placeName(step), own, word?.text)))
}

/** A word on one line, cut if long, so the prompt stays readable. */
private fun oneLine(text: String, max: Int = 160): String {
    val flat = text.replace(Regex("\\s+"), " ").trim()
    return if (flat.length <= max) flat else flat.take(max - 1).trimEnd() + "…"
}

/** The prompt, as the prototype writes it (v75). No first name of the family goes in it. */
fun filmAiPrompt(subject: FilmAiSubject, options: FilmAiOptions): String {
    val what = if (subject.isTrip) "le voyage « ${subject.name} »" else "la destination « ${subject.name} »"
    val lines = subject.sequences.mapIndexed { i, s ->
        val count = if (s.photos.size == 1) "1 photo" else "${s.photos.size} photos"
        val comment = s.comment?.takeIf { it.isNotBlank() }?.let { " — commentaire : « ${oneLine(it)} »" }.orEmpty()
        "${i + 1}. ${s.place} : $count$comment"
    }
    return buildString {
        appendLine("Tu es monteur vidéo. Crée un film vertical (1080 × 1920, format story) de ${options.length.label} pour $what.")
        appendLine("Ambiance : ${options.ambiance.label} — ${options.ambiance.look}.")
        appendLine("Musique : ${if (options.music) "oui, adaptée au style" else "non"}. Voix off : ${if (options.voiceOver) "oui, lue à partir des commentaires" else "non"}.")
        appendLine("Affiche les commentaires en bas des photos.")
        appendLine("Ordre des séquences (une par destination) :")
        lines.forEach { appendLine(it) }
        append("Garde l'ordre chronologique, des transitions douces, et termine par le titre ")
        append(if (subject.isTrip) "du voyage." else "de la destination.")
    }
}

/** An AI app takes a few photos at once: at most this many are exported, spread over the sequences. */
const val FILM_AI_MAX_PHOTOS = 20

/** The photos to export, in the order of the film, [FILM_AI_MAX_PHOTOS] at most, at least one per sequence. */
fun filmAiExportPhotos(subject: FilmAiSubject): List<AlbumPhoto> {
    val all = subject.photos
    if (all.size <= FILM_AI_MAX_PHOTOS) return all
    val room = FILM_AI_MAX_PHOTOS.coerceAtLeast(subject.sequences.size)
    return subject.sequences.flatMap { s -> thin(s.photos, (s.photos.size.toDouble() * room / all.size).toInt().coerceIn(1, s.photos.size)) }
}
