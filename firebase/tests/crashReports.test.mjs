// « Plantages » (PRD, récit 6 sexies, rév. 220): any member sends the report of a crash of their
// own phone; only the admin principal reads them and removes them (« Marquer comme réglé »).
// Fictional family: adult1 and adult2 in h1 with admin2 (chef of h1), admin1 (admin principal).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, SECOND_ADMIN, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
});

const report = (db, id = 'r1') => doc(db, `crashReports/${id}`);

const TRACE = 'java.lang.IllegalStateException\n\tat fr.familia.app.feature.home.HomeViewModel.load(HomeViewModel.kt:42)';

function sent(by, overrides = {}) {
  return {
    memberId: by.memberId, appVersion: '1.4.0', versionCode: 140, android: '14', device: 'Google Pixel 8',
    screen: 'HomeDestination', trace: TRACE, signature: '0123456789abcdef',
    crashedAt: Timestamp.fromMillis(Date.now() - 60_000), createdAt: serverTimestamp(), ...overrides,
  };
}

async function seedReport(by = ADULT.memberId, id = 'r1') {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(report(ctx.firestore(), id), {
      memberId: by, appVersion: '1.4.0', versionCode: 140, android: '14', device: 'Google Pixel 8',
      screen: 'PlanningDestination', trace: TRACE, signature: '0123456789abcdef',
      crashedAt: Timestamp.fromMillis(0), createdAt: Timestamp.fromMillis(0),
    });
  });
}

describe('sending', () => {
  test('a member sends the report of their own crash', async () => {
    await assertSucceeds(setDoc(report(as(env, ADULT)), sent(ADULT)));
    await assertSucceeds(setDoc(report(as(env, ADMIN), 'r2'), sent(ADMIN)));
  });

  test('never in the name of another, nor from outside the family', async () => {
    await assertFails(setDoc(report(as(env, ADULT)), sent(ADULT, { memberId: OTHER_ADULT.memberId })));
    await assertFails(setDoc(report(as(env, STRANGER)), sent({ memberId: 'x' })));
  });

  test('sent twice, the second one is refused: a crash is never counted twice', async () => {
    await assertSucceeds(setDoc(report(as(env, ADULT)), sent(ADULT)));
    await assertFails(setDoc(report(as(env, ADULT)), sent(ADULT)));
  });

  test('only the known fields, in their sizes, at the server time', async () => {
    await assertFails(setDoc(report(as(env, ADULT)), sent(ADULT, { text: 'Ce que j\'écrivais' })));
    await assertFails(setDoc(report(as(env, ADULT)), sent(ADULT, { trace: '' })));
    await assertFails(setDoc(report(as(env, ADULT)), sent(ADULT, { trace: 't'.repeat(8001) })));
    await assertSucceeds(setDoc(report(as(env, ADULT), 'big'), sent(ADULT, { trace: 't'.repeat(8000) })));
    await assertFails(setDoc(report(as(env, ADULT)), sent(ADULT, { signature: 'not-a-hash' })));
    await assertFails(setDoc(report(as(env, ADULT)), sent(ADULT, { signature: '0123456789ABCDEF' })));
    await assertFails(setDoc(report(as(env, ADULT)), sent(ADULT, { versionCode: '140' })));
    await assertFails(setDoc(report(as(env, ADULT)), sent(ADULT, { device: 'd'.repeat(81) })));
    await assertFails(setDoc(report(as(env, ADULT)), sent(ADULT, { screen: 's'.repeat(81) })));
    await assertFails(setDoc(report(as(env, ADULT)), sent(ADULT, { createdAt: Timestamp.fromMillis(0) })));
    await assertFails(setDoc(report(as(env, ADULT)), sent(ADULT, { crashedAt: Timestamp.fromMillis(Date.now() + 3_600_000) })));
    const { trace: _trace, ...withoutTrace } = sent(ADULT);
    await assertFails(setDoc(report(as(env, ADULT)), withoutTrace));
  });

  test('an id of at most 64 characters', async () => {
    await assertFails(setDoc(report(as(env, ADULT), 'x'.repeat(65)), sent(ADULT)));
  });
});

describe('reading and settling', () => {
  test('only the admin principal reads the reports', async () => {
    await seedReport();
    await assertSucceeds(getDoc(report(as(env, ADMIN))));
    await assertSucceeds(getDocs(collection(as(env, ADMIN), 'crashReports')));
    await assertFails(getDoc(report(as(env, ADULT))));
    await assertFails(getDocs(collection(as(env, SECOND_ADMIN), 'crashReports')));
    await assertFails(getDoc(report(as(env, STRANGER))));
  });

  test('only the admin principal removes them, and nobody changes them', async () => {
    await seedReport();
    await assertFails(deleteDoc(report(as(env, ADULT))));
    await assertFails(deleteDoc(report(as(env, SECOND_ADMIN))));
    await assertFails(updateDoc(report(as(env, ADMIN)), { screen: 'Autre' }));
    await assertSucceeds(deleteDoc(report(as(env, ADMIN))));
  });

  test('the notice state of the server is closed to the app', async () => {
    await assertFails(getDoc(doc(as(env, ADMIN), 'crashNotices/state')));
    await assertFails(setDoc(doc(as(env, ADMIN), 'crashNotices/state'), { lastDay: '2026-11-21' }));
  });
});
