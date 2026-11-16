// Le défi lancé par un foyer (PRD, récit 35 bis, cartes PRODUIT-50 et 51). The foyers launch the
// challenge in turn, a month each, in the order the admin principal set in Administration
// (challengeRotation/current). Seven days before its month, the foyer is told « C'est à vous de
// lancer le défi de décembre ! ». It prepares it in householdChallenges/{AAAA-MM} (the whole foyer,
// children included) and an adult publishes it, or passes its turn without any notice to the
// family. Nothing published on the 1st: the default challenge of the month takes over (tree.ts).

import type { Firestore } from 'firebase-admin/firestore';
import { addDays, parisDay, type Notice } from './logic';
import { featureOn, notify, type Sender } from './store';
import { hostedChallenge, parisMonth } from './tree';

const MONTHS = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];

/** « de décembre », « d'avril », « d'août », « d'octobre ». */
export function ofMonth(month: string): string {
  const name = MONTHS[Number(month.slice(5, 7)) - 1];
  return /^[aeiouéèh]/.test(name) ? `d'${name}` : `de ${name}`;
}

function monthIndex(month: string): number {
  return Number(month.slice(0, 4)) * 12 + Number(month.slice(5, 7)) - 1;
}

/** AAAA-MM after [month]. */
export function nextMonth(month: string): string {
  const i = monthIndex(month) + 1;
  return `${Math.floor(i / 12)}-${String((i % 12) + 1).padStart(2, '0')}`;
}

/** The foyer whose turn [month] is, from challengeRotation/current, or null (no order, or before it starts). */
export async function hostOf(db: Firestore, month: string): Promise<string | null> {
  const rotation = await db.doc('challengeRotation/current').get();
  const ids = rotation.get('householdIds') as string[] | undefined;
  const start = rotation.get('startMonth') as string | undefined;
  if (!ids?.length || !start || month < start) return null;
  return ids[(monthIndex(month) - monthIndex(start)) % ids.length];
}

export function hostNotice(month: string): Notice {
  return {
    channel: 'tree',
    title: `Défi ${ofMonth(month)}`,
    body: `C'est à vous de lancer le défi ${ofMonth(month)} ! Préparez-le ensemble dans l'appli, ou passez votre tour.`,
  };
}

/**
 * Seven days before a month, tells the foyer whose turn it is (its members with the app, except
 * those who turned `tree` off). Not when it has already published or passed, nor while the tree
 * is off. Run by the daily task of 18 h; treeMarks/hostReminder-{month} tells it once.
 */
export async function remindHost(db: Firestore, send: Sender, now: Date): Promise<string | null> {
  const month = nextMonth(parisMonth(now));
  if (parisDay(now) !== addDays(`${month}-01`, -7) || !(await featureOn(db, 'tree'))) return null;
  const householdId = await hostOf(db, month);
  if (!householdId) return null;
  const prepared = await db.collection('householdChallenges').doc(month).get();
  if (['published', 'skipped'].includes(String(prepared.get('status')))) return null;
  try {
    await db.collection('treeMarks').doc(`hostReminder-${month}`).create({ memberId: null, day: parisDay(now), expireAt: new Date(`${addDays(parisDay(now), 40)}T00:00:00Z`) });
  } catch (e) {
    if ((e as { code?: number }).code === 6) return null; // Told before.
    throw e;
  }
  const members = await db.collection('members').where('householdId', '==', householdId).get();
  const withApp = members.docs.filter((m) => m.get('profile') !== 'childWithoutApp').map((m) => m.id);
  if (withApp.length === 0) return householdId;
  const settings = await db.getAll(...withApp.map((id) => db.doc(`members/${id}/settings/notifications`)));
  const muted = new Set(settings.filter((s) => s.exists && s.get('tree') === false).map((s) => s.ref.parent.parent!.id));
  await notify(db, send, withApp.filter((id) => !muted.has(id)), hostNotice(month));
  return householdId;
}

/**
 * A foyer published the challenge of [month]. For the month in progress it replaces the
 * challenge while nobody has moved the gauge, unless the admin principal picked one: their
 * choice stays (« L'admin principal peut remplacer un défi »). For the next month, the
 * challenge is created from it when the month starts. Nothing while the tree is off. Returns
 * whether it is now the challenge.
 */
export async function applyHostedChallenge(db: Firestore, month: string, now: Date): Promise<boolean> {
  if (month !== parisMonth(now) || !(await featureOn(db, 'tree'))) return false;
  const hosted = await hostedChallenge(db, month);
  if (!hosted || (await db.collection('challengePicks').doc(month).get()).exists) return false;
  const ref = db.collection('challenges').doc(month);
  return db.runTransaction(async (t) => {
    const doc = await t.get(ref);
    const fields = { ...hosted, choiceId: null, source: 'household' };
    if (!doc.exists) {
      t.create(ref, { ...fields, month, progress: 0, achievedAt: null, createdAt: now });
      return true;
    }
    if (doc.get('achievedAt') || Number(doc.get('progress') ?? 0) > 0) return false;
    t.update(ref, { ...fields, updatedAt: now });
    return true;
  });
}
