// L'arbre et le défi du mois (PRD, récit 35). Fictional family from helpers.mjs.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, SECOND_ADMIN, STRANGER, as, createEnv, seed } from './helpers.mjs';

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

  test('a member can turn off « La semaine de la famille » (récit 36), and the recaps stay closed', async () => {
    const settings = doc(as(env, ADULT), `members/${ADULT.memberId}/settings/notifications`);
    await assertSucceeds(setDoc(settings, { recap: false, tree: true }));
    await assertFails(setDoc(settings, { recap: 'non' }));
    await assertFails(getDoc(doc(as(env, ADMIN), 'weeklyRecaps/2026-11-22')));
    await assertFails(setDoc(doc(as(env, ADMIN), 'weeklyRecaps/2026-11-22'), { sentAt: serverTimestamp() }));
  });

  test('only the admin principal picks the challenge of a month, from the list', async () => {
    const pick = (who, month, data) => setDoc(doc(as(env, who), `challengePicks/${month}`), data);
    await assertSucceeds(pick(ADMIN, '2027-01', { choiceId: 'kitchen', pickedAt: serverTimestamp() }));
    await assertSucceeds(pick(ADMIN, '2027-01', { choiceId: 'pets', pickedAt: serverTimestamp() }));
    await assertSucceeds(getDoc(doc(as(env, ADMIN), 'challengePicks/2027-01')));
    // Not a chef de famille, not another adult, and only a challenge of the list.
    await assertFails(pick(SECOND_ADMIN, '2027-01', { choiceId: 'kitchen', pickedAt: serverTimestamp() }));
    await assertFails(pick(ADULT, '2027-01', { choiceId: 'kitchen', pickedAt: serverTimestamp() }));
    await assertFails(getDoc(doc(as(env, ADULT), 'challengePicks/2027-01')));
    await assertFails(pick(ADMIN, '2027-01', { choiceId: '1000 photos', pickedAt: serverTimestamp() }));
    await assertFails(pick(ADMIN, '2027-01', { choiceId: 'kitchen', pickedAt: serverTimestamp(), target: 1 }));
    await assertFails(pick(ADMIN, '2027-13', { choiceId: 'kitchen', pickedAt: serverTimestamp() }));
    await assertFails(pick(ADMIN, 'janvier', { choiceId: 'kitchen', pickedAt: serverTimestamp() }));
    await assertFails(pick(ADMIN, '2027-02', { choiceId: 'kitchen', pickedAt: new Date('2020-01-01') }));
    await assertFails(deleteDoc(doc(as(env, ADMIN), 'challengePicks/2027-01')));
  });

  test('the résumé of the day stays closed, even to its member (AVIS-14)', async () => {
    const db = as(env, ADULT);
    await assertFails(getDoc(doc(db, `engagementDigests/${ADULT.memberId}`)));
    await assertFails(setDoc(doc(db, `engagementDigests/${ADULT.memberId}`), { pending: false }));
    await assertFails(getDoc(doc(as(env, ADMIN), `engagementDigests/${ADULT.memberId}`)));
  });
});
