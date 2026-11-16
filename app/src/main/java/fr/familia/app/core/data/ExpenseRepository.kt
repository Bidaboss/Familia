package fr.familia.app.core.data

import fr.familia.app.core.model.Expense
import fr.familia.app.core.model.ExpenseBook
import fr.familia.app.core.model.ExpenseContainer
import fr.familia.app.core.model.ExpenseSettlement
import fr.familia.app.core.model.Member
import kotlinx.coroutines.flow.Flow

/**
 * The accounts of a container, a trip today (PRD rév. 193, récit 16 bis). Screens never talk to
 * Firestore directly. Only the adult participants may read them: anyone else gets an error.
 */
interface ExpenseRepository {
    /** The book of [container], updated live; null while nobody has opened it yet. */
    fun observeBook(container: ExpenseContainer): Flow<ExpenseBook?>

    /** Opens the book with the default [shares]; its creator is the creator of the container. */
    suspend fun openBook(container: ExpenseContainer, creatorId: String, shares: Map<String, Double>)

    /** Changes the shares of the households, by the creator of the container. */
    suspend fun setShares(bookId: String, shares: Map<String, Double>)

    fun observeExpenses(bookId: String): Flow<List<Expense>>

    fun observeSettlements(bookId: String): Flow<List<ExpenseSettlement>>

    fun newExpenseId(bookId: String): String

    /** Creates the expense, or updates it when [isNew] is false (author and creation time never change). */
    suspend fun saveExpense(bookId: String, expense: Expense, me: Member, isNew: Boolean)

    suspend fun deleteExpense(bookId: String, expenseId: String)
}
