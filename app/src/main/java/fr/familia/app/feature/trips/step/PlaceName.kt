package fr.familia.app.feature.trips.step

/** Small words that stay in lower case inside a place name: « Saint-Jean-de-Luz », « Aix-en-Provence ». */
private val PARTICLES = setOf("de", "du", "des", "la", "le", "les", "en", "et", "sur", "sous", "aux", "à", "lès", "lez")

/**
 * The name of a place as the family reads it (E-6): « La ciotat » typed on the phone becomes
 * « La Ciotat ». Only a word typed all in lower case changes, so « McDonald's » or « LA »
 * stay as typed; the first word always takes its capital, the small words inside stay small.
 */
fun placeName(typed: String): String {
    val words = typed.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return words.mapIndexed { index, word ->
        word.split('-').mapIndexed { part, piece -> capitalized(piece, first = index == 0 && part == 0) }.joinToString("-")
    }.joinToString(" ")
}

private fun capitalized(piece: String, first: Boolean): String {
    if (piece.isEmpty() || piece != piece.lowercase()) return piece
    // « l'île », « d'oléron »: the letter after the apostrophe takes the capital.
    val apostrophe = piece.indexOfFirst { it == '\'' || it == '’' }
    if (apostrophe in 1..2 && apostrophe < piece.length - 1) {
        val head = piece.substring(0, apostrophe + 1)
        val rest = piece.substring(apostrophe + 1)
        return (if (first) head.replaceFirstChar { it.titlecase() } else head) + rest.replaceFirstChar { it.titlecase() }
    }
    if (!first && piece in PARTICLES) return piece
    return piece.replaceFirstChar { it.titlecase() }
}
