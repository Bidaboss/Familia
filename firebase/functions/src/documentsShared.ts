// « Documents » become private by default (DESIGN-105, décision de l'admin du 24/11): each one lists who reads it in
// sharedWith. Those written before stay shared with everyone who goes, as they were: they get
// sharedWith = the participants of their trip (their owner always in it). Done once, a little
// after the rules are published; migrations/documentsShared marks it, with how many changed.

import { FieldValue, type Firestore } from 'firebase-admin/firestore';

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

/**
 * Who stops reading a document when [gone] leave the trip: all of them but its owner, whose own
 * documents stay theirs (they go with the usual deletion rules).
 */
export function readersToDrop(gone: string[], ownerId: unknown): string[] {
  return gone.filter((id) => id !== ownerId);
}

/**
 * Takes those who left the trip off the sharedWith of its documents (relecture de la 598): Firestore
 * already refuses them, and this way Storage, which only reads the document, refuses them too.
 * Returns how many documents changed.
 */
export async function unshareDocuments(db: Firestore, tripId: string, gone: string[]): Promise<number> {
  if (gone.length === 0) return 0;
  const documents = db.collection('trips').doc(tripId).collection('documents');
  const seen = new Set<string>();
  let count = 0;
  // array-contains-any takes at most 30 values.
  for (let start = 0; start < gone.length; start += 30) {
    const found = await documents.where('sharedWith', 'array-contains-any', gone.slice(start, start + 30)).get();
    for (const document of found.docs) {
      if (seen.has(document.id)) continue;
      seen.add(document.id);
      const drop = readersToDrop(gone, document.get('ownerId'));
      const shared = document.get('sharedWith');
      if (!Array.isArray(shared) || !drop.some((id) => shared.includes(id))) continue;
      await document.ref.update({ sharedWith: FieldValue.arrayRemove(...drop) });
      count++;
    }
  }
  return count;
}
