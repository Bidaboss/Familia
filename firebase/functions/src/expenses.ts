// Les comptes (PRD rév. 193, récit 16 bis): a common brick. A book of accounts lives at
// expenseBooks/{containerType}_{containerId}, out of the trip, so it is removed here with its
// container. The history of each expense and the daily notice come in a later part.
import type { Firestore } from 'firebase-admin/firestore';

export type ExpenseContainerType = 'trip';

/** The id of the book of a container: one per container, found without a query. */
export function expenseBookId(containerType: ExpenseContainerType, containerId: string): string {
  return `${containerType}_${containerId}`;
}

/** Removes the accounts of a container, with their expenses, history and reimbursements. */
export async function deleteExpenseBook(db: Firestore, containerType: ExpenseContainerType, containerId: string): Promise<void> {
  await db.recursiveDelete(db.collection('expenseBooks').doc(expenseBookId(containerType, containerId)));
}
