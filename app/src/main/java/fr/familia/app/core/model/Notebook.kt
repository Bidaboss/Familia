package fr.familia.app.core.model

/** Where a notebook entry comes from (PRD, récit 9 bis). */
enum class NotebookSource(val firestoreValue: String) {
    /** A private idea: it reserves nothing. */
    Idea("idea"),

    /** « Je le prends » on a list, taken up by the server. */
    Claim("claim"),

    /** A part of a joint gift, or a joint gift the member is responsible for. */
    Share("share"),
    ;

    companion object {
        fun fromFirestore(value: String?): NotebookSource? = entries.firstOrNull { it.firestoreValue == value }
    }
}

/** Where a gift stands: Idée, Acheté, Emballé, Offert. */
enum class GiftStatus(val firestoreValue: String) {
    Idea("idea"),
    Bought("bought"),
    Wrapped("wrapped"),
    Given("given"),
    ;

    /** Bought, wrapped or given: counted in « x sur y achetés ». */
    val isBought: Boolean get() = this != Idea

    companion object {
        fun fromFirestore(value: String?): GiftStatus? = entries.firstOrNull { it.firestoreValue == value }
    }
}

/** An occasion of the notebook: Noël 2026, or no occasion yet when both are null. */
data class NotebookOccasion(val occasion: GiftOccasion?, val year: Int?)

/**
 * A gift the member plans to give, in their private notebook. [personId] is null for someone
 * outside the family, known by [personName] only. [listId] and [itemId] are set for a reservation
 * or a part taken up from a list.
 */
data class NotebookEntry(
    val id: String,
    val source: NotebookSource,
    val personId: String?,
    val personName: String,
    val occasion: NotebookOccasion,
    val title: String,
    val url: String?,
    val price: Double?,
    val note: String?,
    val status: GiftStatus,
    val listId: String? = null,
    val itemId: String? = null,
    /** Set by the copy of « Liste unique » (récit 9 ter): the entry of the copied gift. */
    val movedTo: String? = null,
    /** The picture of its link's page (correctif E12), as on a wish. */
    val imageUrl: String? = null,
    /** « Où trouver le cadeau ? » (correctif E11): a shop, a town or a site, 120 characters at most. */
    val whereToFind: String? = null,
    /** Its own photo (correctif E11), private to its author; base path of [fr.familia.app.core.photo.SinglePhoto]. */
    val photo: String? = null,
)

/**
 * What the member means to spend for one person at one occasion, or for the whole occasion when
 * [total] (« Mon budget », récit 57: then no person, and an occasion with its year).
 */
data class NotebookBudget(
    val personId: String?,
    val personName: String,
    val occasion: NotebookOccasion,
    val amount: Double,
    val total: Boolean = false,
)

/** The id of the budget of a whole occasion: one per occasion and year. */
fun notebookTotalBudgetId(occasion: NotebookOccasion): String =
    "total_${occasion.occasion?.firestoreValue ?: "none"}_${occasion.year ?: "none"}"

/**
 * What counts in « Mon budget » (récit 57): my reservations with a price and my parts of joint
 * gifts, whatever their status, and what I bought outside the app (a private idea marked bought).
 * An idea not bought yet spends nothing.
 */
fun NotebookEntry.committed(): Double = when (source) {
    NotebookSource.Idea -> if (status.isBought) price ?: 0.0 else 0.0
    NotebookSource.Claim, NotebookSource.Share -> price ?: 0.0
}

/**
 * The id of a budget: one per person and occasion. A person outside the family is known by their
 * first name, whatever its case. Only letters, digits, « _ » and « - »: a Firestore id has no « / ».
 */
fun notebookBudgetId(personId: String?, personName: String, occasion: NotebookOccasion): String {
    val person = personId ?: "name-" + personName.trim().lowercase()
    val raw = "${person}_${occasion.occasion?.firestoreValue ?: "none"}_${occasion.year ?: "none"}"
    return raw.map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '-' }.joinToString("").take(200)
}
