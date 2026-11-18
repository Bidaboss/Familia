// Le défi lancé par un foyer, à tour de rôle (PRD, récit 35 bis). Fictional family from helpers.mjs.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, SECOND_ADMIN, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };

// The rules know the month from the clock: these tests follow it.
function monthAt(offset) {
  const now = new Date();
  const d = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + offset, 1));
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
}
const THIS_MONTH = monthAt(0);
const NEXT_MONTH = monthAt(1);

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: [ADULT.memberId] }));
    // h1 this month, h2 the next one.
    await setDoc(doc(db, 'challengeRotation/current'), { householdIds: ['h1', 'h2'], startMonth: THIS_MONTH, updatedAt: new Date() });
  });
});

function challenge(by, overrides = {}) {
  return {
    householdId: 'h1', status: 'draft', kind: 'photo', title: '15 photos de nos balades', emoji: '📸',
    illustration: 'camera', target: 15, updatedBy: by.memberId, updatedAt: serverTimestamp(), ...overrides,
  };
}

const write = (who, month, data) => setDoc(doc(as(env, who), `householdChallenges/${month}`), data);

describe('the order of the foyers', () => {
  test('the family reads it, only the admin principal sets it', async () => {
    await assertSucceeds(getDoc(doc(as(env, KID), 'challengeRotation/current')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'challengeRotation/current')));
    const set = (who, data) => setDoc(doc(as(env, who), 'challengeRotation/current'), data);
    const order = { householdIds: ['h2', 'h1'], startMonth: '2027-01', updatedAt: serverTimestamp() };
    await assertSucceeds(set(ADMIN, order));
    await assertFails(set(SECOND_ADMIN, order));
    await assertFails(set(ADULT, order));
    await assertFails(set(ADMIN, { ...order, householdIds: [] }));
    await assertFails(set(ADMIN, { ...order, startMonth: '2027-13' }));
    await assertFails(set(ADMIN, { ...order, updatedAt: new Date('2020-01-01') }));
    await assertFails(set(ADMIN, { ...order, extra: 1 }));
    await assertFails(setDoc(doc(as(env, ADMIN), 'challengeRotation/other'), order));
  });
});

describe('the challenge of a foyer', () => {
  test('the whole foyer prepares it, children included; the family reads it', async () => {
    await assertSucceeds(write(KID, THIS_MONTH, challenge(KID)));
    await assertSucceeds(write(ADULT, THIS_MONTH, challenge(ADULT, { title: '20 photos', target: 20 })));
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), `householdChallenges/${THIS_MONTH}`)));
    await assertFails(getDoc(doc(as(env, STRANGER), `householdChallenges/${THIS_MONTH}`)));
  });

  test('only an adult publishes it or passes the turn', async () => {
    await assertFails(write(KID, THIS_MONTH, challenge(KID, { status: 'published' })));
    await assertFails(write(KID, THIS_MONTH, challenge(KID, { status: 'skipped' })));
    await assertSucceeds(write(KID, THIS_MONTH, challenge(KID)));
    await assertSucceeds(write(SECOND_ADMIN, THIS_MONTH, challenge(SECOND_ADMIN, { status: 'skipped' })));
    await assertFails(write(ADULT, THIS_MONTH, challenge(ADULT, { status: 'published' })));
  });

  test('the optional word of the foyer (récit 65): a string of 140 characters at most', async () => {
    await assertSucceeds(write(KID, THIS_MONTH, challenge(KID, { note: 'Une photo de vos mains en train de cuisiner.' })));
    await assertSucceeds(write(KID, THIS_MONTH, challenge(KID, { note: 'x'.repeat(140) })));
    await assertFails(write(KID, THIS_MONTH, challenge(KID, { note: 'x'.repeat(141) })));
    await assertFails(write(KID, THIS_MONTH, challenge(KID, { note: 12 })));
    // Without a word, as before.
    await assertSucceeds(write(KID, THIS_MONTH, challenge(KID)));
  });

  test('an adult publishes the draft of the foyer', async () => {
    await assertSucceeds(write(KID, THIS_MONTH, challenge(KID)));
    await assertSucceeds(write(ADULT, THIS_MONTH, challenge(ADULT, { status: 'published' })));
  });

  test('once published or passed, it no longer changes from the app', async () => {
    await assertSucceeds(write(ADULT, THIS_MONTH, challenge(ADULT, { status: 'published' })));
    await assertFails(write(ADULT, THIS_MONTH, challenge(ADULT, { status: 'draft' })));
    await assertFails(updateDoc(doc(as(env, ADULT), `householdChallenges/${THIS_MONTH}`), { title: 'Autre', updatedBy: ADULT.memberId, updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(as(env, ADULT), `householdChallenges/${THIS_MONTH}`)));
  });

  test('only the foyer whose turn it is, for this month or the next', async () => {
    // h2 has the next month, not this one.
    await assertFails(write(NEIGHBOUR, THIS_MONTH, challenge(NEIGHBOUR, { householdId: 'h2' })));
    await assertFails(write(ADULT, THIS_MONTH, challenge(ADULT, { householdId: 'h2' })));
    await assertSucceeds(write(NEIGHBOUR, NEXT_MONTH, challenge(NEIGHBOUR, { householdId: 'h2' })));
    await assertFails(write(ADULT, NEXT_MONTH, challenge(ADULT)));
    // Too far ahead, before the order starts, or not a month.
    await assertFails(write(ADULT, monthAt(2), challenge(ADULT)));
    await assertFails(write(ADULT, monthAt(-2), challenge(ADULT)));
    await assertFails(write(ADULT, 'novembre', challenge(ADULT)));
  });

  test('a well-formed challenge only', async () => {
    for (const bad of [
      { title: '' }, { title: 'x'.repeat(61) }, { target: 2 }, { target: 51 }, { target: 10.5 },
      { kind: 'sport' }, { illustration: 'moon' }, { status: 'done' }, { emoji: '' },
      { updatedBy: SECOND_ADMIN.memberId }, { updatedAt: new Date('2020-01-01') }, { extra: true },
    ]) {
      await assertFails(write(ADULT, THIS_MONTH, challenge(ADULT, bad)));
    }
  });

  test('without an order, nobody prepares a challenge', async () => {
    await env.withSecurityRulesDisabled((ctx) => deleteDoc(doc(ctx.firestore(), 'challengeRotation/current')));
    await assertFails(write(ADULT, THIS_MONTH, challenge(ADULT)));
  });
});
