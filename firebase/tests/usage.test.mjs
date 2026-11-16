// Rules of usage/{memberId}/days/{day}: the time spent in the app (PRD, récit 1 nonies, EXPERT-5).
import { after, before, beforeEach, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { Timestamp, collection, deleteDoc, doc, getDoc, getDocs, setDoc } from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, adultDoc, as, createEnv, seed } from './helpers.mjs';

const CHILD = { memberId: 'child1', email: 'child1@example.com', uid: 'uid-child1' };

let env;

before(async () => {
  env = await createEnv();
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${CHILD.email}`), { memberId: CHILD.memberId });
    await setDoc(doc(db, `members/${CHILD.memberId}`), adultDoc(CHILD, { profile: 'childWithApp', parentIds: [ADULT.memberId] }));
    await setDoc(doc(db, `usage/${CHILD.memberId}/days/2026-11-20`), day('2026-11-20'));
    await setDoc(doc(db, `usage/${OTHER_ADULT.memberId}/days/2026-11-20`), day('2026-11-20'));
  });
});

function day(id, overrides = {}) {
  return {
    day: id,
    opens: 3,
    seconds: { home: 120, planning: 300 },
    expireAt: Timestamp.fromDate(new Date('2027-12-20T00:00:00Z')),
    ...overrides,
  };
}

test('a member writes their own day, and nobody else\'s', async () => {
  const db = as(env, CHILD);
  await assertSucceeds(setDoc(doc(db, `usage/${CHILD.memberId}/days/2026-11-21`), day('2026-11-21')));
  await assertSucceeds(setDoc(doc(db, `usage/${CHILD.memberId}/days/2026-11-21`), day('2026-11-21', { opens: 4 })));
  await assertFails(setDoc(doc(db, `usage/${ADULT.memberId}/days/2026-11-21`), day('2026-11-21')));
});

test('a day keeps its shape: its own date, bounded counts, known screens only', async () => {
  const db = as(env, ADULT);
  const path = `usage/${ADULT.memberId}/days/2026-11-21`;
  await assertFails(setDoc(doc(db, path), day('2026-11-22')));
  await assertFails(setDoc(doc(db, path), day('2026-11-21', { opens: -1 })));
  await assertFails(setDoc(doc(db, path), day('2026-11-21', { seconds: { home: 90000 } })));
  await assertFails(setDoc(doc(db, path), day('2026-11-21', { seconds: { 'liste-cadeaux-sam': 10 } })));
  await assertFails(setDoc(doc(db, path), day('2026-11-21', { readItems: ['a'] })));
  await assertFails(setDoc(doc(db, `usage/${ADULT.memberId}/days/hier`), day('hier')));
});

test('the admin principal reads everyone\'s days', async () => {
  const db = as(env, ADMIN);
  await assertSucceeds(getDoc(doc(db, `usage/${OTHER_ADULT.memberId}/days/2026-11-20`)));
  await assertSucceeds(getDocs(collection(db, `usage/${CHILD.memberId}/days`)));
});

test('a responsible adult reads their child\'s days, not another adult\'s', async () => {
  const db = as(env, ADULT);
  await assertSucceeds(getDocs(collection(db, `usage/${CHILD.memberId}/days`)));
  await assertFails(getDoc(doc(db, `usage/${OTHER_ADULT.memberId}/days/2026-11-20`)));
});

test('a member does not read the days of others, nor even their own', async () => {
  await assertFails(getDoc(doc(as(env, OTHER_ADULT), `usage/${CHILD.memberId}/days/2026-11-20`)));
  await assertFails(getDoc(doc(as(env, CHILD), `usage/${CHILD.memberId}/days/2026-11-20`)));
});

test('nobody deletes a day: the TTL does', async () => {
  await assertFails(deleteDoc(doc(as(env, CHILD), `usage/${CHILD.memberId}/days/2026-11-20`)));
  await assertFails(deleteDoc(doc(as(env, ADMIN), `usage/${CHILD.memberId}/days/2026-11-20`)));
});
