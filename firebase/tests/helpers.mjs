// Shared setup for the security rules tests: a fresh emulator database seeded with a small
// fictional family (no real names or addresses).
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, setDoc } from 'firebase/firestore';

export async function createEnv() {
  return initializeTestEnvironment({
    projectId: 'demo-familia',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
}

export const ADULT = { memberId: 'adult1', email: 'adult1@example.com', uid: 'uid-adult1' };
export const OTHER_ADULT = { memberId: 'adult2', email: 'adult2@example.com', uid: 'uid-adult2' };
export const ADMIN = { memberId: 'admin1', email: 'admin1@example.com', uid: 'uid-admin1' };
// ADMIN is the admin principal (config/family); SECOND_ADMIN is a chef de famille of h1.
export const SECOND_ADMIN = { memberId: 'admin2', email: 'admin2@example.com', uid: 'uid-admin2' };
// The only member of household h2: out of reach of the chefs of h1.
export const NEIGHBOUR = { memberId: 'adult3', email: 'adult3@example.com', uid: 'uid-adult3' };
export const STRANGER = { email: 'stranger@example.com', uid: 'uid-stranger' };

export async function seed(env) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'households/h1'), { name: 'Foyer 1' });
    await setDoc(doc(db, 'households/h2'), { name: 'Foyer 2' });
    await setDoc(doc(db, 'config/family'), { ownerMemberId: ADMIN.memberId });
    for (const m of [ADULT, OTHER_ADULT, ADMIN, SECOND_ADMIN]) {
      await setDoc(doc(db, `memberEmails/${m.email}`), { memberId: m.memberId });
      await setDoc(doc(db, `members/${m.memberId}`), adultDoc(m, { isAdmin: m === ADMIN || m === SECOND_ADMIN }));
    }
    await setDoc(doc(db, `memberEmails/${NEIGHBOUR.email}`), { memberId: NEIGHBOUR.memberId });
    await setDoc(doc(db, `members/${NEIGHBOUR.memberId}`), adultDoc(NEIGHBOUR, { householdId: 'h2' }));
  });
}

/** A Firestore client signed in as a Google account. */
export function as(env, { uid, email }, { verified = true } = {}) {
  return env.authenticatedContext(uid, { email, email_verified: verified }).firestore();
}

/** A valid member document for an adult. */
export function adultDoc(m, overrides = {}) {
  return {
    firstName: m.memberId,
    email: m.email,
    birthDate: null,
    householdId: 'h1',
    profile: 'adult',
    isAdmin: false,
    parentIds: [],
    uid: null,
    ...overrides,
  };
}
