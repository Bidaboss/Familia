import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { clearRidesOf, clearRidesOfMember, onRideChanged, rideLines, rideMessages, type RideData } from '../src/carpool';
import { reminders, type Member } from '../src/logic';

const member = (id: string, firstName: string, profile: Member['profile'] = 'adult', parentIds: string[] = []): Member =>
  ({ id, firstName, profile, parentIds, householdId: 'h1', uid: null });
const MEMBERS = [
  member('camille', 'Camille'), member('ines', 'Inès'), member('sam', 'Sam'),
  member('noe', 'Noé', 'childWithoutApp', ['ines']), member('lou', 'Lou', 'childWithApp', ['ines']),
];
const ride = (passengers: string[], seats = 3): RideData => ({ seats, from: null, leaveAt: null, passengers });

describe('carpool, without the emulator', () => {
  test('the driver hears who gets in, with the seats left, directly', () => {
    const [m] = rideMessages('e1', 'Galette', 'camille', ride([]), ride(['ines', 'noe']), 'ines', MEMBERS);
    assert.deepEqual(m.recipientIds, ['camille']);
    assert.equal(m.notice.title, 'Inès et Noé montent avec toi');
    assert.equal(m.notice.body, 'Galette. Il reste 1 place.');
    assert.equal(m.notice.direct, true);
    assert.equal(m.notice.eventId, 'e1');
  });

  test('and who gets out, also when the server takes them out', () => {
    const [m] = rideMessages('e1', 'Galette', 'camille', ride(['sam'], 1), ride([], 1), null, MEMBERS);
    assert.equal(m.notice.title, 'Sam ne monte plus avec toi');
    assert.equal(m.notice.body, 'Galette. Il reste 1 place.');
    const [full] = rideMessages('e1', 'Galette', 'camille', ride([], 1), ride(['sam'], 1), 'sam', MEMBERS);
    assert.equal(full.notice.body, 'Galette. C\'est complet.');
  });

  test('the driver is not told of their own change; those let out are', () => {
    const msgs = rideMessages('e1', 'Galette', 'camille', ride(['sam', 'noe']), ride([]), 'camille', MEMBERS);
    assert.equal(msgs.length, 1);
    assert.deepEqual(msgs[0].recipientIds, ['sam', 'ines']);
    assert.equal(msgs[0].notice.title, 'Camille ne peut plus t\'emmener');
    assert.deepEqual(rideMessages('e1', 'Galette', 'camille', ride([]), ride([], 4), 'camille', MEMBERS), []);
  });

  test('a car withdrawn tells its passengers, a child without the app through their adults', () => {
    const [m] = rideMessages('e1', 'Galette', 'camille', ride(['lou', 'noe']), null, null, MEMBERS);
    assert.deepEqual(m.recipientIds, ['lou', 'ines']);
    assert.equal(m.notice.title, 'Camille ne prend plus sa voiture');
    assert.deepEqual(rideMessages('e1', 'Galette', 'camille', ride([]), null, 'camille', MEMBERS), []);
  });

  test('the reminder of the day before says who I take, or who I go with', () => {
    const lines = rideLines([{ driverId: 'camille', ride: ride(['sam', 'noe']) }], MEMBERS);
    assert.deepEqual(lines.get('camille'), ['Tu emmènes Sam et Noé']);
    assert.deepEqual(lines.get('sam'), ['Tu pars avec Camille']);
    assert.deepEqual(lines.get('ines'), ['Noé part avec Camille']);
    const event = { title: 'Galette', date: '2026-12-24', time: null, place: null, note: null, targetType: 'family', targetMemberId: null, householdId: null };
    const all = reminders(MEMBERS, 'e1', event, new Map([['camille', 'yes'], ['sam', 'yes']]), new Set(), new Map(), lines);
    assert.equal(all.find((r) => r.recipientId === 'camille')?.notice.body, 'Journée\nTu emmènes Sam et Noé');
    assert.equal(all.find((r) => r.recipientId === 'sam')?.notice.body, 'Journée\nTu pars avec Camille');
  });
});

describe('carpool against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'carpool-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const t0 = Timestamp.fromMillis(0);

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('events'));
    await db.doc('events/e1').set({ title: 'Galette', date: '2026-12-24' });
    await db.doc('events/e1/rides/camille').set({ seats: 3, from: null, leaveAt: null, passengers: ['sam', 'ines'], createdAt: t0, updatedAt: t0 });
    await db.doc('events/e1/rides/sam').set({ seats: 2, from: null, leaveAt: null, passengers: [], createdAt: t0, updatedAt: t0 });
    await db.doc('events/e1/rideNeeds/sam').set({ askedBy: 'sam', updatedAt: t0 });
    await db.doc('events/e1/rideNeeds/noe').set({ askedBy: 'ines', updatedAt: t0 });
  });

  test('« Je ne viens plus » takes the member out of every car, with their offer and their request', async () => {
    await clearRidesOf(db, 'e1', 'sam');
    assert.deepEqual((await db.doc('events/e1/rides/camille').get()).get('passengers'), ['ines']);
    assert.equal((await db.doc('events/e1/rides/sam').get()).exists, false);
    assert.equal((await db.doc('events/e1/rideNeeds/sam').get()).exists, false);
    assert.equal((await db.doc('events/e1/rideNeeds/noe').get()).exists, true);
  });

  test('getting in answers the request for a seat, and the driver is told', async () => {
    const sent: { ids: string[]; title: string }[] = [];
    const count = await onRideChanged(db, async (ids, n) => { sent.push({ ids, title: n.title }); return ids.length; },
      'e1', 'camille', ride(['sam', 'ines']), ride(['sam', 'ines', 'noe']), 'ines', MEMBERS);
    assert.equal(count, 1);
    assert.deepEqual(sent, [{ ids: ['camille'], title: 'Noé monte avec toi' }]);
    assert.equal((await db.doc('events/e1/rideNeeds/noe').get()).exists, false);
  });

  test('getting in one car takes the member out of another', async () => {
    await db.doc('events/e1/rides/sam').update({ passengers: ['noe'] });
    await db.doc('events/e1/rides/camille').update({ passengers: ['sam', 'ines', 'noe'] });
    await onRideChanged(db, async () => 0, 'e1', 'camille', ride(['sam', 'ines']), ride(['sam', 'ines', 'noe']), 'ines', MEMBERS);
    assert.deepEqual((await db.doc('events/e1/rides/sam').get()).get('passengers'), []);
    assert.deepEqual((await db.doc('events/e1/rides/camille').get()).get('passengers'), ['sam', 'ines', 'noe']);
  });

  test('a member who leaves the family leaves the cars to come', async () => {
    await db.doc('events/past').set({ title: 'Pique-nique', date: '2026-06-01' });
    await db.doc('events/past/rides/camille').set({ seats: 2, from: null, leaveAt: null, passengers: ['ines'], createdAt: t0, updatedAt: t0 });
    await clearRidesOfMember(db, 'ines', '2026-11-21');
    assert.deepEqual((await db.doc('events/e1/rides/camille').get()).get('passengers'), ['sam']);
    assert.deepEqual((await db.doc('events/past/rides/camille').get()).get('passengers'), ['ines']);
  });

  test('nothing is sent once the event is gone', async () => {
    await db.doc('events/e1').delete();
    assert.equal(await onRideChanged(db, async () => 1, 'e1', 'camille', ride(['sam']), null, null, MEMBERS), 0);
  });
});
