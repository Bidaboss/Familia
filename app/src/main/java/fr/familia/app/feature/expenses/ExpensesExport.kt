package fr.familia.app.feature.expenses

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import fr.familia.app.core.model.EURO
import fr.familia.app.core.model.ExpenseCategory
import fr.familia.app.core.model.SplitMode
import java.io.File
import java.util.Locale

/** Euro cents for a spreadsheet: « 12,50 », with no symbol so the sum still works. */
internal fun csvCents(cents: Long): String = String.format(Locale.FRANCE, "%.2f", cents / 100.0)

/** One cell: quoted when it holds the separator, a quote or a line break. */
internal fun csvCell(value: String): String =
    if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

/** A cell typed by a member: one starting like a formula (« = », « + », « - », « @ ») stays text. */
internal fun csvText(value: String): String = if (value.firstOrNull()?.let { it in "=+-@" } == true) "'$value" else value

/**
 * The accounts as a table for a spreadsheet (récit 16 bis): the expenses, then the balance of each
 * household and « Qui rembourse qui ». French style: « ; » between cells and a comma in amounts,
 * so Excel and LibreOffice open it as it is. [category] and [split] give the words of the app.
 */
fun expensesCsv(
    state: ExpensesUiState.Success,
    category: (ExpenseCategory) -> String,
    split: (SplitMode) -> String,
): String {
    val names = state.households.associate { it.id to it.name }
    val lines = mutableListOf<List<String>>()
    lines += listOf("Date", "Dépense", "Catégorie", "Payé par", "Foyer", "Montant (€)", "Devise", "Montant d'origine", "Taux", "Partage", "Foyers")
    for (row in state.expenses.sortedWith(compareBy({ it.expense.date }, { it.expense.title.lowercase() }))) {
        val e = row.expense
        lines += listOf(
            e.date.toString(),
            csvText(e.title),
            category(e.category),
            csvText(row.payerName),
            csvText(names[e.payerHouseholdId] ?: e.payerHouseholdId),
            csvCents(e.amountCents),
            e.currency,
            if (e.currency == EURO) "" else csvCents(e.originalCents),
            if (e.currency == EURO) "" else e.rate.toString().replace('.', ','),
            split(e.mode),
            csvText(e.householdIds.joinToString(", ") { names[it] ?: it }),
        )
    }
    lines += emptyList<String>()
    lines += listOf("Foyer", "Parts", "A payé (€)", "Sa part (€)", "Solde (€)")
    for (h in state.households) {
        lines += listOf(csvText(h.name), shareText(h.shares), csvCents(h.paidCents), csvCents(h.owedCents), csvCents(h.netCents))
    }
    lines += emptyList<String>()
    lines += listOf("Qui rembourse qui")
    lines += listOf("De", "À", "Montant (€)", "État")
    for (t in state.transfers) lines += listOf(csvText(t.fromName), csvText(t.toName), csvCents(t.amountCents), "à rembourser")
    for (s in state.settlements) lines += listOf(csvText(s.fromName), csvText(s.toName), csvCents(s.amountCents), "remboursé")
    lines += emptyList<String>()
    lines += listOf("Total", "", csvCents(state.totalCents))
    // The byte order mark tells Excel the file is UTF-8, so « é » stays « é ».
    return "\uFEFF" + lines.joinToString("\r\n") { cells -> cells.joinToString(";") { csvCell(it) } } + "\r\n"
}

/** The lines of the Argile image: the total, then who pays back whom, never more than the card holds. */
fun expensesImageLines(
    state: ExpensesUiState.Success,
    total: String,
    transfer: (TransferRow) -> String,
    allSettled: String,
    more: (Int) -> String,
): List<String> {
    val shown = state.transfers.take(IMAGE_TRANSFERS)
    val rest = state.transfers.size - shown.size
    return listOf(total) + (if (state.transfers.isEmpty()) listOf(allSettled) else shown.map(transfer)) +
        (if (rest > 0) listOf(more(rest)) else emptyList())
}

private const val IMAGE_TRANSFERS = 4

/**
 * Opens the share sheet of Android with the table. The file is written to the cache folder shares/
 * (res/xml/photo_paths.xml), replaced at each export. False when no app can receive it.
 */
fun shareCsv(context: Context, csv: String, fileName: String, title: String, chooserTitle: String): Boolean {
    val folder = File(context.cacheDir, "shares").apply { mkdirs() }
    val file = File(folder, fileName)
    file.writeText(csv, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TITLE, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(Intent.createChooser(send, chooserTitle))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** A file name without the characters a phone refuses: « comptes-corse-2027.csv ». */
fun csvFileName(title: String): String {
    val slug = title.lowercase(Locale.FRANCE)
        .let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD) }
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(40)
    return if (slug.isEmpty()) "comptes.csv" else "comptes-$slug.csv"
}
