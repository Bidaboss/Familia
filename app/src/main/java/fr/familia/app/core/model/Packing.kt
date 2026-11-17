package fr.familia.app.core.model

/**
 * « Ma valise » (PRD, récit 62, module packingList): the private list of one participant for one
 * trip, ticked by them or by one of their responsible adults. See docs/data-model.md.
 */
data class PackingLine(val label: String, val done: Boolean = false) {
    companion object {
        const val MAX_LABEL = 60

        /** As many lines as the rules allow in one bag or one list to start from. */
        const val MAX_LINES = 80
    }
}

/** The lines of a bag; empty while it was never opened. */
data class PackingBag(val lines: List<PackingLine> = emptyList()) {
    val left: Int get() = lines.count { !it.done }
}
