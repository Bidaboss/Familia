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

  test('récit 62: the reminder of the bag is turned off, and the list to start from is mine only', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(settings(db, 'adult1'), { packingReminder: false }, { merge: true }));
    await assertFails(setDoc(settings(db, 'adult1'), { packingReminder: 'non' }, { merge: true }));
    await assertSucceeds(setDoc(settings(db, 'adult1', 'packing'), { labels: ['Chargeur', 'Papiers'], updatedAt: serverTimestamp() }));
    await assertSucceeds(getDoc(settings(db, 'adult1', 'packing')));
    await assertFails(setDoc(settings(db, 'adult1', 'packing'), { labels: 'Chargeur', updatedAt: serverTimestamp() }));
    await assertFails(setDoc(settings(db, 'adult1', 'packing'), { labels: [], updatedAt: serverTimestamp(), other: 1 }));
    await assertFails(setDoc(settings(db, 'adult1', 'packing'), { labels: Array.from({ length: 81 }, (_, i) => `o${i}`), updatedAt: serverTimestamp() }));
    await assertFails(setDoc(settings(as(env, OTHER_ADULT), 'adult1', 'packing'), { labels: [], updatedAt: serverTimestamp() }));
    await assertFails(getDoc(settings(as(env, OTHER_ADULT), 'adult1', 'packing')));
  });

  test('récit 6 quinquies: one switch per family and the quiet hours, nothing else', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(settings(db, 'adult1'), {
      planning: true, reminders: true, gifts: false, comments: false, helpRequests: true, trips: false, quietHours: true,
    }, { merge: true }));
    await assertFails(setDoc(settings(db, 'adult1'), { level: 'minimal' }, { merge: true }));
    await assertFails(setDoc(settings(db, 'adult1'), { quietHours: 'oui' }, { merge: true }));
    await assertFails(setDoc(settings(db, 'adult1'), { trips: 1 }, { merge: true }));
    await assertFails(setDoc(settings(db, 'adult1'), { quietFrom: 21 }, { merge: true }));
    // Nobody else sets them, the admin included, and the held notices stay closed.
    await assertFails(setDoc(settings(as(env, ADMIN), 'adult1'), { trips: true }, { merge: true }));
    await assertFails(getDoc(doc(db, 'quietQueue/adult1')));
    await assertFails(setDoc(doc(db, 'quietQueue/adult1'), { items: [] }));
  });
});

// « Mes rappels sur un événement » (PRD rév. 248, récit 56).
describe('my reminders', () => {
  const reminder = (db, memberId, id) => doc(db, `members/${memberId}/reminders/${id}`);

  test('a member asks for reminders on an event and on a whole series, then takes them back', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(reminder(db, 'adult1', 'e1'), { eventId: 'e1', offsets: ['h1', 'w1'], updatedAt: serverTimestamp() }));
    await assertSucceeds(setDoc(reminder(db, 'adult1', 'series-s1'), { seriesId: 's1', offsets: ['morning'], updatedAt: serverTimestamp() }));
    await assertSucceeds(setDoc(reminder(db, 'adult1', 'e2'), { eventId: 'e2', offsets: [], updatedAt: serverTimestamp() }));
    await assertSucceeds(getDoc(reminder(db, 'adult1', 'e1')));
    await assertSucceeds(deleteDoc(reminder(db, 'adult1', 'e1')));
  });

  test('they stay private, even from the admin principal', async () => {
    await env.withSecurityRulesDisabled((ctx) => setDoc(reminder(ctx.firestore(), 'adult1', 'e1'), { eventId: 'e1', offsets: ['h1'] }));
    await assertFails(getDoc(reminder(as(env, ADMIN), 'adult1', 'e1')));
    await assertFails(getDoc(reminder(as(env, OTHER_ADULT), 'adult1', 'e1')));
    await assertFails(setDoc(reminder(as(env, ADMIN), 'adult1', 'e3'), { eventId: 'e3', offsets: ['h1'], updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(reminder(as(env, ADMIN), 'adult1', 'e1')));
  });

  test('only the five choices, under the right name, and nothing else', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(reminder(db, 'adult1', 'e1'), { eventId: 'e1', offsets: ['h2'], updatedAt: serverTimestamp() }));
    await assertFails(setDoc(reminder(db, 'adult1', 'e1'), { eventId: 'e9', offsets: ['h1'], updatedAt: serverTimestamp() }));
    await assertFails(setDoc(reminder(db, 'adult1', 's1'), { seriesId: 's1', offsets: ['h1'], updatedAt: serverTimestamp() }));
    await assertFails(setDoc(reminder(db, 'adult1', 'e1'), { eventId: 'e1', offsets: ['h1'], updatedAt: Timestamp.fromMillis(0) }));
    await assertFails(setDoc(reminder(db, 'adult1', 'e1'), { eventId: 'e1', offsets: ['h1'], note: 'x', updatedAt: serverTimestamp() }));
    await assertFails(setDoc(reminder(db, 'adult1', 'e1'), { eventId: 'e1', seriesId: 's1', offsets: ['h1'], updatedAt: serverTimestamp() }));
  });

  test('the default reminders, in the settings', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(settings(db, 'adult1'), { defaultReminders: ['h1', 'd2'] }));
    await assertSucceeds(setDoc(settings(db, 'adult1'), { defaultReminders: [] }));
    await assertFails(setDoc(settings(db, 'adult1'), { defaultReminders: ['tomorrow'] }));
    await assertFails(setDoc(settings(db, 'adult1'), { defaultReminders: 'h1' }));
  });

  test('what went out stays closed to the app', async () => {
    await assertFails(getDoc(doc(as(env, ADULT), 'remindersSent/adult1_e1_h1')));
    await assertFails(setDoc(doc(as(env, ADULT), 'remindersSent/adult1_e1_h1'), {}));
  });
});
