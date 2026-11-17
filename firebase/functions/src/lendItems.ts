// On se prête (PRD, récit 51): the notices of a loan. An ask goes straight to the owner, the
// answer straight to the one who asked, and one gentle reminder goes to the borrower 3 days
// after the return date, in the task of 18 h of the coups de main. No money, ever.

import { FieldValue, Timestamp, type Firestore } from 'firebase-admin/firestore';
import { formatDay, parisDay, type Notice } from './logic';
import { deliver } from './prefs';
import type { Sender } from './store';

export type LendStatus = 'available' | 'asked' | 'lent';

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
 * What a change of a thing tells: an ask to the owner, « D'accord » or « Pas cette fois » to the
 * one who asked. A dropped ask (the borrower) and « Rendu » tell nobody.
 */
export async function onLendItemUpdated(
  db: Firestore, send: Sender, itemId: string, before: LendItemData, after: LendItemData,
  actorUid: string | undefined, now: Date,
): Promise<void> {
  if (before.status === 'available' && after.status === 'asked' && after.borrowerId) {
    await deliver(db, send, [after.ownerId], askNotice(itemId, after), now);
  } else if (before.status === 'asked' && after.status === 'lent' && after.borrowerId) {
    await deliver(db, send, [after.borrowerId], acceptNotice(itemId, after), now);
  } else if (before.status === 'asked' && after.status === 'available' && before.borrowerId) {
    const actor = await memberOfUid(db, actorUid);
    if (actor !== null && actor === before.borrowerId) return;
    await deliver(db, send, [before.borrowerId], declineNotice(itemId, before), now);
  }
}

/**
 * The things lent whose return date passed 3 days ago and not yet reminded: one gentle reminder
 * to the borrower, then remindedAt so it never comes again. Returns the number of reminders.
 */
export async function sendLendReminders(db: Firestore, send: Sender, now: Date): Promise<number> {
  const limit = now.getTime() - REMIND_AFTER_DAYS * DAY_MS;
  const lent = await db.collection('lendItems').where('status', '==', 'lent').get();
  let told = 0;
  for (const doc of lent.docs) {
    const item = doc.data() as LendItemData;
    if (item.remindedAt || !item.until || !item.borrowerId || item.until.toMillis() > limit) continue;
    await deliver(db, send, [item.borrowerId], reminderNotice(doc.id, item), now);
    await doc.ref.update({ remindedAt: Timestamp.fromDate(now) });
    told++;
  }
  return told;
}

/**
 * A member removed from the family: their things go (the photos follow by onLendItemDeleted),
 * and a thing they asked for or had is free again. Returns the number of things changed.
 */
export async function releaseLendItemsOf(db: Firestore, memberId: string): Promise<number> {
  const owned = await db.collection('lendItems').where('ownerId', '==', memberId).get();
  const borrowed = await db.collection('lendItems').where('borrowerId', '==', memberId).get();
  const batch = db.batch();
  for (const doc of owned.docs) batch.delete(doc.ref);
  const free = {
    status: 'available', borrowerId: null, borrowerName: null, from: null, until: null,
    askedAt: null, lentAt: null, remindedAt: null, updatedAt: FieldValue.serverTimestamp(),
  };
  const released = borrowed.docs.filter((d) => d.get('ownerId') !== memberId);
  for (const doc of released) batch.update(doc.ref, free);
  await batch.commit();
  return owned.size + released.length;
}
