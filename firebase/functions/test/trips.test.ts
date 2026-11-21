// Trips (V3, récit 14). Runs against the Firestore emulator (npm run test:functions in firebase/).
// Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { Member, Notice } from '../src/logic';
import { Sender } from '../src/store';
import {
  arrivalNotice, deleteStepWord, deleteTripContents, isNewArrival, joinNames, notifyArrival, sendArrivalReminders, StepData, TripData, withApp,
} from '../src/trips';

assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');

const app = initializeApp({ projectId: 'demo-familia' }, 'trips-test');
const db = getFirestore(app);

after(() => deleteApp(app));

function fakeSender() {
  const sent: { token: string; notice: Notice }[] = [];
  const send: Sender = async (token, notice) => {
    sent.push({ token, notice });
    return 'ok';
  };
  return { sent, send };
}

function member(firstName: string, profile = 'adult', uid: string | null = null) {
  return { firstName, email: null, birthDate: null, householdId: 'h1', profile, isAdmin: false, parentIds: [], uid };
}

const corse: TripData = { title: 'Corse', participantIds: ['lou', 'sam', 'noe'], readerIds: ['lou', 'sam', 'noe', 'ines', 'max'] };

function step(overrides: Partial<StepData> = {}) {
  return {
    type: 'flight', place: 'Ajaccio', departDate: '2027-07-10', departTime: '08:15', arriveDate: '2027-07-10', arriveTime: '09:40',
    note: null, remindAt: null, arrivedAt: null, arrivedBy: null, arrivedByName: null, updatedAt: new Date(),
    ...overrides,
  };
}

const members: Member[] = [
  { id: 'lou', firstName: 'Lou', profile: 'adult', parentIds: [], uid: 'uid-lou' },
  { id: 'sam', firstName: 'Sam', profile: 'adult', parentIds: [], uid: null },
  { id: 'noe', firstName: 'Noé', profile: 'childWithoutApp', parentIds: ['lou'], uid: null },
  { id: 'ines', firstName: 'Inès', profile: 'adult', parentIds: [], uid: null },
];

describe('logic', () => {
  test('only the first « bien arrivé » counts as an arrival', () => {
    const t = Timestamp.fromMillis(1000);
    assert.equal(isNewArrival(step(), step({ arrivedAt: t })), true);
    assert.equal(isNewArrival(step({ arrivedAt: t }), step({ arrivedAt: t, place: 'Bastia' })), false);
    assert.equal(isNewArrival(step(), step({ place: 'Bastia' })), false);
  });

  test('a child without the app, the author and members who left are not notified', () => {
    assert.deepEqual(withApp(members, ['lou', 'sam', 'noe', 'ines', 'gone'], 'sam'), ['ines', 'lou']);
  });

  test('names are joined in French', () => {
    assert.equal(joinNames(['Sam']), 'Sam');
    assert.equal(joinNames(['Sam', 'Lou']), 'Lou et Sam');
    assert.equal(joinNames(['Sam', 'Noé', 'Lou']), 'Lou, Noé et Sam');
  });

  test('the notice names everyone of the trip, without guessing anyone\'s gender', () => {
    const notice = arrivalNotice('t1', corse, step(), 'Sam', members);
    assert.equal(notice.title, 'Arrivée à Ajaccio');
    assert.equal(notice.body, 'Corse : arrivée de Lou, Noé et Sam, signalée par Sam.');
    assert.equal(notice.tripId, 't1');
    const alone = arrivalNotice('t1', { ...corse, participantIds: ['sam'] }, step(), 'Sam', members);
    assert.equal(alone.body, 'Corse : Sam a signalé son arrivée.');
  });
});

