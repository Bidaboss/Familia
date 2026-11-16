package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.Expense
import fr.familia.app.core.model.ExpenseBook
import fr.familia.app.core.model.ExpenseCategory
import fr.familia.app.core.model.ExpenseContainer
import fr.familia.app.core.model.ExpenseContainerType
import fr.familia.app.core.model.ExpenseHistoryAction
import fr.familia.app.core.model.ExpenseHistoryEntry
import fr.familia.app.core.model.ExpenseSettlement
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.SplitMode
import fr.familia.app.core.model.Transfer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * The accounts live in `expenseBooks/{type}_{id}`, with `expenses/` and `settlements/` under the
 * book, out of the trip (firestore.rules, bloc « Comptes »). Amounts are whole euro cents.
 */
class FirestoreExpenseRepository(private val db: FirebaseFirestore) : ExpenseRepository {

    private fun book(bookId: String) = db.collection(BOOKS).document(bookId)

    override fun observeBook(container: ExpenseContainer): Flow<ExpenseBook?> = callbackFlow {
        val registration = book(container.bookId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.takeIf { it.exists() }?.toBook())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun openBook(container: ExpenseContainer, creatorId: String, shares: Map<String, Double>) {
        book(container.bookId).set(
            mapOf(
                "containerType" to container.type.firestoreValue,
                "containerId" to container.id,
                "creatorId" to creatorId,
                "shares" to shares,
                "closed" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun setShares(bookId: String, shares: Map<String, Double>) {
        book(bookId).update(mapOf("shares" to shares, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun setClosed(bookId: String, closed: Boolean) {
        book(bookId).update(mapOf("closed" to closed, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override fun observeExpenses(bookId: String): Flow<List<Expense>> =
        observe(book(bookId).collection(EXPENSES).orderBy("date", Query.Direction.DESCENDING)) { it.toExpense() }

    override fun observeSettlements(bookId: String): Flow<List<ExpenseSettlement>> =
        observe(book(bookId).collection(SETTLEMENTS)) { it.toSettlement() }

    override fun newExpenseId(bookId: String): String = book(bookId).collection(EXPENSES).document().id

    override fun observeHistory(bookId: String, expenseId: String): Flow<List<ExpenseHistoryEntry>> =
        observe(book(bookId).collection(EXPENSES).document(expenseId).collection(HISTORY).orderBy("at")) { it.toHistoryEntry() }

    override suspend fun saveExpense(bookId: String, expense: Expense, me: Member, isNew: Boolean) {
        val fields = mapOf(
            "title" to expense.title,
            "amountCents" to expense.amountCents,
            "date" to expense.date.toString(),
            "payerId" to expense.payerId,
            "payerHouseholdId" to expense.payerHouseholdId,
            "category" to expense.category.firestoreValue,
            "mode" to expense.mode.firestoreValue,
            "householdIds" to expense.householdIds,
            "amounts" to if (expense.mode == SplitMode.Amounts) expense.amounts else emptyMap<String, Long>(),
            "currency" to expense.currency,
            "originalCents" to expense.originalCents,
            "rate" to expense.rate,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val ref = book(bookId).collection(EXPENSES).document(expense.id)
        if (isNew) {
            ref.set(fields + mapOf("authorId" to me.id, "authorName" to me.firstName, "createdAt" to FieldValue.serverTimestamp())).await()
        } else {
            ref.update(fields).await()
        }
    }

    override suspend fun deleteExpense(bookId: String, expenseId: String) {
        book(bookId).collection(EXPENSES).document(expenseId).delete().await()
    }

    override suspend fun settle(bookId: String, transfer: Transfer, me: Member) {
        book(bookId).collection(SETTLEMENTS).document().set(
            mapOf(
                "fromHouseholdId" to transfer.fromHouseholdId,
                "toHouseholdId" to transfer.toHouseholdId,
                "amountCents" to transfer.amountCents,
                "confirmedBy" to me.id,
                "confirmedByName" to me.firstName,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun unsettle(bookId: String, settlementId: String) {
        book(bookId).collection(SETTLEMENTS).document(settlementId).delete().await()
    }

    private fun <T> observe(query: Query, map: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else if (snapshot != null) trySend(snapshot.documents.mapNotNull(map))
        }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toBook(): ExpenseBook? {
        val type = ExpenseContainerType.fromFirestore(getString("containerType")) ?: return null
        val containerId = getString("containerId") ?: return null
        val shares = (get("shares") as? Map<*, *>).orEmpty()
            .mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.toDouble()?.let { key to it } } }
            .toMap()
        return ExpenseBook(id, ExpenseContainer(type, containerId), getString("creatorId").orEmpty(), shares, getBoolean("closed") == true)
    }

    private fun DocumentSnapshot.toExpense(): Expense? {
        val amount = getLong("amountCents") ?: return null
        @Suppress("UNCHECKED_CAST")
        val households = (get("householdIds") as? List<Any?>)?.filterIsInstance<String>() ?: return null
        val amounts = (get("amounts") as? Map<*, *>).orEmpty()
            .mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.toLong()?.let { key to it } } }
            .toMap()
        return Expense(
            id = id,
            title = getString("title").orEmpty(),
            amountCents = amount,
            date = getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null,
            payerId = getString("payerId") ?: return null,
            payerHouseholdId = getString("payerHouseholdId") ?: return null,
            category = ExpenseCategory.fromFirestore(getString("category")),
            mode = SplitMode.fromFirestore(getString("mode")),
            householdIds = households,
            amounts = amounts,
            currency = getString("currency") ?: return null,
            originalCents = getLong("originalCents") ?: amount,
            rate = getDouble("rate") ?: 1.0,
            authorId = getString("authorId").orEmpty(),
        )
    }

    private fun DocumentSnapshot.toHistoryEntry(): ExpenseHistoryEntry? {
        val action = ExpenseHistoryAction.fromFirestore(getString("action")) ?: return null
        @Suppress("UNCHECKED_CAST")
        val changed = (get("changed") as? List<Any?>)?.filterIsInstance<String>().orEmpty()
        return ExpenseHistoryEntry(
            id = id,
            action = action,
            byName = getString("byName").orEmpty(),
            title = getString("title").orEmpty(),
            amountCents = getLong("amountCents") ?: 0,
            changed = changed,
            at = getTimestamp("at")?.toDate()?.toInstant(),
        )
    }

    private fun DocumentSnapshot.toSettlement(): ExpenseSettlement? {
        return ExpenseSettlement(
            id = id,
            fromHouseholdId = getString("fromHouseholdId") ?: return null,
            toHouseholdId = getString("toHouseholdId") ?: return null,
            amountCents = getLong("amountCents") ?: return null,
            confirmedBy = getString("confirmedBy").orEmpty(),
            confirmedByName = getString("confirmedByName").orEmpty(),
        )
    }

    private companion object {
        const val BOOKS = "expenseBooks"
        const val EXPENSES = "expenses"
        const val SETTLEMENTS = "settlements"
        const val HISTORY = "history"
    }
}
