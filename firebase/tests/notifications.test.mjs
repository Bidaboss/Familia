// Lot 8 rules: the phones and the notification settings of each member. Only the member
// themselves reads or writes them; the Cloud Functions use their own server access.
// Fictional family from helpers.mjs.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, Timestamp } from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
});

const TOKEN = 'fcm-token:abc_123-XYZ';
const device = (db, memberId, token = TOKEN) => doc(db, `members/${memberId}/devices/${token}`);
const settings = (db, memberId, id = 'notifications') => doc(db, `members/${memberId}/settings/${id}`);

describe('phones', () => {
  test('a member registers, refreshes and removes their phone', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(device(db, 'adult1'), { updatedAt: serverTimestamp() }));
    await assertSucceeds(setDoc(device(db, 'adult1'), { updatedAt: serverTimestamp() }));
    await assertSucceeds(getDoc(device(db, 'adult1')));
    await assertSucceeds(deleteDoc(device(db, 'adult1')));
  });

  test('nobody registers a phone for someone else, not even an admin', async () => {
    await assertFails(setDoc(device(as(env, OTHER_ADULT), 'adult1'), { updatedAt: serverTimestamp() }));
    await assertFails(setDoc(device(as(env, ADMIN), 'adult1'), { updatedAt: serverTimestamp() }));
    await assertFails(setDoc(device(as(env, STRANGER), 'adult1'), { updatedAt: serverTimestamp() }));
  });

  test('nobody reads the phones of someone else', async () => {
    await env.withSecurityRulesDisabled((ctx) => setDoc(device(ctx.firestore(), 'adult1'), { updatedAt: Timestamp.fromMillis(0) }));
    await assertFails(getDoc(device(as(env, OTHER_ADULT), 'adult1')));
    await assertFails(getDoc(device(as(env, ADMIN), 'adult1')));
    await assertFails(deleteDoc(device(as(env, ADMIN), 'adult1')));
  });

  test('a phone holds nothing but its refresh time, set by the server', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(device(db, 'adult1'), { updatedAt: Timestamp.fromMillis(0) }));
    await assertFails(setDoc(device(db, 'adult1'), { updatedAt: serverTimestamp(), model: 'Pixel' }));
  });
});

describe('settings', () => {
  test('a member turns the reminder off and on again', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(settings(db, 'adult1'), { dayBeforeReminder: false }));
    await assertSucceeds(setDoc(settings(db, 'adult1'), { dayBeforeReminder: true }));
    await assertSucceeds(getDoc(settings(db, 'adult1')));
  });

  test('nobody changes or reads the settings of someone else', async () => {
    await assertFails(setDoc(settings(as(env, ADMIN), 'adult1'), { dayBeforeReminder: false }));
    await assertFails(getDoc(settings(as(env, OTHER_ADULT), 'adult1')));
  });

  test('only the known settings, with the right type', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(settings(db, 'adult1'), { dayBeforeReminder: 'no' }));
    await assertFails(setDoc(settings(db, 'adult1'), { dayBeforeReminder: false, isAdmin: true }));
    await assertFails(setDoc(settings(db, 'adult1', 'other'), { dayBeforeReminder: false }));
  });
});
