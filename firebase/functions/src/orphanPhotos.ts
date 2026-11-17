// Ménage des photos orphelines (note C de la relecture de #263), en lecture seule pour l'instant.
//
// A good news photo whose send was cancelled, or whose thumbnail failed after the photo itself,
// gets a new id at the next try: the file already sent stays in Storage, linked to nothing. Once a
// week the report lists them in the logs, with their size. It deletes nothing: the deletion
// comes after the 21/11, once the list has been read.

import type { Firestore } from 'firebase-admin/firestore';

/** A file of the bucket, as listed: its path, when it was last written, its size in bytes. */
export interface StoredFile {
  name: string;
  updated: Date;
  size: number;
}

/** A good news as stored: who wrote it and which photos it shows. */
export interface PostPhotos {
  authorId: string;
  photoIds: string[];
}

/**
 * Whether [now] falls on Sunday between 4 h 00 and 4 h 29 in Paris: the run of `arrivalReminder`
 * (every 30 minutes) that makes the report, once a week. No scheduled task of its own: Cloud
 * Scheduler offers only 3 for free.
 */
export function orphanReportDue(now: Date): boolean {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Paris', weekday: 'short', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
    .formatToParts(now);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return get('weekday') === 'Sun' && Number(get('hour')) === 4 && Number(get('minute')) < 30;
}

/** A send in progress writes the photos before the good news: a day leaves it the time to end. */
export const ORPHAN_MIN_AGE_MS = 24 * 60 * 60 * 1000;

const GOOD_NEWS_PHOTO = /^goodNews\/([^/]+)\/([^/]+)\/([^/]+?)(-thumb)?\.jpg$/;

/**
 * The good news photos that no good news shows: its post is gone, or does not list this photo,
 * or was written by someone else. Files younger than [minAgeMs] are left out, and so is any file
 * that does not look like a good news photo.
 */
export function orphanGoodNewsPhotos(
  files: StoredFile[],
  posts: ReadonlyMap<string, PostPhotos>,
  now: Date,
  minAgeMs: number = ORPHAN_MIN_AGE_MS,
): StoredFile[] {
  return files.filter((file) => {
    const match = GOOD_NEWS_PHOTO.exec(file.name);
    if (!match) return false;
    if (now.getTime() - file.updated.getTime() < minAgeMs) return false;
    const [, authorId, postId, photoId] = match;
    const post = posts.get(postId);
    return !post || post.authorId !== authorId || !post.photoIds.includes(photoId);
  });
}

/** Lists the files under a prefix of the bucket. */
export type FileLister = (prefix: string) => Promise<StoredFile[]>;

/** How many paths the log shows; the count and the size cover them all. */
const LOGGED_PATHS = 50;

/**
 * Reads the good news and the photos of Storage, then writes to [log] what would be deleted.
 * Returns the orphans found. Deletes nothing.
 */
export async function reportOrphanGoodNewsPhotos(
  db: Firestore,
  listFiles: FileLister,
  now: Date,
  log: (message: string) => void = console.log,
): Promise<StoredFile[]> {
  const [files, snapshot] = await Promise.all([listFiles('goodNews/'), db.collection('goodNews').get()]);
  const posts = new Map<string, PostPhotos>();
  for (const doc of snapshot.docs) {
    const data = doc.data();
    posts.set(doc.id, {
      authorId: typeof data.authorId === 'string' ? data.authorId : '',
      photoIds: Array.isArray(data.photoIds) ? data.photoIds.filter((p): p is string => typeof p === 'string') : [],
    });
  }
  const orphans = orphanGoodNewsPhotos(files, posts, now);
  const bytes = orphans.reduce((sum, f) => sum + f.size, 0);
  log(`orphanPhotos (lecture seule) : ${orphans.length} photo(s) orpheline(s) sur ${files.length}, ${Math.round(bytes / 1024)} Ko, rien n'est supprimé.`);
  for (const f of orphans.slice(0, LOGGED_PATHS)) log(`orphanPhotos : serait supprimée ${f.name}`);
  if (orphans.length > LOGGED_PATHS) log(`orphanPhotos : et ${orphans.length - LOGGED_PATHS} autre(s).`);
  return orphans;
}