describe('store', () => {
  beforeEach(async () => {
    await db.recursiveDelete(db.collection('members'));
    await db.recursiveDelete(db.collection('trips'));
    const batch = db.batch();
    batch.set(db.doc('members/lou'), member('Lou', 'adult', 'uid-lou'));
    batch.set(db.doc('members/sam'), member('Sam'));
    batch.set(db.doc('members/noe'), member('Noé', 'childWithoutApp'));
    batch.set(db.doc('members/ines'), member('Inès'));
    batch.set(db.doc('members/max'), member('Max'));
    for (const id of ['lou', 'sam', 'ines', 'max']) batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: new Date() });
    batch.set(db.doc('trips/t1'), corse);
    await batch.commit();
  });

  test('« bien arrivé » reaches every follower with the app but its author', async () => {
    const { sent, send } = fakeSender();
    const count = await notifyArrival(db, send, 't1', step({ arrivedAt: Timestamp.now(), arrivedBy: 'lou' }), 'uid-lou');
    assert.equal(count, 3);
    assert.deepEqual(sent.map((s) => s.token).sort(), ['token-ines', 'token-max', 'token-sam']);
    assert.equal(sent[0].notice.body, 'Corse : arrivée de Lou, Noé et Sam, signalée par Lou.');
  });

  test('a member unticked from the followers is never told', async () => {
    await db.doc('trips/t1').update({ readerIds: ['lou', 'sam', 'noe', 'ines'] });
    const { sent, send } = fakeSender();
    await notifyArrival(db, send, 't1', step({ arrivedAt: Timestamp.now(), arrivedBy: 'lou' }), 'uid-lou');
    assert.ok(!sent.some((s) => s.token === 'token-max'));
  });

  test('3 hours after the planned arrival, the participants are reminded once, the family is not', async () => {
    const now = new Date('2027-07-10T12:00:00Z');
    await db.doc('trips/t1/steps/due').set(step({ remindAt: Timestamp.fromDate(new Date('2027-07-10T11:40:00Z')) }));
    await db.doc('trips/t1/steps/later').set(step({ place: 'Bastia', remindAt: Timestamp.fromDate(new Date('2027-07-10T15:00:00Z')) }));
    const { sent, send } = fakeSender();
    assert.equal(await sendArrivalReminders(db, send, now), 2);
    assert.deepEqual(sent.map((s) => s.token).sort(), ['token-lou', 'token-sam']);
    assert.equal(sent[0].notice.channel, 'trips');
    assert.equal(sent[0].notice.body, 'Corse, Ajaccio : touche « Bien arrivé » pour rassurer la famille.');
    assert.equal((await db.doc('trips/t1/steps/due').get()).get('remindAt'), null);
    assert.ok((await db.doc('trips/t1/steps/later').get()).get('remindAt'));
    // Nothing is sent twice.
    assert.equal(await sendArrivalReminders(db, fakeSender().send, now), 0);
  });

  test('a step of a deleted trip is never reminded', async () => {
    await db.doc('trips/t2/steps/orphan').set(step({ remindAt: Timestamp.fromMillis(0) }));
    const { sent, send } = fakeSender();
    await sendArrivalReminders(db, send, new Date());
    assert.equal(sent.length, 0);
  });

  test('deleting a trip deletes its steps', async () => {
    await db.doc('trips/t1/steps/s1').set(step());
    await db.doc('trips/t1').delete();
    await deleteTripContents(db, 't1');
    assert.equal((await db.collection('trips/t1/steps').get()).size, 0);
  });
});

describe('le mot de l\'étape (récit 88)', () => {
  beforeEach(async () => {
    await db.recursiveDelete(db.collection('trips'));
  });

  test('the word of a deleted step goes with its hearts and comments, the others stay', async () => {
    await db.doc('trips/t1/words/s1').set({ text: 'Bien arrivés', hearts: { lou: true } });
    await db.doc('trips/t1/words/s1/comments/c1').set({ text: 'Super' });
    await db.doc('trips/t1/words/s2').set({ text: 'Autre étape' });
    await deleteStepWord(db, 't1', 's1');
    assert.equal((await db.doc('trips/t1/words/s1').get()).exists, false);
    assert.equal((await db.collection('trips/t1/words/s1/comments').get()).size, 0);
    assert.equal((await db.doc('trips/t1/words/s2').get()).exists, true);
  });

  test('deleting the trip deletes its words', async () => {
    await db.doc('trips/t1/words/s1').set({ text: 'Bien arrivés' });
    await deleteTripContents(db, 't1');
    assert.equal((await db.collection('trips/t1/words').get()).size, 0);
  });
});
