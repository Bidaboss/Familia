package fr.familia.app.feature.expenses

import fr.familia.app.core.model.Expense
import fr.familia.app.core.model.ExpenseCategory
import fr.familia.app.core.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpensesExportTest {

    private val market = Expense(
        "e1", "Marché; fromages", 8_450, LocalDate.of(2027, 7, 12), "adult1", "h1",
        ExpenseCategory.Groceries, SplitMode.Shares, listOf("h1", "h2"), authorId = "adult1",
    )
    private val ferry = Expense(
        "e2", "Ferry", 12_000, LocalDate.of(2027, 7, 10), "adult2", "h2",
        ExpenseCategory.Transport, SplitMode.Equal, listOf("h1", "h2"), currency = "CHF", originalCents = 12_500, rate = 0.96, authorId = "adult2",
    )
    private val state = ExpensesUiState.Success(
        title = "Corse", bookId = "trip_t1", closed = false, isCreator = false, totalCents = 20_450,
        households = listOf(HouseholdRow("h2", "Foyer Deux", 1.0, 12_000, 9_000, 3_000), HouseholdRow("h1", "Foyer Un", 1.5, 8_450, 11_450, -3_000)),
        transfers = listOf(TransferRow("h1", "h2", "Foyer Un", "Foyer Deux", 3_000)),
        expenses = listOf(ExpenseRow(market, "Camille", false), ExpenseRow(ferry, "Sacha", false)),
    )

    @Test
    fun `the table opens in a French spreadsheet, oldest expense first, with the balance and the transfers`() {
        val csv = expensesCsv(state, { it.name }, { it.name })
        assertTrue(csv.startsWith("\uFEFFDate;Dépense;"))
        val lines = csv.removePrefix("\uFEFF").split("\r\n")
        assertEquals("2027-07-10;Ferry;Transport;Sacha;Foyer Deux;120,00;CHF;125,00;0,96;Equal;Foyer Un, Foyer Deux", lines[1])
        assertEquals("2027-07-12;\"Marché; fromages\";Groceries;Camille;Foyer Un;84,50;EUR;;;Shares;Foyer Un, Foyer Deux", lines[2])
        assertTrue(lines.contains("Foyer Un;1,5;84,50;114,50;-30,00"))
        assertTrue(lines.contains("De;À;Montant (€);État"))
        assertTrue(lines.contains("Foyer Un;Foyer Deux;30,00;à rembourser"))
        assertTrue(lines.contains("Foyer Un;1,5;84,50;114,50;-30,00"))
        assertTrue(lines.contains("Total;;204,50"))
    }

    @Test
    fun `a cell with a quote is quoted, and the file name stays plain`() {
        assertEquals("\"Le \"\"grand\"\" plein\"", csvCell("Le \"grand\" plein"))
        assertEquals("comptes-ete-a-l-ile.csv", csvFileName("Été à l'île !"))
        assertEquals("comptes.csv", csvFileName("…"))
        // A title that looks like a formula stays text in the spreadsheet; a negative amount does not.
        assertEquals("'=SOMME(A1)", csvText("=SOMME(A1)"))
        assertEquals("'-5 € de remise", csvText("-5 € de remise"))
        assertEquals("Ferry", csvText("Ferry"))
    }

    @Test
    fun `the image keeps the total and at most four transfers`() {
        val many = state.copy(transfers = (1..6).map { TransferRow("a$it", "b", "A$it", "B", 100L * it) })
        val lines = expensesImageLines(many, "Total", { it.fromName }, "À jour", { "et $it autres" })
        assertEquals(listOf("Total", "A1", "A2", "A3", "A4", "et 2 autres"), lines)
        assertEquals(listOf("Total", "À jour"), expensesImageLines(state.copy(transfers = emptyList()), "Total", { "" }, "À jour", { "" }))
    }
}
