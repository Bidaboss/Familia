// La bienvenue (PRD, récit 43, idée 6 de l'audit d'engagement) : the first time a member signs in,
// the family is told « Camille vient d'arriver, dis-lui bonjour ». Once per member (welcomes/{id},
// written only here), never for a profile of the foyer de test, and never without a foyer.
//
// Avalanche du 21: at most one bienvenue every 30 min. The first arrival is told at once; those who
// come in the next 30 min wait in welcomeQueue/family (only the functions use it), and arrivalReminder
// tells them in one notice, « Inès et 3 autres viennent d'arriver ». During the mode présentation
// every arrival goes to its summary as before, so nothing is counted twice.

import { FieldValue, type Firestore } from 'firebase-admin/firestore';
import type { Notice } from './logic';
import { presentingNow } from './prefs';
import { featureOn, notify, type Sender } from './store';
import { householdKindOf } from './testHousehold';

export const WELCOME = 'welcome';
/** The gRPC code Firestore gives when create() meets an existing document. */
const ALREADY_EXISTS = 6;

/** True when the member document went from no Google account to one: their first sign-in. */
export function isFirstSignIn(before: { uid?: unknown } | undefined, after: { uid?: unknown } | undefined): boolean {
  const had = typeof before?.uid === 'string' && before.uid !== '';
  const has = typeof after?.uid === 'string' && after.uid !== '';
  return !had && has;
}

export function welcomeNotice(firstName: string, memberId: string): Notice {
  return { channel: 'welcome', title: `${firstName} vient d'arriver`, body: 'Dis-lui bonjour !', welcomeOf: memberId };
}

/**
 * Tells the family (every member with the app but the newcomer) that [memberId] arrived. Returns
 * the phones reached, or null when nothing was to be sent: module off, a test or foyer-less
 * profile, or a welcome already given.
 */
export async function welcomeMember(db: Firestore, send: Sender, memberId: string, now: Date): Promise<number | null> {
  if (!(await featureOn(db, WELCOME))) return null;
  if ((await householdKindOf(db, memberId)) !== 'real') return null;
  try {
    await db.collection('welcomes').doc(memberId).create({ at: now });
  } catch (e) {
    if ((e as { code?: number }).code === ALREADY_EXISTS) return null;
    throw e;
  }
  if (!(await presenting(db, now)) && !(await welcomeNow(db, memberId, now))) return 0;
  const members = await db.collection('members').get();
  const newcomer = members.docs.find((m) => m.id === memberId);
  const firstName = String(newcomer?.get('firstName') ?? '');
  if (!firstName) return 0;
  const others = members.docs.filter((m) => m.id !== memberId && m.get('profile') !== 'childWithoutApp').map((m) => m.id).sort();
  return notify(db, send, others, welcomeNotice(firstName, memberId));
}

/** At most one bienvenue in this time. */
export const WELCOME_GAP_MS = 30 * 60 * 1000;
const WELCOME_QUEUE = 'welcomeQueue/family';

async function presenting(db: Firestore, now: Date): Promise<boolean> {
  try {
    return await presentingNow(db, now);
  } catch (e) {
    console.error('presentation: read failed', e);
    return false;
  }
}

/** True when [memberId] may be told at once; otherwise they wait in the queue for arrivalReminder. */
async function welcomeNow(db: Firestore, memberId: string, now: Date): Promise<boolean> {
  const ref = db.doc(WELCOME_QUEUE);
  return db.runTransaction(async (tx) => {
    const doc = await tx.get(ref);
    const last = (doc.get('lastSentAt') as { toDate?: () => Date } | undefined)?.toDate?.();
    const pending = (doc.get('pending') as string[] | undefined) ?? [];
    if (pending.length === 0 && (!last || now.getTime() - last.getTime() >= WELCOME_GAP_MS)) {
      tx.set(ref, { lastSentAt: now, pending: [] });
      return true;
    }
    tx.set(ref, { pending: FieldValue.arrayUnion(memberId) }, { merge: true });
    return false;
  });
}

/** « Inès et Sam viennent d'arriver », « Inès, Sam et Noé… », « Inès et 3 autres… ». One name: as [welcomeNotice]. */
export function welcomeGroupNotice(names: string[], ids: string[]): Notice & { welcomeNames: string[] } {
  if (names.length === 1) return { ...welcomeNotice(names[0], ids[0]), welcomeNames: names };
  const who = names.length <= 3
    ? `${names.slice(0, -1).join(', ')} et ${names[names.length - 1]}`
    : `${names[0]} et ${names.length - 1} autres`;
  return { channel: 'welcome', title: `${who} viennent d'arriver`, body: 'Dis-leur bonjour !', welcomeNames: names };
}

/**
 * From arrivalReminder: once 30 min have passed since the last bienvenue, those who waited are told
 * in one notice, each newcomer hearing of the others only. Returns the phones reached.
 */
export async function sendQueuedWelcomes(db: Firestore, send: Sender, now: Date): Promise<number> {
  const ref = db.doc(WELCOME_QUEUE);
  const pending = await db.runTransaction(async (tx) => {
    const doc = await tx.get(ref);
    const last = (doc.get('lastSentAt') as { toDate?: () => Date } | undefined)?.toDate?.();
    const ids = (doc.get('pending') as string[] | undefined) ?? [];
    if (ids.length === 0 || (last && now.getTime() - last.getTime() < WELCOME_GAP_MS)) return [];
    tx.set(ref, { lastSentAt: now, pending: [] });
    return ids;
  });
  if (pending.length === 0 || !(await featureOn(db, WELCOME))) return 0;
  const members = await db.collection('members').get();
  const byId = new Map(members.docs.map((m) => [m.id, m]));
  const newcomers = pending
    .map((id) => ({ id, name: String(byId.get(id)?.get('firstName') ?? '') }))
    .filter((n) => n.name !== '');
  const groups = new Map<string, { names: string[]; ids: string[]; to: string[] }>();
  for (const m of members.docs) {
    if (m.get('profile') === 'childWithoutApp') continue;
    const others = newcomers.filter((n) => n.id !== m.id);
    if (others.length === 0) continue;
    const key = others.map((n) => n.id).join(',');
    const group = groups.get(key) ?? { names: others.map((n) => n.name), ids: others.map((n) => n.id), to: [] };
    group.to.push(m.id);
    groups.set(key, group);
  }
  let sent = 0;
  for (const g of groups.values()) sent += await notify(db, send, g.to.sort(), welcomeGroupNotice(g.names, g.ids), now);
  return sent;
}

/** A member removed takes their welcome with them (RELECTURE on #145). */
export async function deleteWelcomeOf(db: Firestore, memberId: string): Promise<void> {
  await db.collection('welcomes').doc(memberId).delete();
}
