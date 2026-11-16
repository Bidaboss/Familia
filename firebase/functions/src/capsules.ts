// Capsule temporelle (PRD, récit 39): a capsule stays sealed until its day (openAt, in Paris).
// From then on, the 30-minute task copies its recipients into openedFor, which lets them read it
// (firestore.rules, storage.rules), and tells each of them once.
// When an author leaves the family, their sealed capsules still open on their day. The admin
// principal sees how many are left (capsuleLeftovers/{memberId}) and may delete them without
// reading them (PRD rév. 184). A removed recipient gets nothing.

import type { Firestore } from 'firebase-admin/firestore';
import { Notice, parisDay } from './logic';
import { notify, Sender } from './store';

export interface CapsuleData {
  recipientIds: string[];
  openAt: string;
  openedFor: string[];
  authorId?: string;
  authorName: string;
}

/** True when [capsule] should open on [today] (AAAA-MM-JJ) and has not been opened yet. */
export function isDue(capsule: CapsuleData, today: string): boolean {
  return capsule.openAt <= today && capsule.openedFor.length === 0;
}

export function capsuleNotice(capsuleId: string, authorName: string): Notice {
  return {
    channel: 'capsules',
    title: 'Une capsule temporelle s’ouvre',
    body: `${authorName} t’a laissé un message pour aujourd’hui.`,
    capsuleId,
  };
}

/** Modules switched on in Administration (config/app.features). */
async function isModuleOn(db: Firestore): Promise<boolean> {
  const config = await db.doc('config/app').get();
  return config.get('features.capsules') === true;
}

/**
 * Opens the capsules whose day has come, and returns their ids. A transaction marks each one, so
 * two close runs never tell the recipients twice. With the module off, capsules wait sealed.
 */
export async function openDueCapsules(db: Firestore, send: Sender, now: Date): Promise<string[]> {
  if (!(await isModuleOn(db))) return [];
  const today = parisDay(now);
  // A single-field range: no composite index. Only the capsules not yet opened go on.
  // Only the sealed ones, by equality: no composite index, and the opened ones are not read again
  // at each run (relecture #104). The day is checked here.
  const sealed = await db.collection('capsules').where('openedFor', '==', []).get();
  const opened: string[] = [];
  for (const snap of sealed.docs) {
    if (!(String(snap.get('openAt')) <= today)) continue;
    const ref = snap.ref;
    const capsule = await db.runTransaction(async (tx) => {
      const fresh = await tx.get(ref);
      const data = fresh.data() as CapsuleData | undefined;
      if (!data || !isDue(data, today)) return null;
      const leftovers = data.authorId ? db.doc(`capsuleLeftovers/${data.authorId}`) : null;
      const left = leftovers ? await tx.get(leftovers) : null;
      tx.update(ref, { openedFor: data.recipientIds, openedAt: now });
      // An opened capsule is no longer one the admin principal may delete: the count follows.
      if (leftovers && left?.exists) {
        const count = Number(left.get('count') ?? 0) - 1;
        if (count > 0) tx.update(leftovers, { count }); else tx.delete(leftovers);
      }
      return data;
    });
    if (!capsule) continue;
    opened.push(ref.id);
    await notify(db, send, capsule.recipientIds, capsuleNotice(ref.id, capsule.authorName));
  }
  return opened;
}

/** The sealed capsules written by [memberId]: equalities only, no composite index. */
function sealedBy(db: Firestore, memberId: string) {
  return db.collection('capsules').where('authorId', '==', memberId).where('openedFor', '==', []);
}

/**
 * When [memberId] leaves the family: counts their sealed capsules, for the admin principal. Nothing
 * is written when there are none. The capsules themselves stay as they are.
 */
export async function countLeftovers(db: Firestore, memberId: string, firstName: string, now: Date): Promise<number> {
  const count = (await sealedBy(db, memberId).count().get()).data().count;
  if (count > 0) {
    await db.doc(`capsuleLeftovers/${memberId}`).set({ memberName: firstName, count, deleteAsked: false, updatedAt: now });
  }
  return count;
}

/**
 * Once the admin principal asked for it (deleteAsked): deletes the sealed capsules of a member who
 * left, unread, then the count. Their photos go with each one (onCapsuleDeleted). If the member is
 * back in the family, their capsules are theirs again and nothing is deleted.
 */
export async function deleteLeftovers(db: Firestore, memberId: string): Promise<number> {
  const ref = db.doc(`capsuleLeftovers/${memberId}`);
  const left = await ref.get();
  if (left.get('deleteAsked') !== true) return 0;
  if ((await db.doc(`members/${memberId}`).get()).exists) {
    await ref.delete();
    return 0;
  }
  const sealed = await sealedBy(db, memberId).get();
  const writer = db.bulkWriter();
  for (const snap of sealed.docs) void writer.delete(snap.ref);
  await writer.close();
  await ref.delete();
  return sealed.size;
}
