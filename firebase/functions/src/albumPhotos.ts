// Supprimer une photo d'album (récit 121): a photo of the album of an event or of a trip, removed
// by its author, by a responsible adult of the child who added it, or by the admin principal,
// takes with it everything that was only about it:
// - its two files in Storage, the photo and its thumbnail ({folder}/{authorId}/{slot}.jpg and
//   {slot}-thumb.jpg). The phone of its author removes them first; a responsible adult may not
//   (storage.rules reads no member), so the server always does it too;
// - its place in « Le mot de l'étape » of a trip (récit 88), which lists up to 4 photos by id;
// - the card of the gazette for that album and that day (« album:trips:t1:2026-11-20 »), with its
//   J'aime and comments, when it was the last photo added that day: the card is gone for everyone.
// The album itself (a removed event or trip) cleans up on its own: nothing more is read then.

import { FieldValue, Firestore, Timestamp } from 'firebase-admin/firestore';
import { deleteGazetteThread } from './gazetteThreads';

/** The two kinds of albums this cleans: an event (récit 41) or a trip (récit 15). */
export type AlbumKind = 'events' | 'trips';

/** A photo record as the rules write it: `{folder}/photos/{authorId}_{slot}`. */
export interface AlbumPhotoData {
  authorId?: unknown;
  slot?: unknown;
  createdAt?: unknown;
}

/** Ids of the app and of the rules: letters, digits, « _ » and « - ». */
const SAFE_ID = /^[A-Za-z0-9_-]{1,64}$/;

/**
 * The two files of a removed photo, at its own place only, named after the record; none for a
 * record that does not look like one the rules let through.
 */
export function albumPhotoFiles(kind: AlbumKind, albumId: string, data: AlbumPhotoData | undefined): string[] {
  if (!data || typeof data.authorId !== 'string' || !SAFE_ID.test(data.authorId) || !SAFE_ID.test(albumId)) return [];
  const slot = data.slot;
  if (typeof slot !== 'number' || !Number.isInteger(slot) || slot < 1 || slot > 30) return [];
  const base = `${kind}/${albumId}/${data.authorId}/${slot}`;
  return [`${base}.jpg`, `${base}-thumb.jpg`];
}

/** The day in Paris of [at], as AAAA-MM-JJ: the day of the card of the gazette (FAMILY_ZONE in the app). */
export function parisDay(at: Date): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Paris', year: 'numeric', month: '2-digit', day: '2-digit' }).format(at);
}

function toDate(value: unknown): Date | null {
  return value instanceof Timestamp ? value.toDate() : value instanceof Date ? value : null;
}

/** The thread of the card of the gazette that showed this photo, as the app names it (FeedAlbums.kt). */
export function albumDayThreadId(kind: AlbumKind, albumId: string, data: AlbumPhotoData | undefined): string | null {
  const at = toDate(data?.createdAt);
  return at ? `album:${kind}:${albumId}:${parisDay(at)}` : null;
}

/** What a removed photo takes with it, apart from its files: see the head of this file. */
export async function cleanAfterAlbumPhoto(
  db: Firestore,
  kind: AlbumKind,
  albumId: string,
  photoId: string,
  data: AlbumPhotoData | undefined,
): Promise<void> {
  const album = db.collection(kind).doc(albumId);
  // The whole album is going (its event or its trip removed): its own cleanup does the rest.
  if (!(await album.get()).exists) return;

  if (kind === 'trips') {
    const words = await album.collection('words').where('photoIds', 'array-contains', photoId).get();
    for (const word of words.docs) await word.ref.update({ photoIds: FieldValue.arrayRemove(photoId) });
  }

  const threadId = albumDayThreadId(kind, albumId, data);
  if (!threadId) return;
  const day = threadId.slice(threadId.lastIndexOf(':') + 1);
  const left = await album.collection('photos').select('createdAt').get();
  const sameDay = left.docs.some((d) => {
    const at = toDate(d.get('createdAt'));
    return at !== null && parisDay(at) === day;
  });
  if (!sameDay) await deleteGazetteThread(db, threadId);
}
