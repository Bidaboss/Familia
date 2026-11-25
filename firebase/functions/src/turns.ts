// « À qui le tour ? » (PRD, récit 58) is removed (l'admin, 20/11): its data is erased once, with his
// agreement. migrations/turnsErased marks that it was done, with how many documents went, so a
// second run does nothing.

import type { DocumentReference, Firestore } from 'firebase-admin/firestore';

export const TURNS_ERASED = 'migrations/turnsErased';

/** Every document under [ref], and [ref] itself when it exists, subcollections included. */
async function countUnder(ref: DocumentReference): Promise<number> {
  let count = (await ref.get()).exists ? 1 : 0;
  for (const sub of await ref.listCollections()) {
    for (const child of await sub.listDocuments()) count += await countUnder(child);
  }
  return count;
}

/**
 * Erases turns/ and everything under it, once: the count is logged first, then kept in
 * migrations/turnsErased. Returns the number of documents erased, or null when already done.
 */
export async function eraseTurns(db: Firestore, now: Date): Promise<number | null> {
  const marker = db.doc(TURNS_ERASED);
  if ((await marker.get()).exists) return null;
  let count = 0;
  for (const turn of await db.collection('turns').listDocuments()) count += await countUnder(turn);
  console.log(`turns: ${count} documents to erase`);
  await db.recursiveDelete(db.collection('turns'));
  await marker.set({ count, at: now });
  console.log(`turns: ${count} documents erased`);
  return count;
}
