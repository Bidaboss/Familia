// La gazette, les publications (récits 117, 119 et 121): gazettePosts/{postId}. A removed
// publication, by its author, a responsible adult or the admin principal, takes its photos and
// their thumbnails in Storage, and its thread of J'aime and comments, with it.

import { FieldValue, Timestamp } from 'firebase-admin/firestore';
import type { Firestore } from 'firebase-admin/firestore';
import { deleteGazetteThreadsOfThing } from './gazetteThreads';

/** A publication as stored: who wrote it and which photos it shows. */
export interface GazettePostData {
  authorId: string;
  photoIds?: string[];
}

/** The files of the photos of a publication: each photo and its thumbnail. */
export function gazettePostPhotoPaths(postId: string, authorId: string, photoIds: string[]): string[] {
  return photoIds.flatMap((p) => [`gazettePosts/${authorId}/${postId}/${p}.jpg`, `gazettePosts/${authorId}/${postId}/${p}-thumb.jpg`]);
}

export type FileRemover = (paths: string[]) => Promise<void>;

/** onGazettePostDeleted: the photos, then the thread, each tried even if the other fails. */
export async function onGazettePostRemoved(db: Firestore, remove: FileRemover, postId: string, post: GazettePostData | undefined): Promise<void> {
  const results = await Promise.allSettled([
    post && post.authorId ? remove(gazettePostPhotoPaths(postId, post.authorId, post.photoIds ?? [])) : Promise.resolve(),
    deleteGazetteThreadsOfThing(db, { post: postId }),
  ]);
  const failed = results.find((r): r is PromiseRejectedResult => r.status === 'rejected');
  if (failed) throw failed.reason;
}

/** « Dire où je suis » (récit 117): a position is shared 2 h at most. */
export const PLACE_SHARE_MS = 2 * 60 * 60 * 1000;

/**
 * The point of every position whose 2 h are over is erased, its town kept (« était à Lyon »): no
 * position is kept beyond (brief §5). Run by arrivalReminder every 30 minutes, no task of its own.
 * Returns how many were erased.
 */
export async function clearEndedPostPlaces(db: Firestore, now: Date): Promise<number> {
  const live = await db.collection('gazettePosts').where('locLive', '==', true).get();
  let cleared = 0;
  for (const post of live.docs) {
    const created = post.get('createdAt');
    const at = created instanceof Timestamp ? created.toMillis() : 0;
    if (now.getTime() - at < PLACE_SHARE_MS) continue;
    const label = post.get('loc.label');
    await post.ref.update({
      loc: typeof label === 'string' && label.trim() !== '' ? { label } : FieldValue.delete(),
      locLive: FieldValue.delete(),
    });
    cleared++;
  }
  return cleared;
}
