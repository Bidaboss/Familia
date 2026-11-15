// Lot 10 rules: config/app holds the latest version and the link of its APK. Every member reads
// it; from the app only the admin principal switches modules on or off (features).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, SECOND_ADMIN, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const config = { latestVersionCode: 2, latestVersionName: '0.2.0' };

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'config/app'), config));
});

describe('config/app', () => {
  test('every member reads it', async () => {
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'config/app')));
  });

  test('a stranger does not', async () => {
    await assertFails(getDoc(doc(as(env, STRANGER), 'config/app')));
  });

  test('nobody writes it from the app, not even an admin', async () => {
    const db = as(env, ADMIN);
    await assertFails(setDoc(doc(db, 'config/app'), { ...config, latestVersionCode: 99 }));
    await assertFails(deleteDoc(doc(db, 'config/app')));
  });

  test('the admin principal switches a module on or off, and nothing else', async () => {
    const db = as(env, ADMIN);
    await assertSucceeds(setDoc(doc(db, 'config/app'), { features: { gifts: true } }, { merge: true }));
    await assertSucceeds(setDoc(doc(db, 'config/app'), { features: { gifts: false, trips: true } }, { merge: true }));
    await assertFails(updateDoc(doc(db, 'config/app'), { features: { gifts: true }, latestVersionCode: 99 }));
    await assertFails(updateDoc(doc(db, 'config/app'), { features: 'all' }));
    const many = Object.fromEntries(Array.from({ length: 31 }, (_, i) => [`m${i}`, true]));
    await assertFails(updateDoc(doc(db, 'config/app'), { features: many }));
  });

  test('a chef de famille or another member never switches a module', async () => {
    await assertFails(setDoc(doc(as(env, SECOND_ADMIN), 'config/app'), { features: { gifts: true } }, { merge: true }));
    await assertFails(setDoc(doc(as(env, ADULT), 'config/app'), { features: { gifts: true } }, { merge: true }));
  });

  test('config/family never changes from the app', async () => {
    await assertFails(setDoc(doc(as(env, ADMIN), 'config/family'), { ownerMemberId: ADMIN.memberId, features: {} }, { merge: true }));
  });

  test('other config documents stay closed', async () => {
    await assertFails(getDoc(doc(as(env, ADULT), 'config/other')));
  });
});
