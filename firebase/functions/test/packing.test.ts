import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { dropBags, leftIn, leftTheTrip, packingMessages, sendPackingReminders } from '../src/packing';
import type { Member } from '../src/logic';

const member = (id: string, firstName: string, profile: Member['profile'] = 'adult', parentIds: string[] = []): Member =>
  ({ id, firstName, profile, parentIds, householdId: 'h1', uid: null });
const MEMBERS = [
  member('camille', 'Camille'), member('ines', 'Inès'), member('sam', 'Sam'),
  member('noe', 'Noé', 'childWithoutApp', ['ines']), member('lou', 'Lou', 'childWithApp', ['ines']),
];

describe('packing, without the emulator', () => {
  test('a bag never opened, or all ticked, says nothing', () => {
    assert.equal(leftIn(undefined), 0);
    assert.equal(leftIn([{ label: 'Chargeur', done: true }]), 0);
    assert.equal(leftIn([{ label: 'Chargeur', done: false }, { label: 'Pyjama', done: true }, { label: 'Maillot' }]), 2);
  });

  test('each one hears about their own bag; the adult of a child without the app about the child’s too', () => {
    const left = new Map([['camille', 1], ['ines', 3], ['noe', 2], ['lou', 4], ['sam', 0]]);
    const msgs = packingMessages('t1', 'Corse', ['camille', 'ines', 'noe', 'lou', 'sam'], left, MEMBERS);
    assert.deepEqual(msgs.map((m) => m.recipientId), ['camille', 'ines', 'lou']);
    assert.equal(msgs[0].notice.body, 'Corse : il reste 1 objet dans ta valise.');
    assert.equal(msgs[1].notice.body, 'Corse : il reste 3 objets dans ta valise, la valise de Noé n\'est pas prête.');
    assert.equal(msgs[1].notice.title, 'Demain, on part !');
    assert.equal(msgs[1].notice.tripId, 't1');
    assert.equal(msgs[1].notice.channel, 'trips');
  });

  test('an adult who does not come is not told about the child’s bag', () => {
    const msgs = packingMessages('t1', 'Corse', ['noe', 'camille'], new Map([['noe', 2]]), MEMBERS);
    assert.deepEqual(msgs, []);
  });

  test('a bag left behind by someone no longer on the trip says nothing', () => {
    const msgs = packingMessages('t1', 'Corse', ['camille'], new Map([['camille', 0], ['ines', 3]]), MEMBERS);
    assert.deepEqual(msgs, []);
  });

  test('only those taken off the trip leave their bag', () => {
    assert.deepEqual(leftTheTrip(['camille', 'ines', 'noe'], ['ines', 'sam']), ['camille', 'noe']);
    assert.deepEqual(leftTheTrip(['camille'], ['camille', 'sam']), []);
    assert.deepEqual(leftTheTrip(undefined, ['camille']), []);
    assert.deepEqual(leftTheTrip(['camille', 42], 'abimé'), ['camille']);
  });
});

describe('packing against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'packing-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  // 9 July 2027 at 18 h 30 in Paris (UTC+2): the trip leaves tomorrow.
  const evening = new Date('2027-07-09T16:30:00Z');

  beforeEach(async () => {
    for (const c of ['trips', 'members', 'remindersSent', 'config']) await db.recursiveDelete(db.collection(c));
    await db.doc('config/app').set({ features: { packingList: true } });
    for (const m of MEMBERS) await db.doc(`members/${m.id}`).set({ firstName: m.firstName, profile: m.profile, parentIds: m.parentIds, householdId: 'h1' });
    await db.doc('trips/t1').set({ title: 'Corse', startDate: '2027-07-10', endDate: '2027-07-20', participantIds: ['camille', 'ines', 'noe'] });
    await db.doc('trips/t1/bags/camille').set({ items: [{ label: 'Chargeur', done: false }] });
    await db.doc('trips/t1/bags/ines').set({ items: [{ label: 'Chargeur', done: true }] });
  });

  const sender = (titles: string[]) => async (_token: string, n: { body: string }) => { titles.push(n.body); return 'ok' as const; };

  test('once, from 18 h the day before, to those whose bag is not ready', async () => {
    await db.doc('members/camille/devices/tok').set({ updatedAt: new Date() });
    const bodies: string[] = [];
    assert.equal(await sendPackingReminders(db, sender(bodies), new Date('2027-07-09T15:00:00Z')), 0); // 17 h
    await sendPackingReminders(db, sender(bodies), evening);
    await sendPackingReminders(db, sender(bodies), new Date('2027-07-09T17:00:00Z'));
    assert.deepEqual(bodies, ['Corse : il reste 1 objet dans ta valise.']);
  });

  test('not when turned off in the settings, nor while the module is off', async () => {
    await db.doc('members/camille/devices/tok').set({ updatedAt: new Date() });
    await db.doc('members/camille/settings/notifications').set({ packingReminder: false });
    const bodies: string[] = [];
    await sendPackingReminders(db, sender(bodies), evening);
    assert.deepEqual(bodies, []);
    await db.doc('members/camille/settings/notifications').delete();
    await db.doc('config/app').set({ features: { packingList: false } });
    await sendPackingReminders(db, sender(bodies), evening);
    assert.deepEqual(bodies, []);
  });

  test('the bag of someone taken off the trip goes, the others stay', async () => {
    assert.equal(await dropBags(db, 't1', ['camille', 'noe']), 2);
    assert.equal((await db.doc('trips/t1/bags/camille').get()).exists, false);
    assert.equal((await db.doc('trips/t1/bags/ines').get()).exists, true);
    assert.equal(await dropBags(db, 't1', []), 0);
  });
});
