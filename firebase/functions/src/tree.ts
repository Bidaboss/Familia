// L'arbre de la famille et le défi du mois (PRD, récit 35). The server alone writes them:
// familyTree/current grows by one leaf for each gesture of a member, at most 5 a day per person,
// and never shrinks. challenges/{AAAA-MM} holds the challenge of the month and its gauge. Nobody
// is ever shown who gave the most (carte EXPERT-3): the server keeps no count per member beyond
// the day's cap, and the day documents are erased with the member.

import { FieldValue, type Firestore } from 'firebase-admin/firestore';
import { addDays, parisDay, type Notice } from './logic';
import { notify, type Sender } from './store';

export const LEAVES_PER_DAY = 5;

/** The visible levels of the tree: the app draws a bigger tree from each one. */
export const TREE_LEVELS = [0, 20, 60, 120, 250, 500, 1000];

export function treeLevel(leaves: number): number {
  return TREE_LEVELS.filter((n) => leaves >= n).length - 1;
}

export type Measure = 'photos' | 'goodNews';

export interface Challenge {
  title: string;
  emoji: string;
  measure: Measure;
  target: number;
}

/** One default challenge per month (01 to 12), always within everyone's reach. */
const DEFAULTS: Record<string, Challenge> = {
  '01': { title: '10 bonnes nouvelles pour bien commencer', emoji: '✨', measure: 'goodNews', target: 10 },
  '02': { title: '15 photos de l\'hiver', emoji: '❄️', measure: 'photos', target: 15 },
  '03': { title: '10 premiers signes du printemps', emoji: '🌱', measure: 'photos', target: 10 },
  '04': { title: '12 bonnes nouvelles du printemps', emoji: '🌷', measure: 'goodNews', target: 12 },
  '05': { title: '15 photos en plein air', emoji: '🌳', measure: 'photos', target: 15 },
  '06': { title: '12 bonnes nouvelles avant l\'été', emoji: '☀️', measure: 'goodNews', target: 12 },
  '07': { title: '20 photos de vacances', emoji: '🏖️', measure: 'photos', target: 20 },
  '08': { title: '20 cartes postales en photo', emoji: '📸', measure: 'photos', target: 20 },
  '09': { title: '10 bonnes nouvelles de la rentrée', emoji: '🎒', measure: 'goodNews', target: 10 },
  '10': { title: '15 couleurs d\'automne', emoji: '🍂', measure: 'photos', target: 15 },
  '11': { title: '12 petits bonheurs', emoji: '🧡', measure: 'goodNews', target: 12 },
  '12': { title: '20 photos de Noël', emoji: '🎄', measure: 'photos', target: 20 },
};

/** The month in Paris, AAAA-MM. */
export function parisMonth(now: Date): string {
  return parisDay(now).slice(0, 7);
}

export function defaultChallenge(month: string): Challenge {
  return DEFAULTS[month.slice(5, 7)];
}

/** Marks and day counts are only needed for a while: the TTL rule on expireAt erases them. */
function expireAt(day: string): Date {
  return new Date(`${addDays(day, 40)}T00:00:00Z`);
}

/** The keys of the gestures, so the same gesture undone and done again, or a trigger run twice, counts once. */
export const treeMark = {
  goodNews: (postId: string) => `goodNews-${postId}`,
  reaction: (postId: string, memberId: string) => `reaction-${postId}-${memberId}`,
  rsvp: (eventId: string, memberId: string) => `rsvp-${eventId}-${memberId}`,
  help: (requestId: string) => `help-${requestId}`,
  arrival: (tripId: string, stepId: string) => `arrival-${tripId}-${stepId}`,
};

/**
 * Adds one leaf for a gesture of [memberId]. A gesture counts once ([markId]); beyond 5 leaves in
 * the day the gesture is noted but adds nothing, to stop cheating. Returns whether a leaf grew.
 */
export async function addLeaf(db: Firestore, memberId: string, markId: string, now: Date): Promise<boolean> {
  const day = parisDay(now);
  const mark = db.collection('treeMarks').doc(markId);
  const dayRef = db.collection('treeDays').doc(`${memberId}_${day}`);
  const tree = db.collection('familyTree').doc('current');
  return db.runTransaction(async (t) => {
    const [seen, today] = await Promise.all([t.get(mark), t.get(dayRef)]);
    if (seen.exists) return false;
    t.create(mark, { memberId, day, expireAt: expireAt(day) });
    const count = Number(today.get('count') ?? 0);
    if (count >= LEAVES_PER_DAY) return false;
    t.set(dayRef, { memberId, day, count: count + 1, expireAt: expireAt(day) });
    t.set(tree, { leaves: FieldValue.increment(1), updatedAt: now }, { merge: true });
    return true;
  });
}

