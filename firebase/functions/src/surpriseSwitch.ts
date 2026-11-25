// La bascule vers les surprises (plan Cadeaux et Surprise, PR 3; décision de l'admin du 25/11).
//
// From now on a gift is reserved and shared only inside a surprise. The old system goes:
// - no more reveal the morning after (revealGifts does nothing but this reset);
// - the reservations (giftLists/*/claims, with their parts) and the participations of the
//   « Déjà acheté » wishes (items/*/contributions and contributors) of the old system are wiped,
//   ONCE, without a way back (PRODUIT). The wishes and ideas themselves stay.
//
// The notebook entries of the wiped reservations go with them, through onGiftClaimDeleted; those
// of a gift already revealed stay (« Offert »).

import type { Firestore } from 'firebase-admin/firestore';
import { FieldValue } from 'firebase-admin/firestore';

/** Written once the reset is done: it never runs twice. */
export const RESET_DOC = 'migrations/surpriseReset';

const STALE_MS = 60 * 60 * 1000;

export interface ResetResult {
  lists: number;
  claims: number;
  contributions: number;
}

export async function resetOldReservationsOnce(db: Firestore): Promise<ResetResult | null> {
  const marker = db.doc(RESET_DOC);
  // Claimed first, in a transaction: two runs at once (a retry) never both wipe.
  const mine = await db.runTransaction(async (tx) => {
    const done = await tx.get(marker);
    // A run cut by its time limit stays « running »: after an hour, the next morning starts again (C2 de #630).
    const startedAt = done.get('startedAt') as { toMillis(): number } | undefined;
    const stale = done.get('status') === 'running' && startedAt !== undefined && Date.now() - startedAt.toMillis() > STALE_MS;
    if (done.exists && !stale) return false;
    tx.set(marker, { status: 'running', startedAt: FieldValue.serverTimestamp() });
    return true;
  });
  if (!mine) return null;
  try {
    return await wipe(db);
  } catch (e) {
    // Half done: the next morning starts again; what is gone stays gone.
    await marker.delete();
    throw e;
  }
}

async function wipe(db: Firestore): Promise<ResetResult> {
  const marker = db.doc(RESET_DOC);
  const result: ResetResult = { lists: 0, claims: 0, contributions: 0 };
  const lists = await db.collection('giftLists').get();
  for (const list of lists.docs) {
    const claims = await list.ref.collection('claims').get();
    let touched = claims.size > 0;
    result.claims += claims.size;
    await db.recursiveDelete(list.ref.collection('claims'));
    const items = await list.ref.collection('items').get();
    for (const item of items.docs) {
      const [contributions, contributors] = await Promise.all([
        item.ref.collection('contributions').get(), item.ref.collection('contributors').get(),
      ]);
      const total = item.get('contributedTotal');
      if (contributions.empty && contributors.empty && total === undefined) continue;
      touched = true;
      result.contributions += contributions.size;
      await db.recursiveDelete(item.ref.collection('contributions'));
      await db.recursiveDelete(item.ref.collection('contributors'));
      // No total without its parts: « Déjà acheté » can be unticked again (B1 de #630).
      if (total !== undefined) await item.ref.update({ contributedTotal: FieldValue.delete() });
    }
    if (touched) result.lists += 1;
  }
  await marker.set({ status: 'done', doneAt: FieldValue.serverTimestamp(), ...result });
  console.log(`surpriseReset: ${result.claims} reservations and ${result.contributions} participations wiped in ${result.lists} lists`);
  return result;
}
