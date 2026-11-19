// « Pensez à moi » (PRD, récit 43) is retired (PRODUIT-136, 19/11): nothing starts a moment or sends
// an encouragement any more. Its data (moments/, momentCheers/) stays in the database, closed to new
// writes by the rules; what is here only keeps it tidy when a moment or a member is removed.

import type { Firestore } from 'firebase-admin/firestore';

/** A moment removed (taken back, refused) takes its encouragements with it. */
export async function deleteCheersOf(db: Firestore, momentId: string): Promise<void> {
  const cheers = await db.collection('momentCheers').where('momentId', '==', momentId).get();
  await Promise.all(cheers.docs.map((c) => c.ref.delete()));
}

/** A member removed takes their moments, and the encouragements they wrote or received. */
export async function deleteMomentsOf(db: Firestore, memberId: string): Promise<void> {
  for (const field of ['forMemberId', 'authorId']) {
    const moments = await db.collection('moments').where(field, '==', memberId).get();
    await Promise.all(moments.docs.map((m) => m.ref.delete()));
    const cheers = await db.collection('momentCheers').where(field, '==', memberId).get();
    await Promise.all(cheers.docs.map((c) => c.ref.delete()));
  }
}
