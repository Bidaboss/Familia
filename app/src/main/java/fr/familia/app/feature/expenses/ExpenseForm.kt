package fr.familia.app.feature.expenses

import fr.familia.app.core.model.EURO
import fr.familia.app.core.model.Expense
import fr.familia.app.core.model.ExpenseCategory
import fr.familia.app.core.model.SplitMode
import java.time.LocalDate

/** The longest title of an expense, as the rules allow. */
const val EXPENSE_TITLE_MAX = 80

/** The largest expense, as the rules allow: 100 000 €. */
const val EXPENSE_MAX_CENTS = 10_000_000L

/**
 * What the member types in the expense form (récit 16 bis). Amounts stay text until saved, so
 * « 12,5 » or « 12.50 » both work. [amounts] holds the text typed for each household in
 * [SplitMode.Amounts] only.
 */
data class ExpenseForm(
    val title: String = "",
    val amount: String = "",
    val currency: String = EURO,
    val rate: String = "",
    val date: LocalDate? = null,
    val payerId: String? = null,
    val category: ExpenseCategory = ExpenseCategory.Other,
    val mode: SplitMode = SplitMode.Shares,
    val householdIds: Set<String> = emptySet(),
    val amounts: Map<String, String> = emptyMap(),
)

enum class ExpenseFormError { Title, Amount, Currency, Rate, Date, Payer, Households, Amounts }

/**
 * Typed euros in whole cents, without going through a Double: « 12 », « 12,5 » or « 12.50 », at
 * most two decimals. Null for anything else (« 1e3 », « 12,505 », a minus sign).
 */
internal fun centsOf(text: String): Long? {
    val cleaned = text.replace("€", "").replace('\u00A0', ' ').replace('\u202F', ' ').replace(" ", "").trim()
    val match = Regex("^(\\d{1,7})(?:[.,](\\d{1,2}))?$").matchEntire(cleaned) ?: return null
    val euros = match.groupValues[1].toLong()
    val cents = match.groupValues[2].padEnd(2, '0').toLong()
    return euros * 100 + cents
}

/** Typed text in whole cents; null when it is not a positive amount under the cap. */
internal fun parseCents(text: String): Long? = centsOf(text)?.takeIf { it in 1..EXPENSE_MAX_CENTS }

/** The rate typed for a currency: euros for one unit, « 0,92 » or « 0.92 ». */
internal fun parseRate(text: String): Double? =
    text.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 && it < 100_000 && !it.isNaN() }

/** What is wrong in [form]; empty when it can be saved. [payerHouseholds] maps each possible payer to their household. */
fun validate(form: ExpenseForm, payerHouseholds: Map<String, String>, bookHouseholds: Set<String>): Set<ExpenseFormError> = buildSet {
    if (form.title.isBlank() || form.title.trim().length > EXPENSE_TITLE_MAX) add(ExpenseFormError.Title)
    val currency = form.currency.trim().uppercase()
    val currencyOk = currency.matches(Regex("^[A-Z]{3}$"))
    if (!currencyOk) add(ExpenseFormError.Currency)
    val original = parseCents(form.amount)
    if (original == null) add(ExpenseFormError.Amount)
    // The rate is asked only once the currency is known.
    val rate = if (currency == EURO) 1.0 else parseRate(form.rate)
    if (rate == null && currencyOk) add(ExpenseFormError.Rate)
    // A tiny rate would round the expense to nothing, which the rules refuse.
    if (original != null && rate != null && toEuroCents(original, rate) !in 1..EXPENSE_MAX_CENTS) add(ExpenseFormError.Rate)
    if (form.date == null) add(ExpenseFormError.Date)
    if (form.payerId == null || form.payerId !in payerHouseholds) add(ExpenseFormError.Payer)
    if (form.householdIds.isEmpty() || !bookHouseholds.containsAll(form.householdIds)) add(ExpenseFormError.Households)
    if (form.mode == SplitMode.Amounts && original != null && rate != null) {
        val total = toEuroCents(original, rate)
        val typed = form.householdIds.map { id -> form.amounts[id].let { text -> if (text.isNullOrBlank()) 0L else centsOf(text) } }
        // Every household gets a whole amount, none below zero, and they add up to the expense.
        if (typed.any { it == null || it < 0 } || typed.sumOf { it ?: 0L } != total) add(ExpenseFormError.Amounts)
    }
}

/** The expense of a valid [form] (see [validate]); amounts in another currency are turned into euros at its rate. */
fun expenseOf(id: String, form: ExpenseForm, payerHouseholds: Map<String, String>, authorId: String): Expense {
    val currency = form.currency.trim().uppercase()
    val original = parseCents(form.amount)!!
    val rate = if (currency == EURO) 1.0 else parseRate(form.rate)!!
    val households = form.householdIds.sorted()
    return Expense(
        id = id,
        title = form.title.trim(),
        amountCents = toEuroCents(original, rate),
        date = form.date!!,
        payerId = form.payerId!!,
        payerHouseholdId = payerHouseholds.getValue(form.payerId),
        category = form.category,
        mode = form.mode,
        householdIds = households,
        amounts = if (form.mode == SplitMode.Amounts) {
            households.associateWith { id -> form.amounts[id]?.takeIf { it.isNotBlank() }?.let { centsOf(it)!! } ?: 0L }
        } else {
            emptyMap()
        },
        currency = currency,
        originalCents = original,
        rate = rate,
        authorId = authorId,
    )
}

/** The form of an existing [expense], to change it. */
fun formOf(expense: Expense): ExpenseForm = ExpenseForm(
    title = expense.title,
    amount = centsText(expense.originalCents),
    currency = expense.currency,
    rate = if (expense.currency == EURO) "" else expense.rate.toString().replace('.', ','),
    date = expense.date,
    payerId = expense.payerId,
    category = expense.category,
    mode = expense.mode,
    householdIds = expense.householdIds.toSet(),
    amounts = expense.amounts.mapValues { (_, cents) -> centsText(cents) },
)

/** « 12,50 », for a text field. */
internal fun centsText(cents: Long): String = "%d,%02d".format(cents / 100, cents % 100)
