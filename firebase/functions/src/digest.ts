// Le résumé quotidien d'engagement (PRD, carte AVIS-14). The notices of the souvenirs, of the
// challenge, of the question of the week and of the leaves go out as one notification a day at
// most, on the `weekly` channel: « Aujourd'hui dans Familia : nouveau souvenir, défi à 60 %,
// question de la semaine ». The events, the Bonnes nouvelles, the birthday wishes and « Rejoignez-moi »
// stay instant, and so does the very first souvenir of each member. While the module `digest` is
// off, everything is sent at once, as before. With the résumé du soir on (récit 70), it leaves
// from 19 h instead, with the small news of the day.
//
// engagementDigests/{memberId} holds what is waiting for one member; only the functions use it.

import { FieldValue, type Firestore } from 'firebase-admin/firestore';
import { parisDay, type Notice } from './logic';
import { EVENING_HOUR, eveningDigestOn } from './prefs';
import { featureOn, notify, type Sender } from './store';

/** From this hour in Paris, the day's résumé leaves (run by the task of every 30 minutes). */
export const DIGEST_HOUR = 18;

export type DigestItem =
  | { kind: 'souvenir'; key: string }
  | { kind: 'challengeDone'; month: string }
  | { kind: 'weekly'; weekId: string }
  // The day after an event, those who came are invited to add their photos (récit 41).
  | { kind: 'album'; eventId: string };

/** Whether the notices wait for the résumé (Administration > Modules > « Résumé du jour »). */
export async function digestOn(db: Firestore): Promise<boolean> {
  return featureOn(db, 'digest');
}

/** Puts [item] in the résumé of each of [memberIds]. The caller has checked their settings. */
export async function queueDigest(db: Firestore, memberIds: string[], item: DigestItem, now: Date): Promise<void> {
  const fields: Record<string, unknown> =
    item.kind === 'souvenir' ? { souvenirs: FieldValue.arrayUnion(item.key) }
      : item.kind === 'challengeDone' ? { challengeDone: item.month }
        : item.kind === 'album' ? { albums: FieldValue.arrayUnion(item.eventId) }
          : { weekly: item.weekId };
  for (let i = 0; i < memberIds.length; i += 400) {
    const batch = db.batch();
    for (const id of memberIds.slice(i, i + 400)) {
      batch.set(db.collection('engagementDigests').doc(id), { ...fields, pending: true, updatedAt: now }, { merge: true });
    }
    await batch.commit();
  }
}

/** The family's gauge of the month and leaves, read once for every résumé of the run. */
export interface FamilyNews {
  /** The gauge of the challenge in progress, in %, or null (none, achieved, or the tree is off). */
  challengePercent: number | null;
  leaves: number;
}

export async function familyNews(db: Firestore, now: Date): Promise<FamilyNews> {
  const [config, tree, challenge] = await Promise.all([
    db.doc('config/app').get(),
    db.doc('familyTree/current').get(),
    db.doc(`challenges/${parisDay(now).slice(0, 7)}`).get(),
  ]);
  const treeOn = config.get('features.tree') === true;
  const target = Number(challenge.get('target') ?? 0);
  const percent = treeOn && challenge.exists && !challenge.get('achievedAt') && target > 0
    ? Math.floor((100 * Number(challenge.get('progress') ?? 0)) / target)
    : null;
  return { challengePercent: percent, leaves: treeOn ? Number(tree.get('leaves') ?? 0) : 0 };
}

/**
 * The sentence of one résumé. What is waiting comes first; the gauge and the new leaves only go
 * with it, so that the family never gets a notification for the gauge alone. Null when nothing waits.
 */
export function digestNotice(
  waiting: { souvenirs: string[]; challengeDone: boolean; weekly: string | null; albums?: string[] },
  news: FamilyNews,
  newLeaves: number,
): Notice | null {
  const parts: string[] = [];
  const albums = waiting.albums ?? [];
  if (albums.length === 1) parts.push('tes photos d\'hier à mettre dans l\'album');
  if (albums.length > 1) parts.push('tes photos d\'hier à mettre dans les albums');
  if (waiting.souvenirs.length === 1) parts.push('nouveau souvenir');
  if (waiting.souvenirs.length > 1) parts.push(`${waiting.souvenirs.length} nouveaux souvenirs`);
  if (waiting.challengeDone) parts.push('défi réussi, l\'arbre fleurit');
  if (waiting.weekly) parts.push('question de la semaine');
  if (parts.length === 0) return null;
  if (!waiting.challengeDone && news.challengePercent !== null) parts.push(`défi à ${news.challengePercent} %`);
  if (newLeaves > 0) parts.push(newLeaves === 1 ? '1 nouvelle feuille' : `${newLeaves} nouvelles feuilles`);
  return {
    channel: 'weekly',
    title: 'Aujourd\'hui dans Familia',
    body: parts.join(', ').replace(/^./, (c) => c.toUpperCase()) + '.',
    ...(waiting.weekly && parts.length === 1 ? { weeklyQuestionId: waiting.weekly } : {}),
    // The album alone: a tap opens its event, where the album is.
    ...(albums.length === 1 && parts.length === 1 ? { eventId: albums[0] } : {}),
  };
}

/**
 * From [DIGEST_HOUR] (from [EVENING_HOUR] with the résumé du soir), sends each résumé that waits, once a day. A member's résumé is emptied
 * before it is sent, so two close runs never send it twice. Returns how many members were told.
 */
export async function sendDigests(db: Firestore, send: Sender, now: Date): Promise<number> {
  const hour = Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Paris', hour: '2-digit', hourCycle: 'h23' }).format(now));
  if (hour < DIGEST_HOUR) return 0;
  // Résumé du soir (récit 70): the résumé of the day waits for 19 h too.
  if (hour < EVENING_HOUR && (await eveningDigestOn(db))) return 0;
  const waiting = await db.collection('engagementDigests').where('pending', '==', true).get();
  if (waiting.empty) return 0;
  const today = parisDay(now);
  const news = await familyNews(db, now);
  let told = 0;
  for (const doc of waiting.docs) {
    const taken = await db.runTransaction(async (t) => {
      const d = await t.get(doc.ref);
      if (!d.get('pending') || d.get('sentDay') === today) return null;
      t.set(doc.ref, {
        pending: false, sentDay: today, souvenirs: [], challengeDone: null, weekly: null, albums: [],
        leavesSeen: news.leaves, updatedAt: now,
      }, { merge: true });
      return d;
    });
    if (!taken) continue;
    const seen = taken.get('leavesSeen');
    const notice = digestNotice({
      souvenirs: (taken.get('souvenirs') as string[] | undefined) ?? [],
      challengeDone: Boolean(taken.get('challengeDone')),
      weekly: (taken.get('weekly') as string | null | undefined) ?? null,
      albums: (taken.get('albums') as string[] | undefined) ?? [],
    }, news, typeof seen === 'number' ? Math.max(0, news.leaves - seen) : 0);
    if (notice && (await notify(db, send, [doc.id], notice, now)) > 0) told++;
  }
  return told;
}

/** A member who leaves takes their résumé with them. */
export async function deleteDigestOf(db: Firestore, memberId: string): Promise<void> {
  await db.collection('engagementDigests').doc(memberId).delete();
}
