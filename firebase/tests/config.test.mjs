// Lot 10 rules: config/app holds the latest version and the link of its APK. Every member reads
// it; from the app only the admin principal switches modules on or off (features).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, deleteField, doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';
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
    // Room for 60 modules (relevé de 40 à 60 avec la présentation R1 bis, 39 interrupteurs déjà).
    const sixty = Object.fromEntries(Array.from({ length: 60 }, (_, i) => [`m${i}`, true]));
    await assertSucceeds(updateDoc(doc(db, 'config/app'), { features: sixty }));
    const many = Object.fromEntries(Array.from({ length: 61 }, (_, i) => [`m${i}`, true]));
    await assertFails(updateDoc(doc(db, 'config/app'), { features: many }));
  });

  test('the admin principal orders « À découvrir », a short list only', async () => {
    const db = as(env, ADMIN);
    await assertSucceeds(setDoc(doc(db, 'config/app'), { discoverOrder: ['recipes', 'polls'] }, { merge: true }));
    await assertSucceeds(updateDoc(doc(db, 'config/app'), { discoverOrder: [] }));
    await assertFails(updateDoc(doc(db, 'config/app'), { discoverOrder: 'recipes' }));
    await assertFails(updateDoc(doc(db, 'config/app'), { discoverOrder: Array.from({ length: 21 }, (_, i) => `m${i}`) }));
    await assertFails(updateDoc(doc(db, 'config/app'), { discoverOrder: ['recipes'], latestVersionCode: 99 }));
    await assertFails(setDoc(doc(as(env, ADULT), 'config/app'), { discoverOrder: ['recipes'] }, { merge: true }));
    await assertFails(setDoc(doc(as(env, SECOND_ADMIN), 'config/app'), { discoverOrder: ['recipes'] }, { merge: true }));
  });

  test('the switches are never removed, nor emptied of their kind', async () => {
    const db = as(env, ADMIN);
    await assertSucceeds(setDoc(doc(db, 'config/app'), { features: { gifts: true } }, { merge: true }));
    await assertFails(updateDoc(doc(db, 'config/app'), { features: deleteField() }));
    await assertFails(updateDoc(doc(db, 'config/app'), { features: null }));
    await assertFails(updateDoc(doc(db, 'config/app'), { features: deleteField(), discoverOrder: ['recipes'] }));
    await assertSucceeds(updateDoc(doc(db, 'config/app'), { discoverOrder: ['recipes'] }));
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

describe('config/presentation (mode présentation, récit 48)', () => {
  const until = new Date('2026-11-21T15:00:00Z');

  test('the admin principal turns it on for a time or until turned off, then off', async () => {
    const db = as(env, ADMIN);
    await assertSucceeds(setDoc(doc(db, 'config/presentation'), { on: true, until, since: new Date() }));
    await assertSucceeds(getDoc(doc(db, 'config/presentation')));
    await assertSucceeds(setDoc(doc(db, 'config/presentation'), { on: true, until: null, since: new Date() }));
    await assertSucceeds(updateDoc(doc(db, 'config/presentation'), { on: false }));
    await assertFails(deleteDoc(doc(db, 'config/presentation')));
  });

  test('only on, until and since, of their kind', async () => {
    const db = as(env, ADMIN);
    await assertFails(setDoc(doc(db, 'config/presentation'), { on: 'yes' }));
    await assertFails(setDoc(doc(db, 'config/presentation'), { on: true, until: 'ce soir' }));
    await assertFails(setDoc(doc(db, 'config/presentation'), { on: true, until, by: 'admin' }));
  });

  test('nobody else reads or writes it, not even a chef de famille', async () => {
    await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'config/presentation'), { on: true, until: null }));
    for (const who of [ADULT, SECOND_ADMIN, STRANGER]) {
      await assertFails(getDoc(doc(as(env, who), 'config/presentation')));
      await assertFails(updateDoc(doc(as(env, who), 'config/presentation'), { on: false }));
    }
  });

  test('its queue stays with the functions', async () => {
    await assertFails(getDoc(doc(as(env, ADMIN), 'presentationQueue/adult1')));
  });
});
