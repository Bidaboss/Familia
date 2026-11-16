// Les comptes (PRD rév. 193, récit 16 bis): a common brick. A book of accounts lives at
// expenseBooks/{containerType}_{containerId}, out of the trip, so it is removed here with its
// container. Each change of an expense is written in its history; the daily notice comes later.
import { FieldValue, type Firestore } from 'firebase-admin/firestore';

export type ExpenseContainerType = 'trip';

/** The id of the book of a container: one per container, found without a query. */
export function expenseBookId(containerType: ExpenseContainerType, containerId: string): string {
  return `${containerType}_${containerId}`;
}

/** Removes the accounts of a container, with their expenses, history and reimbursements. */
export async function deleteExpenseBook(db: Firestore, containerType: ExpenseContainerType, containerId: string): Promise<void> {
  await db.recursiveDelete(db.collection('expenseBooks').doc(expenseBookId(containerType, containerId)));
}

/** The fields of an expense that the history follows. */
const FOLLOWED = ['title', 'amountCents', 'date', 'payerId', 'category', 'mode', 'householdIds', 'amounts', 'currency', 'originalCents', 'rate'] as const;

export type ExpenseData = Partial<Record<(typeof FOLLOWED)[number], unknown>> & { title?: string; amountCents?: number };

export interface HistoryEntry {
  action: 'created' | 'edited' | 'deleted';
  byId: string | null;
  byName: string;
  title: string;
  amountCents: number;
  changed: string[];
}

/**
 * One line of the history of an expense (récit 16 bis): who did what, and which fields changed.
 * Null when nothing the members see has changed (the server's own touch, for example).
 */
export function historyEntry(
  before: ExpenseData | undefined,
  after: ExpenseData | undefined,
  actor: { id: string; firstName: string } | null,
): HistoryEntry | null {
  const shown = after ?? before;
  if (!shown) return null;
  const action = !before ? 'created' : !after ? 'deleted' : 'edited';
  const changed = action === 'edited' ? FOLLOWED.filter((k) => JSON.stringify(before?.[k]) !== JSON.stringify(after?.[k])) : [];
  if (action === 'edited' && changed.length === 0) return null;
  return {
    action,
    byId: actor?.id ?? null,
    byName: actor?.firstName ?? '',
    title: typeof shown.title === 'string' ? shown.title : '',
    amountCents: typeof shown.amountCents === 'number' ? shown.amountCents : 0,
    changed: [...changed],
  };
}

/** The collection of each kind of container: a trip today, an event later. */
const CONTAINERS: Record<ExpenseContainerType, string> = { trip: 'trips' };

/**
 * Writes [entry] under the expense; never once its book or its container is gone. Deleting a trip
 * removes the expenses before the book: their removal must leave nothing behind.
 */
export async function writeExpenseHistory(db: Firestore, bookId: string, expenseId: string, entry: HistoryEntry): Promise<void> {
  const book = db.collection('expenseBooks').doc(bookId);
  const snapshot = await book.get();
  if (!snapshot.exists) return;
  const type = snapshot.get('containerType') as ExpenseContainerType | undefined;
  const containerId = snapshot.get('containerId');
  const collection = type ? CONTAINERS[type] : undefined;
  if (!collection || typeof containerId !== 'string' || !(await db.collection(collection).doc(containerId).get()).exists) return;
  await book.collection('expenses').doc(expenseId).collection('history').add({ ...entry, at: FieldValue.serverTimestamp() });
}
