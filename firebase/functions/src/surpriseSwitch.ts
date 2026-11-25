// La bascule vers les surprises (plan Cadeaux et Surprise, PR 3; décision de Kévin du 25/11 13 h 27).
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
    if (done.exists) return false;
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
      if (contributions.empty && contributors.empty) continue;
      touched = true;
      result.contributions += contributions.size;
      await db.recursiveDelete(item.ref.collection('contributions'));
      await db.recursiveDelete(item.ref.collection('contributors'));
    }
    if (touched) result.lists += 1;
  }
  await marker.set({ status: 'done', doneAt: FieldValue.serverTimestamp(), ...result });
  console.log(`surpriseReset: ${result.claims} reservations and ${result.contributions} participations wiped in ${result.lists} lists`);
  return result;
}