/** The challenge of [month], created from the default the first time it is needed. */
export async function ensureChallenge(db: Firestore, month: string, now: Date): Promise<void> {
  const ref = db.collection('challenges').doc(month);
  try {
    await ref.create({ ...defaultChallenge(month), month, progress: 0, achievedAt: null, source: 'default', createdAt: now });
  } catch (e) {
    if ((e as { code?: number }).code !== 6) throw e; // ALREADY_EXISTS: it is there.
  }
}

/**
 * A good news published in the month moves the gauge: by its photos, or by one post. Counted once
 * per post. When the target is reached, the challenge is achieved once and the tree blooms.
 * Returns the challenge that was just achieved, or null.
 */
export async function countForChallenge(
  db: Firestore, postId: string, photoCount: number, now: Date,
): Promise<(Challenge & { month: string }) | null> {
  const month = parisMonth(now);
  await ensureChallenge(db, month, now);
  const ref = db.collection('challenges').doc(month);
  const mark = db.collection('treeMarks').doc(`challenge-${postId}`);
  const tree = db.collection('familyTree').doc('current');
  return db.runTransaction(async (t) => {
    const [doc, seen] = await Promise.all([t.get(ref), t.get(mark)]);
    if (seen.exists || doc.get('achievedAt')) return null;
    const c = doc.data() as Challenge & { progress: number };
    const amount = c.measure === 'photos' ? photoCount : 1;
    t.create(mark, { memberId: null, day: parisDay(now), expireAt: expireAt(parisDay(now)) });
    if (amount <= 0) return null;
    const progress = Math.min(c.target, c.progress + amount);
    const achieved = progress >= c.target;
    t.update(ref, { progress, ...(achieved ? { achievedAt: now } : {}) });
    if (!achieved) return null;
    t.set(tree, { bloomedMonths: FieldValue.arrayUnion(month), updatedAt: now }, { merge: true });
    return { ...c, month };
  });
}

export function challengeNotice(c: Challenge): Notice {
  return { channel: 'tree', title: 'Défi réussi !', body: `${c.emoji} ${c.title} : bravo à toute la famille, l'arbre fleurit.` };
}

/** The module is on for the family: until then the tree grows quietly, and nothing is told or posted. */
async function treeOn(db: Firestore): Promise<boolean> {
  return (await db.doc('config/app').get()).get('features.tree') === true;
}

/**
 * A challenge achieved: everyone with the app is told (except those who turned `tree` off), and a
 * Bonne nouvelle « Défi réussi » is published by Familia, once (goodNews/challenge-{month}). It
 * names nobody: the challenge belongs to the whole family.
 */
export async function celebrateChallenge(db: Firestore, send: Sender, c: Challenge & { month: string }, now: Date): Promise<boolean> {
  if (!(await treeOn(db))) return false;
  try {
    await db.collection('goodNews').doc(`challenge-${c.month}`).create({
      text: `${c.emoji} Défi réussi : ${c.title}. Bravo à toute la famille !`,
      photoIds: [], authorId: 'familia', authorName: 'Familia', forMemberId: 'familia', householdId: null,
      mentionIds: [], reactions: {}, hiddenByIds: [], challengeMonth: c.month, createdAt: now, updatedAt: now,
    });
  } catch (e) {
    if ((e as { code?: number }).code === 6) return false; // Celebrated before.
    throw e;
  }
  const members = await db.collection('members').get();
  const withApp = members.docs.filter((m) => m.get('profile') !== 'childWithoutApp').map((m) => m.id);
  if (withApp.length === 0) return true;
  const settings = await db.getAll(...withApp.map((id) => db.doc(`members/${id}/settings/notifications`)));
  const muted = new Set(settings.filter((s) => s.exists && s.get('tree') === false).map((s) => s.ref.parent.parent!.id));
  await notify(db, send, withApp.filter((id) => !muted.has(id)), challengeNotice(c));
  return true;
}

/** A member who leaves takes their marks and day counts with them (carte EXPERT-1); the leaves stay the family's. */
export async function deleteTreeDataOf(db: Firestore, memberId: string): Promise<void> {
  const [marks, days] = await Promise.all([
    db.collection('treeMarks').where('memberId', '==', memberId).get(),
    db.collection('treeDays').where('memberId', '==', memberId).get(),
  ]);
  const docs = [...marks.docs, ...days.docs];
  for (let i = 0; i < docs.length; i += 400) {
    const batch = db.batch();
    docs.slice(i, i + 400).forEach((d) => batch.delete(d.ref));
    await batch.commit();
  }
}
