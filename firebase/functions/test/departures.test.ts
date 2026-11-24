import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { departureDue, departureNotice, departureWindow, sendDepartureReminders, type DepartureStep } from '../src/departures';

const step = (type: string, departTime: string | null, place = 'Ajaccio'): DepartureStep => ({ type, place, departDate: '2027-07-10', departTime });

describe('departures, without the emulator', () => {
  test('a flight, a train or a bus; never a car, a destination or an activity', () => {
    for (const type of ['flight', 'train', 'bus']) assert.equal(departureDue(step(type, '10:00'), 'eve', '18:00'), true);
    for (const type of ['car', 'lodging', 'activity']) assert.equal(departureDue(step(type, '10:00'), 'eve', '18:00'), false);
  });

  test('the morning one only from 9 h, and not once gone', () => {
    assert.equal(departureDue(step('flight', '08:15'), 'morning', '07:00'), false);
    assert.equal(departureDue(step('flight', '09:00'), 'morning', '07:00'), true);
    assert.equal(departureDue(step('train', null), 'morning', '07:00'), true);
    assert.equal(departureDue(step('train', '10:00'), 'morning', '10:30'), false);
  });

  test('« Corse : départ demain », « Vol pour Ajaccio à 8 h 15. »', () => {
    const eve = departureNotice('t1', { title: 'Corse' }, step('flight', '08:15'), 'eve');
    assert.equal(eve.title, 'Corse : départ demain');
    assert.equal(eve.body, 'Vol pour Ajaccio à 8 h 15.');
    assert.equal(eve.tripId, 't1');
    assert.equal(eve.channel, 'trips');
    const morning = departureNotice('t1', { title: 'Corse' }, step('bus', null, 'Calvi'), 'morning');
    assert.equal(morning.title, "Corse : départ aujourd'hui");
    assert.equal(morning.body, 'Bus pour Calvi.');
  });

  test('the morning from 7 h, the day before from 18 h, in Paris', () => {
    assert.deepEqual(departureWindow(new Date('2027-07-10T04:30:00Z')), []); // 6 h 30
    assert.deepEqual(departureWindow(new Date('2027-07-10T05:00:00Z')), [{ kind: 'morning', day: '2027-07-10', hhmm: '07:00' }]);
    assert.deepEqual(departureWindow(new Date('2027-07-09T16:30:00Z')).map((w) => [w.kind, w.day]), [['morning', '2027-07-09'], ['eve', '2027-07-10']]);
  });
});

describe('departures against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'departures-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const bodies: string[] = [];
  const sender = async (_token: string, n: { title: string; body: string }) => { bodies.push(`${n.title} | ${n.body}`); return 'ok' as const; };

  beforeEach(async () => {
    bodies.length = 0;
    for (const c of ['trips', 'members', 'remindersSent', 'config']) await db.recursiveDelete(db.collection(c));
    await db.doc('members/camille').set({ firstName: 'Camille', profile: 'adult', parentIds: [], householdId: 'h1' });
    await db.doc('members/noe').set({ firstName: 'Noé', profile: 'childWithoutApp', parentIds: ['camille'], householdId: 'h1' });
    await db.doc('members/sam').set({ firstName: 'Sam', profile: 'adult', parentIds: [], householdId: 'h2' });
    for (const id of ['camille', 'sam']) await db.doc(`members/${id}/devices/tok`).set({ updatedAt: new Date() });
    await db.doc('trips/t1').set({ title: 'Corse', participantIds: ['camille', 'noe'], readerIds: ['camille', 'noe', 'sam'] });
    await db.doc('trips/t1/steps/s1').set({ type: 'flight', place: 'Ajaccio', departDate: '2027-07-10', departTime: '10:15' });
    await db.doc('trips/t1/steps/s2').set({ type: 'car', place: 'Bonifacio', departDate: '2027-07-10', departTime: '14:00' });
  });

  test('once the day before and once the morning, to the participants with the app only', async () => {
    await sendDepartureReminders(db, sender, new Date('2027-07-09T15:30:00Z')); // 17 h 30: nothing yet
    assert.deepEqual(bodies, []);
    await sendDepartureReminders(db, sender, new Date('2027-07-09T16:00:00Z')); // 18 h
    await sendDepartureReminders(db, sender, new Date('2027-07-09T16:30:00Z')); // 18 h 30: already sent
    assert.deepEqual(bodies, ['Corse : départ demain | Vol pour Ajaccio à 10 h 15.']);
    bodies.length = 0;
    await sendDepartureReminders(db, sender, new Date('2027-07-10T06:00:00Z')); // 8 h on the day
    await sendDepartureReminders(db, sender, new Date('2027-07-10T06:30:00Z'));
    assert.deepEqual(bodies, ["Corse : départ aujourd'hui | Vol pour Ajaccio à 10 h 15."]);
  });
});
