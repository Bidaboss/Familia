// On se prête (PRD, récit 51): the notices of a loan. An ask goes straight to the owner, the
// answer straight to the one who asked, and one gentle reminder goes to the borrower 3 days
// after the return date, in the task of 18 h of the coups de main. No money, ever.

import { FieldValue, Timestamp, type Firestore } from 'firebase-admin/firestore';
import { formatDay, parisDay, type Notice } from './logic';
import { deliver } from './prefs';
import type { Sender } from './store';

export type LendStatus = 'available' | 'asked' | 'lent';

/**
 * The thing as everyone reads it, in lendItems/{itemId}: no name and no date of a loan, only
 * `lent` (EXPERT-9, PRD rév. 268). The ask or the loan lives in lendLoans/{itemId}, which only the
 * owner, the borrower and the admin principal (for the export) read.
 */
export interface LendThingData {
  name: string;
  ownerId: string;
  ownerName: string;
  lent: boolean;
}

/** lendLoans/{itemId}: the ask, then the loan, of one thing. Gone when the thing is free. */
export interface LendLoanData {
  ownerId: string;
  parties: string[];
  status: 'asked' | 'lent';
  borrowerId: string;
  borrowerName: string;
  from: Timestamp | null;
  until: Timestamp | null;
  remindedAt: Timestamp | null;
}

/** The thing and its loan together, as the notices tell it. */
export interface LendItemData {
  name: string;
  ownerId: string;
  ownerName: string;
  status: LendStatus;
  borrowerId: string | null;
  borrowerName: string | null;
  from: Timestamp | null;
  until: Timestamp | null;
  remindedAt: Timestamp | null;
}

export function withLoan(thing: LendThingData, loan: LendLoanData | undefined): LendItemData {
  return {
    name: thing.name, ownerId: thing.ownerId, ownerName: thing.ownerName,
    status: loan?.status ?? 'available', borrowerId: loan?.borrowerId ?? null, borrowerName: loan?.borrowerName ?? null,
    from: loan?.from ?? null, until: loan?.until ?? null, remindedAt: loan?.remindedAt ?? null,
  };
}

/** The reminder leaves 3 days after the return date (récit 51), once. */
export const REMIND_AFTER_DAYS = 3;
const DAY_MS = 24 * 60 * 60 * 1000;

const day = (t: Timestamp) => formatDay(parisDay(t.toDate()));

/** « du samedi 12 décembre au mardi 15 décembre », « à partir du … », « jusqu'au … », or nothing. */
export function lendDates(from: Timestamp | null, until: Timestamp | null): string {
  if (from && until) return ` du ${day(from)} au ${day(until)}`;
  if (from) return ` à partir du ${day(from)}`;
  if (until) return ` jusqu'au ${day(until)}`;
  return '';
}

function notice(itemId: string, title: string, body: string, direct: boolean): Notice {
  return { channel: 'helpRequests', title, body, lendItemId: itemId, direct, tag: `lend-${itemId}` };
}

/** « Je voudrais l'emprunter », to the owner. */
export function askNotice(itemId: string, item: LendItemData): Notice {
  return notice(itemId, 'On voudrait t\'emprunter quelque chose',
    `${item.borrowerName} voudrait emprunter ${item.name}${lendDates(item.from, item.until)}.`, true);
}

/** « D'accord », to the one who asked. */
export function acceptNotice(itemId: string, item: LendItemData): Notice {
  return notice(itemId, 'C\'est d\'accord', `${item.ownerName} te prête ${item.name}.`, true);
}

/** « Pas cette fois », to the one who asked. */
export function declineNotice(itemId: string, item: LendItemData): Notice {
  return notice(itemId, 'Pas cette fois', `${item.ownerName} ne peut pas te prêter ${item.name} cette fois.`, true);
}

