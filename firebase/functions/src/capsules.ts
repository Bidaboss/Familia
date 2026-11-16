// Capsule temporelle (PRD, récit 39): a capsule stays sealed until its day (openAt, in Paris).
// From then on, the 30-minute task copies its recipients into openedFor, which lets them read it
// (firestore.rules, storage.rules), and tells each of them once. Nothing else is ever written.

import type { Firestore } from 'firebase-admin/firestore';
import { Notice, parisDay } from './logic';
import { notify, Sender } from './store';

export interface CapsuleData {
  recipientIds: string[];
  openAt: string;
  openedFor: string[];
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
  const due = await db.collection('capsules').where('openAt', '<=', today).get();
  const opened: string[] = [];
  for (const snap of due.docs) {
    // Already opened ones come back every run: skip them before any transaction (relecture #94, C1).
    if ((snap.get('openedFor') ?? []).length > 0) continue;
    const ref = snap.ref;
    const capsule = await db.runTransaction(async (tx) => {
      const fresh = await tx.get(ref);
      const data = fresh.data() as CapsuleData | undefined;
      if (!data || !isDue(data, today)) return null;
      tx.update(ref, { openedFor: data.recipientIds, openedAt: now });
      return data;
    });
    if (!capsule) continue;
    opened.push(ref.id);
    await notify(db, send, capsule.recipientIds, capsuleNotice(ref.id, capsule.authorName));
  }
  return opened;
}
