// « Documents » become private by default (DESIGN-105, décision de l'admin du 24/11): each one lists who reads it in
// sharedWith. Those written before stay shared with everyone who goes, as they were: they get
// sharedWith = the participants of their trip (their owner always in it). Done once, a little
// after the rules are published; migrations/documentsShared marks it, with how many changed.

import type { Firestore } from 'firebase-admin/firestore';

export const DOCUMENTS_SHARED = 'migrations/documentsShared';

/** Who reads an old document: those who go, and its owner (once, first). */
export function oldDocumentSharing(participantIds: unknown, ownerId: unknown): string[] {
  const going = Array.isArray(participantIds) ? participantIds.filter((id): id is string => typeof id === 'string') : [];
  const owner = typeof ownerId === 'string' && ownerId ? [ownerId] : [];
  return [...new Set([...owner, ...going])];
}

/**
 * Gives every document without sharedWith the participants of its trip, once. Returns how many
 * documents changed, or null when already done.
 */
export async function shareOldDocuments(db: Firestore, now: Date): Promise<number | null> {
  const marker = db.doc(DOCUMENTS_SHARED);
  if ((await marker.get()).exists) return null;
  let count = 0;
  for (const trip of (await db.collection('trips').get()).docs) {
    const documents = await trip.ref.collection('documents').get();
    let writer = db.batch();
    let pending = 0;
    for (const document of documents.docs) {
      if (Array.isArray(document.get('sharedWith'))) continue;
      writer.update(document.ref, { sharedWith: oldDocumentSharing(trip.get('participantIds'), document.get('ownerId')) });
      count++;
      pending++;
      if (pending === 400) {
        await writer.commit();
        writer = db.batch();
        pending = 0;
      }
    }
    if (pending > 0) await writer.commit();
  }
  await marker.set({ count, at: now });
  console.log(`documentsShared: ${count} documents shared with those who go`);
  return count;
}
