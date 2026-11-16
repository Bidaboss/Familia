package fr.familia.app.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * The shared accounts of a trip (PRD rév. 193, récit 16 bis). They are a common brick: a book of
 * accounts belongs to a [container] (a trip today, an event of the planning later, PRODUIT-57)
 * and never to the trip itself. Amounts are whole euro cents, so a sum never loses a cent.
 */
enum class ExpenseContainerType(val firestoreValue: String) {
    Trip("trip"),
    ;

    companion object {
        fun fromFirestore(value: String?): ExpenseContainerType? = entries.firstOrNull { it.firestoreValue == value }
    }
}

/** What a book of accounts is attached to: the trip « tripId », later an event. */
data class ExpenseContainer(val type: ExpenseContainerType, val id: String) {
    /** The id of its book: one book per container, found without a query. */
    val bookId: String get() = "${type.firestoreValue}_$id"
}

/**
 * The accounts of one container. Each household taking part counts for a number of [shares]
 * (one per adult, a half per child by default, changed by [creatorId]). Once [closed], the
 * balance is frozen.
 */
data class ExpenseBook(
    val id: String,
    val container: ExpenseContainer,
    val creatorId: String,
    val shares: Map<String, Double>,
    val closed: Boolean = false,
)

enum class ExpenseCategory(val firestoreValue: String) {
    Lodging("lodging"),
    Transport("transport"),
    Groceries("groceries"),
    Restaurant("restaurant"),
    Activity("activity"),
    Other("other"),
    ;

    companion object {
        fun fromFirestore(value: String?): ExpenseCategory = entries.firstOrNull { it.firestoreValue == value } ?: Other
    }
}

/** How an expense is split between its households. */
enum class SplitMode(val firestoreValue: String) {
    /** In proportion to the shares of each household: the default. */
    Shares("shares"),

    /** The same amount for each household. */
    Equal("equal"),

    /** Amounts typed by hand, which add up to the expense. */
    Amounts("amounts"),
    ;

    companion object {
        fun fromFirestore(value: String?): SplitMode = entries.firstOrNull { it.firestoreValue == value } ?: Shares
    }
}

/**
 * One expense. [amountCents] is in euros; one paid in another [currency] keeps what was paid
 * ([originalCents]) and the [rate] typed by the member (euros for one unit of that currency).
 * [householdIds] share it; [amounts] holds the cents of each one in [SplitMode.Amounts] only.
 */
data class Expense(
    val id: String,
    val title: String,
    val amountCents: Long,
    val date: LocalDate,
    val payerId: String,
    val payerHouseholdId: String,
    val category: ExpenseCategory,
    val mode: SplitMode,
    val householdIds: List<String>,
    val amounts: Map<String, Long> = emptyMap(),
    val currency: String = EURO,
    val originalCents: Long = amountCents,
    val rate: Double = 1.0,
    val authorId: String,
)

/** « C'est remboursé »: [fromHouseholdId] paid back [amountCents] to [toHouseholdId], ticked by who received it. */
data class ExpenseSettlement(
    val id: String,
    val fromHouseholdId: String,
    val toHouseholdId: String,
    val amountCents: Long,
    val confirmedBy: String,
    val confirmedByName: String = "",
)

/** What a line of the history of an expense tells. */
enum class ExpenseHistoryAction(val firestoreValue: String) {
    Created("created"),
    Edited("edited"),
    Deleted("deleted"),
    ;

    companion object {
        fun fromFirestore(value: String?): ExpenseHistoryAction? = entries.firstOrNull { it.firestoreValue == value }
    }
}

/**
 * One line of the history of an expense, written by the server (récit 16 bis): who did what, when,
 * and which fields changed ([changed] holds the Firestore names: amountCents, date…).
 */
data class ExpenseHistoryEntry(
    val id: String,
    val action: ExpenseHistoryAction,
    val byName: String,
    val title: String,
    val amountCents: Long,
    val changed: List<String> = emptyList(),
    val at: Instant? = null,
)

/**
 * Where a household stands: what it paid, what it should have paid, and the difference. The
 * money it already paid back ([sentCents]) or received ([receivedCents]) counts in the difference.
 */
data class HouseholdBalance(
    val householdId: String,
    val paidCents: Long,
    val owedCents: Long,
    val sentCents: Long = 0,
    val receivedCents: Long = 0,
) {
    /** Positive: the others still owe it money. Negative: it still owes money. */
    val netCents: Long get() = paidCents - owedCents + sentCents - receivedCents
}

/** One transfer of « Qui rembourse qui ». */
data class Transfer(val fromHouseholdId: String, val toHouseholdId: String, val amountCents: Long)

const val EURO = "EUR"
