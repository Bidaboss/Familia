// La bienvenue (PRD, récit 43, idée 6 de l'audit d'engagement) : the first time a member signs in,
// the family is told « Camille vient d'arriver, dis-lui bonjour ». Once per member (welcomes/{id},
// written only here), never for a profile of the foyer de test, and never without a foyer.

import type { Firestore } from 'firebase-admin/firestore';
import type { Notice } from './logic';
import { featureOn, notify, type Sender } from './store';
import { householdKindOf } from './testHousehold';
import { familyMembers } from './audience';

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
  const members = await familyMembers(db);
  const newcomer = members.find((m) => m.id === memberId);
  const firstName = String(newcomer?.get('firstName') ?? '');
  if (!firstName) return 0;
  const others = members.filter((m) => m.id !== memberId && m.get('profile') !== 'childWithoutApp').map((m) => m.id).sort();
  return notify(db, send, others, welcomeNotice(firstName, memberId));
}

/** A member removed takes their welcome with them (RELECTURE on #145). */
export async function deleteWelcomeOf(db: Firestore, memberId: string): Promise<void> {
  await db.collection('welcomes').doc(memberId).delete();
}
