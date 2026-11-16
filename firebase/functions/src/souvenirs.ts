// Souvenirs (PRD, récit 34): a fixed list of keepsakes, earned through one's own gestures. The
// server gives them: members/{memberId}/souvenirs/{key}, never written by the app except `shown`.
// No score, no total, no ranking (carte EXPERT-3): a souvenir is private until its member shows it
// on their page. A gift souvenir never tells a reservation, so none comes from the claims.

import { FieldValue, type Firestore } from 'firebase-admin/firestore';
import type { Notice } from './logic';
import { notify, type Sender } from './store';

/** The counted gestures, kept in souvenirCounters/{memberId}, which only the server reads. */
export type Counter = 'reactions' | 'helpGiven' | 'weeklyAnswers';

export interface SouvenirSpec {
  key: string;
  /** The name shown on the souvenir and in the notification. */
  title: string;
  /** Earned when [counter] reaches [threshold]; no counter means earned on the gesture itself. */
  counter?: Counter;
  threshold?: number;
}

/** The list of the PRD. Its keys never change: the app draws each souvenir from its key. */
export const SOUVENIRS: SouvenirSpec[] = [
  { key: 'firstGoodNews', title: 'Première Bonne nouvelle' },
  { key: 'reactions10', title: '10 réactions données', counter: 'reactions', threshold: 10 },
  { key: 'helpGiven5', title: '5 coups de main rendus', counter: 'helpGiven', threshold: 5 },
  { key: 'firstArrival', title: 'Premier « bien arrivé »' },
  { key: 'firstWeeklyAnswer', title: 'Première réponse à la question de la semaine', counter: 'weeklyAnswers', threshold: 1 },
  { key: 'weeklyAnswers10', title: '10 réponses à la question de la semaine', counter: 'weeklyAnswers', threshold: 10 },
  { key: 'oneYear', title: 'Un an dans Familia' },
];

const MS_PER_YEAR = 365 * 24 * 3600 * 1000;

function spec(key: string): SouvenirSpec {
  const s = SOUVENIRS.find((x) => x.key === key);
  if (!s) throw new Error(`Unknown souvenir ${key}`);
  return s;
}

export function souvenirNotice(key: string): Notice {
  return { title: 'Nouveau souvenir', body: `${spec(key).title} : il t'attend sur ta fiche.`, channel: 'souvenirs', souvenirKey: key };
}

/** The module is on for the family: until then souvenirs are kept, but nobody is told. */
async function moduleOn(db: Firestore): Promise<boolean> {
  return (await db.doc('config/app').get()).get('features.souvenirs') === true;
}

/**
 * Gives [key] to [memberId] once: a second call does nothing (create fails on an existing
 * souvenir). Tells the member, unless the module is off, the member has no app or turned it off.
 */
export async function giveSouvenir(db: Firestore, send: Sender, memberId: string, key: string, now: Date): Promise<boolean> {
  spec(key);
  const member = await db.collection('members').doc(memberId).get();
  if (!member.exists) return false;
  try {
    await member.ref.collection('souvenirs').doc(key).create({ key, earnedAt: now, shown: false });
  } catch (e) {
    if ((e as { code?: number }).code === 6) return false; // ALREADY_EXISTS: earned before.
    throw e;
  }
  if (member.get('profile') === 'childWithoutApp' || !(await moduleOn(db))) return true;
  const settings = await member.ref.collection('settings').doc('notifications').get();
  if (settings.get('souvenirs') !== false) await notify(db, send, [memberId], souvenirNotice(key));
  return true;
}

/**
 * Counts one gesture of [memberId] and gives the souvenirs whose threshold it reaches. [markId]
 * names the gesture (souvenirMarks/{markId}), so the same gesture undone and done again, or a
 * trigger run twice, counts once.
 */
export async function countGesture(
  db: Firestore, send: Sender, memberId: string, counter: Counter, markId: string, now: Date,
): Promise<string[]> {
  try {
    await db.collection('souvenirMarks').doc(markId).create({ memberId, at: now });
  } catch (e) {
    if ((e as { code?: number }).code === 6) return [];
    throw e;
  }
  const ref = db.collection('souvenirCounters').doc(memberId);
  await ref.set({ [counter]: FieldValue.increment(1) }, { merge: true });
  const n = Number((await ref.get()).get(counter) ?? 0);
  const given: string[] = [];
  for (const s of SOUVENIRS.filter((x) => x.counter === counter && (x.threshold ?? 0) <= n)) {
    if (await giveSouvenir(db, send, memberId, s.key, now)) given.push(s.key);
  }
  return given;
}

/**
 * Coups de main rendus: once the day of a taken request is past, its volunteer counts one. Run by
 * the daily task of 18 h, for [day]'s requests; souvenirMarks/{requestId} keeps each one counted once.
 */
export async function countHelpGiven(db: Firestore, send: Sender, day: string, now: Date): Promise<number> {
  const taken = await db.collection('helpRequests').where('date', '==', day).where('status', '==', 'taken').get();
  let counted = 0;
  for (const request of taken.docs) {
    const volunteerId = request.get('volunteerId') as string | null;
    if (!volunteerId) continue;
    await countGesture(db, send, volunteerId, 'helpGiven', `help-${request.id}`, now);
    counted++;
  }
  return counted;
}

/** Un an dans Familia: a year after the member document was created. Run once a day. */
export async function giveYearSouvenirs(db: Firestore, send: Sender, now: Date): Promise<string[]> {
  const members = await db.collection('members').get();
  const given: string[] = [];
  for (const m of members.docs) {
    if (now.getTime() - m.createTime.toDate().getTime() < MS_PER_YEAR) continue;
    if (await giveSouvenir(db, send, m.id, 'oneYear', now)) given.push(m.id);
  }
  return given;
}

/** A member removed from the family takes their souvenirs and counters with them. */
export async function deleteSouvenirsOf(db: Firestore, memberId: string): Promise<void> {
  const souvenirs = await db.collection('members').doc(memberId).collection('souvenirs').get();
  const batch = db.batch();
  souvenirs.docs.forEach((s) => batch.delete(s.ref));
  batch.delete(db.collection('souvenirCounters').doc(memberId));
  await batch.commit();
}

/** The souvenirs of the existing triggers, so no new function is needed for them. */
export const souvenirMark = {
  reaction: (postId: string, memberId: string) => `reaction-${postId}-${memberId}`,
  weeklyAnswer: (weekId: string, memberId: string) => `weekly-${weekId}-${memberId}`,
};

/** A real answer (not « Passer ») that was not one before. */
export function isNewWeeklyAnswer(before: { skipped?: boolean } | undefined, after: { skipped?: boolean } | undefined): boolean {
  return after !== undefined && after.skipped === false && (before === undefined || before.skipped !== false);
}
