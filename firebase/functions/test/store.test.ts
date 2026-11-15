// Runs against the Firestore emulator (npm run test:functions in firebase/). Fictional data only.
import { after, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import { comingMembers, deleteEventAnswers, deleteMemberData, notify, notifyChange, Sender, sendDayBeforeReminders } from '../src/store';

assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');

const app = initializeApp({ projectId: 'demo-familia' }, 'store-test');
const db = getFirestore(app);

after(() => deleteApp(app));

/** A fake phone network: records what would be sent, and knows uninstalled apps. */
function fakeSender(uninstalled: string[] = []) {
  const sent: { token: string; notice: Notice }[] = [];
  const send: Sender = async (token, notice) => {
    if (uninstalled.includes(token)) return 'unregistered';
    sent.push({ token, notice });
    return 'ok';
  };
  return { sent, send };
}

function member(firstName: string, profile = 'adult', parentIds: string[] = [], uid: string | null = null) {
  return { firstName, email: null, birthDate: null, householdId: 'h1', profile, isAdmin: false, parentIds, uid };
}

function event(title: string, date: string) {
  return {
    title, date, time: '19:30', place: null, note: null, targetType: 'family', targetMemberId: null, householdId: null,
    authorId: 'camille', authorName: 'Camille', createdAt: new Date(), updatedAt: new Date(),
  };
}

// 2026-12-18 at 18 h in Paris.
const now = new Date('2026-12-18T17:00:00Z');

beforeEach(async () => {
  await db.recursiveDelete(db.collection('members'));
  await db.recursiveDelete(db.collection('events'));
  const batch = db.batch();
  batch.set(db.doc('members/camille'), member('Camille', 'adult', [], 'uid-camille'));
  batch.set(db.doc('members/ines'), member('Inès', 'adult', [], 'uid-ines'));
  batch.set(db.doc('members/noe'), member('Noé', 'childWithoutApp', ['camille']));
  batch.set(db.doc('members/camille/devices/token-camille'), { updatedAt: new Date() });
  batch.set(db.doc('members/ines/devices/token-ines'), { updatedAt: new Date() });
  batch.set(db.doc('members/ines/devices/token-old'), { updatedAt: new Date() });
  batch.set(db.doc('events/meal'), event('Repas', '2026-12-19'));
  batch.set(db.doc('events/later'), event('Galette', '2027-01-06'));
  batch.set(db.doc('events/meal/rsvps/noe'), { status: 'yes', answeredBy: 'camille', updatedAt: new Date() });
  batch.set(db.doc('events/meal/rsvps/ines'), { status: 'maybe', answeredBy: 'ines', updatedAt: new Date() });
  batch.set(db.doc('events/later/rsvps/ines'), { status: 'yes', answeredBy: 'ines', updatedAt: new Date() });
  await batch.commit();
});

test('a change reaches the phones of everyone but its author', async () => {
  const { sent, send } = fakeSender();
  const data = (await db.doc('events/meal').get()).data() as never;
  await notifyChange(db, send, 'added', 'meal', data, 'uid-camille', now);
  assert.deepEqual(sent.map((s) => s.token).sort(), ['token-ines', 'token-old']);
  assert.match(sent[0].notice.body, /^Camille a ajouté « Repas »/);
});

test('the token of an uninstalled app is forgotten', async () => {
  const { sent, send } = fakeSender(['token-old']);
  await notify(db, send, ['ines'], { channel: 'events', title: 't', body: 'b', eventId: 'meal' });
  assert.deepEqual(sent.map((s) => s.token), ['token-ines']);
  assert.equal((await db.doc('members/ines/devices/token-old').get()).exists, false);
  assert.equal((await db.doc('members/ines/devices/token-ines').get()).exists, true);
});

test('those coming are read from the answers, a child without the app through their adults', async () => {
  assert.deepEqual([...(await comingMembers(db, 'meal'))].sort(), ['camille', 'ines']);
  await db.doc('events/meal/rsvps/ines').set({ status: 'no', answeredBy: 'ines', updatedAt: new Date() });
  assert.deepEqual([...(await comingMembers(db, 'meal'))], ['camille']);
});

test('an event of another household still reaches whoever said they were coming', async () => {
  const { sent, send } = fakeSender();
  const other = { ...event('Goûter', '2026-12-19'), targetType: 'household', householdId: 'h9' } as never;
  await notifyChange(db, send, 'deleted', 'meal', other, 'uid-camille', now);
  assert.equal(sent.length, 0);
  await notifyChange(db, send, 'deleted', 'meal', other, 'uid-camille', now, new Set(['ines']));
  assert.deepEqual(sent.map((s) => s.token).sort(), ['token-ines', 'token-old']);
});

test('deleting an event deletes its answers, and only its answers', async () => {
  await deleteEventAnswers(db, 'meal');
  assert.equal((await db.collection('events/meal/rsvps').get()).size, 0);
  assert.equal((await db.collection('events/later/rsvps').get()).size, 1);
});

test('removing a member deletes their answers, phones and settings', async () => {
  await db.doc('members/ines/settings/notifications').set({ dayBeforeReminder: false });
  await db.doc('members/ines').delete();
  await deleteMemberData(db, 'ines');
  assert.equal((await db.doc('events/meal/rsvps/ines').get()).exists, false);
  assert.equal((await db.doc('events/later/rsvps/ines').get()).exists, false);
  assert.equal((await db.doc('events/meal/rsvps/noe').get()).exists, true);
  assert.equal((await db.collection('members/ines/devices').get()).size, 0);
  assert.equal((await db.doc('members/ines/settings/notifications').get()).exists, false);
});

test('at 18 h, those coming tomorrow are reminded, children without the app through their adults', async () => {
  const { sent, send } = fakeSender();
  await sendDayBeforeReminders(db, send, now);
  assert.deepEqual(
    sent.map((s) => [s.token, s.notice.title]).sort(),
    [
      ['token-camille', 'Demain : Repas (pour Noé)'],
      ['token-ines', 'Demain : Repas'],
      ['token-old', 'Demain : Repas'],
    ],
  );
});

test('the reminder can be turned off', async () => {
  await db.doc('members/ines/settings/notifications').set({ dayBeforeReminder: false });
  const { sent, send } = fakeSender();
  await sendDayBeforeReminders(db, send, now);
  assert.deepEqual(sent.map((s) => s.token), ['token-camille']);
});