/** The one gentle reminder, to the borrower: never direct, so it waits for the morning. */
export function reminderNotice(itemId: string, item: LendItemData): Notice {
  const when = item.until ? ` devait rentrer le ${day(item.until)}` : ' devait rentrer';
  return notice(itemId, 'Petit rappel',
    `${item.name} de ${item.ownerName}${when}. Tu le rendras quand tu pourras.`, false);
}

/** The member id of [uid], the one who made the change in the app. */
async function memberOfUid(db: Firestore, uid: string | undefined): Promise<string | null> {
  if (!uid) return null;
  const found = await db.collection('members').where('uid', '==', uid).limit(1).get();
  return found.empty ? null : found.docs[0].id;
}

/**
 * What a change of a loan tells: an ask to the owner, « D'accord » or « Pas cette fois » to the
 * one who asked. A dropped ask (the borrower), « Rendu », and a loan that goes with its thing
 * (removed, or its owner gone) tell nobody.
 */
export async function onLendLoanWritten(
  db: Firestore, send: Sender, itemId: string, before: LendLoanData | undefined, after: LendLoanData | undefined,
  actorUid: string | undefined, now: Date,
): Promise<void> {
  const thingDoc = await db.doc(`lendItems/${itemId}`).get();
  if (!thingDoc.exists) return;
  const thing = thingDoc.data() as LendThingData;
  if (!before && after?.status === 'asked') {
    await deliver(db, send, [thing.ownerId], askNotice(itemId, withLoan(thing, after)), now);
  } else if (before?.status === 'asked' && after?.status === 'lent') {
    await deliver(db, send, [after.borrowerId], acceptNotice(itemId, withLoan(thing, after)), now);
  } else if (before?.status === 'asked' && !after) {
    const actor = await memberOfUid(db, actorUid);
    if (actor === null || actor === before.borrowerId) return;
    await deliver(db, send, [before.borrowerId], declineNotice(itemId, withLoan(thing, before)), now);
  }
}

/**
 * The things lent whose return date passed 3 days ago and not yet reminded: one gentle reminder
 * to the borrower, then remindedAt so it never comes again. Returns the number of reminders.
 */
export async function sendLendReminders(db: Firestore, send: Sender, now: Date): Promise<number> {
  const limit = now.getTime() - REMIND_AFTER_DAYS * DAY_MS;
  const lent = await db.collection('lendLoans').where('status', '==', 'lent').get();
  let told = 0;
  for (const doc of lent.docs) {
    const loan = doc.data() as LendLoanData;
    if (loan.remindedAt || !loan.until || !loan.borrowerId || loan.until.toMillis() > limit) continue;
    const thing = await db.doc(`lendItems/${doc.id}`).get();
    if (!thing.exists) continue;
    await deliver(db, send, [loan.borrowerId], reminderNotice(doc.id, withLoan(thing.data() as LendThingData, loan)), now);
    await doc.ref.update({ remindedAt: Timestamp.fromDate(now) });
    told++;
  }
  return told;
}

/** A thing removed takes its ask or loan with it. */
export async function deleteLoanOf(db: Firestore, itemId: string): Promise<void> {
  await db.doc(`lendLoans/${itemId}`).delete();
}

/**
 * A member removed from the family: their things go, with their loans, and a thing they asked for
 * or had is free again. Returns the number of things changed.
 */
export async function releaseLendItemsOf(db: Firestore, memberId: string): Promise<number> {
  const owned = await db.collection('lendItems').where('ownerId', '==', memberId).get();
  const borrowed = await db.collection('lendLoans').where('borrowerId', '==', memberId).get();
  const batch = db.batch();
  for (const doc of owned.docs) {
    batch.delete(doc.ref);
    batch.delete(db.doc(`lendLoans/${doc.id}`));
  }
  const released = borrowed.docs.filter((d) => d.get('ownerId') !== memberId);
  for (const doc of released) {
    batch.delete(doc.ref);
    const thing = db.doc(`lendItems/${doc.id}`);
    if ((await thing.get()).exists) batch.update(thing, { lent: false, updatedAt: FieldValue.serverTimestamp() });
  }
  await batch.commit();
  return owned.size + released.length;
}
