// Les comptes (PRD rév. 193, récit 16 bis): a common brick. A book of accounts lives at
// expenseBooks/{containerType}_{containerId}, out of the trip, so it is removed here with its
// container. Each change of an expense is written in its history, and the adult participants get
// one grouped notice a day at most, in the evening, with no reminder ever.
import { FieldValue, Timestamp, type Firestore } from 'firebase-admin/firestore';
import { parisDay, type Notice } from './logic';
import { featureOn, loadMembers, notify, type Sender } from './store';

export type ExpenseContainerType = 'trip';

/** The id of the book of a container: one per container, found without a query. */
export function expenseBookId(containerType: ExpenseContainerType, containerId: string): string {
  return `${containerType}_${containerId}`;
}

/** Removes the accounts of a container, with their expenses, history, reimbursements and daily notice. */
export async function deleteExpenseBook(db: Firestore, containerType: ExpenseContainerType, containerId: string): Promise<void> {
  const id = expenseBookId(containerType, containerId);
  await db.recursiveDelete(db.collection('expenseBooks').doc(id));
  await db.collection('expenseNotices').doc(id).delete();
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

// --- La notification du jour (récit 16 bis) -----------------------------------------------------

/** From this hour in Paris, the day's notice of the accounts leaves (run by the task of every 30 minutes). */
export const EXPENSE_NOTICE_HOUR = 20;

/** An expense added since the last notice: who noted it, and how much. */
export interface NewExpense {
  authorId: string;
  amountCents: number;
}

/** « 125,00 € », the French way, without grouping so it never splits. */
export function formatEuroCents(cents: number): string {
  const whole = Math.floor(Math.abs(cents) / 100);
  const rest = String(Math.abs(cents) % 100).padStart(2, '0');
  return `${cents < 0 ? '-' : ''}${whole},${rest}\u00A0€`;
}

/**
 * The notice of one member: what the others noted since the last one, grouped. Null when only this
 * member added expenses, or nobody did: a notice about one's own expenses tells nothing.
 */
export function expenseNotice(tripId: string, tripTitle: string, memberId: string, added: NewExpense[]): Notice | null {
  const others = added.filter((e) => e.authorId !== memberId);
  if (others.length === 0) return null;
  const total = others.reduce((sum, e) => sum + e.amountCents, 0);
  const what = others.length === 1 ? 'Une nouvelle dépense' : `${others.length} nouvelles dépenses`;
  return {
    channel: 'trips',
    title: `Comptes · ${tripTitle}`,
    // Since the last notice, which may be days ago when nobody added anything in between.
    body: `${what}, ${formatEuroCents(total)}. Le solde est à jour dans l'appli.`,
    tripId,
  };
}

/**
 * Once a day from [EXPENSE_NOTICE_HOUR], each open book tells its adult participants what the others
 * added since the last notice. expenseNotices/{bookId} keeps the day and the time of the last one
 * (only the functions use it): a book never gets two notices on the same day. While the module
 * `expenses` is off, only the admin principal is told, like every module behind its switch.
 */
export async function sendExpenseNotices(db: Firestore, send: Sender, now: Date): Promise<number> {
  const hour = Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Paris', hour: '2-digit', hourCycle: 'h23' }).format(now));
  if (hour < EXPENSE_NOTICE_HOUR) return 0;
  const books = await db.collection('expenseBooks').where('closed', '==', false).get();
  if (books.empty) return 0;
  const today = parisDay(now);
  const on = await featureOn(db, 'expenses');
  const owner = on ? null : (await db.doc('config/family').get()).get('ownerMemberId');
  const members = await loadMembers(db);
  let told = 0;
  for (const book of books.docs) {
    const marker = db.collection('expenseNotices').doc(book.id);
    const since = await db.runTransaction(async (t) => {
      const last = await t.get(marker);
      if (last.get('day') === today) return null;
      t.set(marker, { day: today, at: now });
      const at = last.get('at');
      // The first evening of a book looks back one day.
      return at instanceof Timestamp ? at.toDate() : new Date(now.getTime() - 24 * 3600 * 1000);
    });
    if (!since) continue;
    const containerId = book.get('containerId');
    if (book.get('containerType') !== 'trip' || typeof containerId !== 'string') continue;
    const trip = await db.collection('trips').doc(containerId).get();
    if (!trip.exists) continue;
    const added = await book.ref.collection('expenses').where('createdAt', '>', since).get();
    if (added.empty) continue;
    const list: NewExpense[] = added.docs.map((e) => ({ authorId: String(e.get('authorId') ?? ''), amountCents: Number(e.get('amountCents') ?? 0) }));
    const participants = new Set((trip.get('participantIds') as string[] | undefined) ?? []);
    const recipients = members
      .filter((m) => m.profile === 'adult' && participants.has(m.id))
      .filter((m) => on || (typeof owner === 'string' && m.id === owner));
    const title = String(trip.get('title') ?? '');
    for (const member of recipients) {
      const notice = expenseNotice(containerId, title, member.id, list);
      if (notice && (await notify(db, send, [member.id], notice)) > 0) told++;
    }
  }
  return told;
}
