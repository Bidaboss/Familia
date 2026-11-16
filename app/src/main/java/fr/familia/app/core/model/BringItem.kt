package fr.familia.app.core.model

/**
 * One line of « Qui apporte quoi » (PRD, récit 4 bis): something to bring to an event, and who took
 * it ([takerId] null while it is free). Its label never changes once added.
 */
data class BringItem(
    val id: String,
    val label: String,
    val authorId: String,
    val takerId: String?,
    val takerName: String?,
    /** A recipe of the book joined when the line was added (« Joindre une recette »), or null. */
    val recipeId: String? = null,
) {
    companion object {
        const val MAX_LABEL = 60
    }
}
