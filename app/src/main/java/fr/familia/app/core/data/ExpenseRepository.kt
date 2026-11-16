package fr.familia.app.core.data

import fr.familia.app.core.model.Expense
import fr.familia.app.core.model.ExpenseBook
import fr.familia.app.core.model.ExpenseContainer
import fr.familia.app.core.model.ExpenseSettlement
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Transfer
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

    /** Closes the accounts, which freezes the balance, or opens them again: by the creator. */
    suspend fun setClosed(bookId: String, closed: Boolean)

    fun observeExpenses(bookId: String): Flow<List<Expense>>

    fun observeSettlements(bookId: String): Flow<List<ExpenseSettlement>>

    fun newExpenseId(bookId: String): String

    /** Creates the expense, or updates it when [isNew] is false (author and creation time never change). */
    suspend fun saveExpense(bookId: String, expense: Expense, me: Member, isNew: Boolean)

    suspend fun deleteExpense(bookId: String, expenseId: String)

    /** « C'est remboursé »: [me], of the household that received [transfer], ticks it. */
    suspend fun settle(bookId: String, transfer: Transfer, me: Member)

    /** Takes back a « C'est remboursé », by who ticked it. */
    suspend fun unsettle(bookId: String, settlementId: String)
}
