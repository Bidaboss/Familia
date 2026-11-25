// La gazette (récits 115, 116): gazetteThreads/{threadId} holds the J'aime of a card of the gazette
// and, under it, its comments. The phone writes the J'aime and the comments; the server alone keeps
// what the card shows of the comments: how many (commentCount) and the 2 latest (last), so a card
// reads one document, never the whole conversation (brief §9, « ne charger les commentaires complets
// qu'à l'ouverture du détail »). The notifications of the comments: gazetteNotices.ts.

import { FieldPath, Firestore, Timestamp } from 'firebase-admin/firestore';

/** What a card shows of one of the latest comments: who, what, when. */
export interface LastComment {
  id: string;
  authorId: string;
  authorName: string;
  text: string;
  createdAt: Timestamp | null;
}

/** The 2 latest comments are kept on the thread. */
export const LAST_SHOWN = 2;

/** A comment of the card is cut here: the card shows 2 lines at most (brief §4). */
export const LAST_TEXT_MAX = 200;

/** Up to [LAST_TEXT_MAX] characters of [text], on one line. */
export function lastText(text: string): string {
  const line = text.replace(/\s+/g, ' ').trim();
  return line.length > LAST_TEXT_MAX ? `${line.slice(0, LAST_TEXT_MAX - 1)}…` : line;
}

/**
 * A comment was added or removed: the count and the 2 latest are read again from the comments
 * themselves, so a trigger that runs twice (Cloud Functions may replay an event) leaves them right.
 * The thread is written only when they changed; a thread without any J'aime nor comment left is
 * kept (its J'aime may come back), with a count of 0.
 */
export async function refreshGazetteThread(db: Firestore, threadId: string): Promise<void> {
  const ref = db.collection('gazetteThreads').doc(threadId);
  await db.runTransaction(async (tx) => {
    const thread = await tx.get(ref);
    const count = (await tx.get(ref.collection('comments').count())).data().count;
    const latest = await tx.get(ref.collection('comments').orderBy('createdAt', 'desc').limit(LAST_SHOWN));
    const last: LastComment[] = latest.docs.reverse().map((d) => ({
      id: d.id,
      authorId: String(d.get('authorId') ?? ''),
      authorName: String(d.get('authorName') ?? ''),
      text: lastText(String(d.get('text') ?? '')),
      createdAt: (d.get('createdAt') as Timestamp | undefined) ?? null,
    }));
    if (!thread.exists && count === 0) return;
    const same = thread.exists
      && thread.get('commentCount') === count
      && JSON.stringify(thread.get('last') ?? []) === JSON.stringify(last);
    if (same) return;
    tx.set(ref, { commentCount: count, last }, { merge: true });
  });
}

/** A card that no longer exists (a publication removed): its J'aime and its comments go with it. */
export async function deleteGazetteThread(db: Firestore, threadId: string): Promise<void> {
  const ref = db.collection('gazetteThreads').doc(threadId);
  await db.recursiveDelete(ref.collection('comments'));
  await ref.delete();
}


/**
 * The threads whose id starts with [prefix] (« album:trips:t1: », every day of the album of a trip)
 * go with their J'aime and their comments. Returns how many went.
 */
export async function deleteGazetteThreadsFrom(db: Firestore, prefix: string): Promise<number> {
  const found = await db.collection('gazetteThreads')
    .where(FieldPath.documentId(), '>=', prefix)
    .where(FieldPath.documentId(), '<', `${prefix}\uf8ff`)
    .select().get();
  for (const d of found.docs) await deleteGazetteThread(db, d.id);
  return found.size;
}

/**
 * The cards that go when their thing goes (relecture du renfort sur #685, C2): a removed event,
 * recipe, coup de main, bon plan, trip, film or publication (récit 121) takes the thread of its card with it, so its
 * comments do not stay readable by the family.
 */
export async function deleteGazetteThreadsOfThing(
  db: Firestore,
  thing: { event: string } | { recipe: string } | { help: string } | { deal: string } | { trip: string } | { film: string; trip: string } | { post: string },
): Promise<void> {
  if ('post' in thing) {
    await deleteGazetteThread(db, `post:${thing.post}`);
  } else if ('film' in thing) {
    await deleteGazetteThread(db, `film:${thing.trip}:${thing.film}`);
  } else if ('trip' in thing) {
    await deleteGazetteThreadsFrom(db, `album:trips:${thing.trip}:`);
    await deleteGazetteThreadsFrom(db, `film:${thing.trip}:`);
  } else if ('event' in thing) {
    await deleteGazetteThread(db, `event:${thing.event}`);
    await deleteGazetteThreadsFrom(db, `album:events:${thing.event}:`);
  } else if ('recipe' in thing) {
    await deleteGazetteThread(db, `recipe:${thing.recipe}`);
  } else if ('help' in thing) {
    await deleteGazetteThread(db, `help:${thing.help}`);
  } else {
    await deleteGazetteThread(db, `deal:${thing.deal}`);
  }
}
