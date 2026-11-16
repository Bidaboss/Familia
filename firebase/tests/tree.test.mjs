// L'arbre et le défi du mois (PRD, récit 35). Fictional family from helpers.mjs.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';
import { ADULT, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'familyTree/current'), { leaves: 12, bloomedMonths: [] });
    await setDoc(doc(db, 'challenges/2026-12'), { title: '20 photos de Noël', emoji: '🎄', measure: 'photos', target: 20, progress: 3, achievedAt: null });
    await setDoc(doc(db, `treeDays/${ADULT.memberId}_2026-12-01`), { memberId: ADULT.memberId, count: 2 });
    await setDoc(doc(db, 'treeMarks/goodNews-n1'), { memberId: ADULT.memberId });
  });
});

describe('tree', () => {
  test('the family reads the tree and the challenge, a stranger does not', async () => {
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'familyTree/current')));
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'challenges/2026-12')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'familyTree/current')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'challenges/2026-12')));
  });

  test('nobody writes them from the app, not even a leaf or a step of the gauge', async () => {
    const db = as(env, ADULT);
    await assertFails(updateDoc(doc(db, 'familyTree/current'), { leaves: 13 }));
    await assertFails(setDoc(doc(db, 'familyTree/other'), { leaves: 1 }));
    await assertFails(updateDoc(doc(db, 'challenges/2026-12'), { progress: 20 }));
    await assertFails(setDoc(doc(db, 'challenges/2027-01'), { title: 'x' }));
    await assertFails(deleteDoc(doc(db, 'challenges/2026-12')));
  });

  test('the day counts and the marks stay closed, even to their member', async () => {
    const db = as(env, ADULT);
    await assertFails(getDoc(doc(db, `treeDays/${ADULT.memberId}_2026-12-01`)));
    await assertFails(getDoc(doc(db, 'treeMarks/goodNews-n1')));
    await assertFails(setDoc(doc(db, `treeDays/${ADULT.memberId}_2026-12-02`), { memberId: ADULT.memberId, count: 0 }));
  });

  test('a member can turn off the notice of a challenge achieved', async () => {
    const settings = doc(as(env, ADULT), `members/${ADULT.memberId}/settings/notifications`);
    await assertSucceeds(setDoc(settings, { tree: false }));
    await assertFails(setDoc(settings, { tree: 'non' }));
  });
});
