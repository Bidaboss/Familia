// Arbre généalogique (PRD, récit 40): kinLinks/{linkId} joins two people of the tree, members or
// kin/{kinId}. When one of them leaves the tree, their links go with them, so the tree never
// points at someone who is gone. A member who leaves also takes their own words (kinWords).

import type { Firestore } from 'firebase-admin/firestore';

/** Deletes every link that touches [personId], and returns how many. Two equalities: no index. */
export async function deleteLinksOf(db: Firestore, personId: string): Promise<number> {
  const links = db.collection('kinLinks');
  const [asA, asB] = await Promise.all([
    links.where('aId', '==', personId).get(),
    links.where('bId', '==', personId).get(),
  ]);
  const writer = db.bulkWriter();
  for (const snap of [...asA.docs, ...asB.docs]) void writer.delete(snap.ref);
  await writer.close();
  return asA.size + asB.size;
}

/** A member removed from the family: their links, and the words they chose for the others. */
export async function removeMemberFromTree(db: Firestore, memberId: string): Promise<void> {
  await deleteLinksOf(db, memberId);
  await db.doc(`kinWords/${memberId}`).delete();
}
