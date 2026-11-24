// Trips (V3, récit 14). Runs against the Firestore emulator (npm run test:functions in firebase/).
// Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { Member, Notice } from '../src/logic';
import { Sender } from '../src/store';
import {
  ALERT_GAP_MS, alertNotice, arrivalNotice, deleteStepWord, deleteTripContents, isNewAlert, isNewArrival, joinNames, notifyAlert, notifyArrival,
  addTripPhotoToBurst, deleteStepFilms, deleteStepVoiceNotes, filmDay, filmFile, filmNotice, notifyFilm, voiceNoteFile, PHOTO_BURSTS, photoBurstDue, sendArrivalReminders, sendTripPhotoDigests, StepData, TripData, tripDocumentFile, withApp,
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

describe('« Prévenir ma famille » (récit 105)', () => {
  const at = (millis: number) => ({ authorId: 'lou', authorName: 'Lou', createdAt: Timestamp.fromMillis(millis) });

  test('a first alert, or one 10 minutes after the last, is new; a rewrite sooner is not', () => {
    assert.equal(isNewAlert(undefined, at(1000)), true);
    assert.equal(isNewAlert(at(1000), at(1000 + ALERT_GAP_MS)), true);
    assert.equal(isNewAlert(at(1000), at(1000 + ALERT_GAP_MS - 1)), false);
    assert.equal(isNewAlert(at(1000), undefined), false);
  });

  test('the notice asks for a call, rings at once and says nothing of a position', () => {
    const notice = alertNotice('t1', corse, 'Lou');
    assert.equal(notice.title, 'Corse : Lou prévient la famille');
    assert.equal(notice.body, 'Lou a touché « Prévenir ma famille ». Appelle dès que possible.');
    assert.equal(notice.direct, true);
    assert.equal(notice.tripId, 't1');
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

    test('every follower with the app but its author is told', async () => {
      const { sent, send } = fakeSender();
      assert.equal(await notifyAlert(db, send, 't1', 'lou', at(Date.now())), 3);
      assert.deepEqual(sent.map((s) => s.token).sort(), ['token-ines', 'token-max', 'token-sam']);
    });

    test('someone who does not go, or an alert signed by another, rings nobody', async () => {
      const { sent, send } = fakeSender();
      assert.equal(await notifyAlert(db, send, 't1', 'ines', { ...at(Date.now()), authorId: 'ines' }), 0);
      assert.equal(await notifyAlert(db, send, 't1', 'sam', at(Date.now())), 0);
      assert.equal(sent.length, 0);
    });
  });
});

describe('file of a removed document', () => {
  test('only the file at the place of this document', () => {
    assert.equal(tripDocumentFile('t1', 'd1', { ownerId: 'adult1', file: 'd1.pdf' }), 'trips/t1/documents/adult1/d1.pdf');
    assert.equal(tripDocumentFile('t1', 'd1', { ownerId: 'adult1', file: 'd1.png' }), 'trips/t1/documents/adult1/d1.png');
    assert.equal(tripDocumentFile('t1', 'd1', { ownerId: 'adult1', file: 'd2.pdf' }), null);
    assert.equal(tripDocumentFile('t1', 'd1', { ownerId: 'adult1', file: 'd1.exe' }), null);
    assert.equal(tripDocumentFile('t1', 'd1', { ownerId: '../x', file: 'd1.pdf' }), null);
    assert.equal(tripDocumentFile('t1', 'd1', { ownerId: 'adult1' }), null);
    assert.equal(tripDocumentFile('t1', 'd1', undefined), null);
  });
});

describe('photos groupées (récit 104, règle 34)', () => {
  const t0 = new Date('2027-07-11T14:00:00Z');
  const plus = (ms: number) => new Date(t0.getTime() + ms);

  beforeEach(async () => {
    for (const c of ['members', 'trips', PHOTO_BURSTS, 'notifyLog', 'burstQueue']) await db.recursiveDelete(db.collection(c));
    const batch = db.batch();
    batch.set(db.doc('members/lou'), member('Lou', 'adult', 'uid-lou'));
    batch.set(db.doc('members/sam'), member('Sam'));
    batch.set(db.doc('members/noe'), member('Noé', 'childWithoutApp'));
    batch.set(db.doc('members/ines'), member('Inès'));
    batch.set(db.doc('members/max'), member('Max'));
    batch.set(db.doc('members/zoe'), member('Zoé'));
    for (const id of ['lou', 'sam', 'ines', 'max', 'zoe']) batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: new Date() });
    batch.set(db.doc('trips/t1'), corse);
    await batch.commit();
  });

  test('a burst waits 10 min of calm, never more than 45 min after its first photo', () => {
    assert.equal(photoBurstDue(t0, t0).getTime(), plus(10 * 60000).getTime());
    assert.equal(photoBurstDue(t0, plus(40 * 60000)).getTime(), plus(45 * 60000).getTime());
  });

  test('no notice per photo: the burst goes once, 10 min after the last, to the followers only', async () => {
    const { sent, send } = fakeSender();
    await addTripPhotoToBurst(db, 't1', 'lou', t0);
    await addTripPhotoToBurst(db, 't1', 'lou', plus(5 * 60000));
    // 10 min after the first photo, the second one still holds it.
    assert.equal(await sendTripPhotoDigests(db, send, plus(11 * 60000)), 0);
    assert.equal(await sendTripPhotoDigests(db, send, plus(16 * 60000)), 3);
    // Zoé does not follow the trip; Lou, the only author, is not told of their own photos.
    assert.deepEqual(sent.map((s) => s.token).sort(), ['token-ines', 'token-max', 'token-sam']);
    assert.equal(sent[0].notice.title, 'Corse : 2 nouvelles photos');
    assert.equal(sent[0].notice.body, 'Par Lou.');
    assert.equal(sent[0].notice.tripId, 't1');
    assert.equal((await db.collection(PHOTO_BURSTS).doc('t1').get()).exists, false);
    assert.equal(await sendTripPhotoDigests(db, send, plus(60 * 60000)), 0);
  });

  test('a long send still goes 45 min after its first photo', async () => {
    const { sent, send } = fakeSender();
    for (let minute = 0; minute <= 44; minute += 4) await addTripPhotoToBurst(db, 't1', 'lou', plus(minute * 60000));
    assert.equal(await sendTripPhotoDigests(db, send, plus(46 * 60000)), 3);
    assert.equal(sent[0].notice.title, 'Corse : 12 nouvelles photos');
  });

  test('with several authors, every follower is told, the authors too', async () => {
    const { sent, send } = fakeSender();
    await addTripPhotoToBurst(db, 't1', 'lou', t0);
    await addTripPhotoToBurst(db, 't1', 'sam', t0);
    assert.equal(await sendTripPhotoDigests(db, send, plus(20 * 60000)), 4);
    assert.equal(sent[0].notice.body, 'Par Lou et Sam.');
  });

  test('a photo that comes while sending is kept for the next burst', async () => {
    const { sent, send: base } = fakeSender();
    let once = false;
    const send: Sender = async (token, notice) => {
      if (!once) {
        once = true;
        await addTripPhotoToBurst(db, 't1', 'sam', plus(20 * 60000));
      }
      return base(token, notice);
    };
    await addTripPhotoToBurst(db, 't1', 'lou', t0);
    await sendTripPhotoDigests(db, send, plus(20 * 60000));
    const left = await db.collection(PHOTO_BURSTS).doc('t1').get();
    assert.deepEqual(left.get('counts'), { sam: 1 });
    sent.length = 0;
    await sendTripPhotoDigests(db, base, plus(31 * 60000));
    assert.equal(sent[0].notice.title, 'Corse : une nouvelle photo');
    assert.equal(sent[0].notice.body, 'Par Sam.');
  });

  test('a removed trip tells nobody, and takes its burst with it', async () => {
    const { sent, send } = fakeSender();
    await addTripPhotoToBurst(db, 't1', 'lou', t0);
    await db.doc('trips/t1').delete();
    assert.equal(await sendTripPhotoDigests(db, send, plus(20 * 60000)), 0);
    assert.equal(sent.length, 0);
    assert.equal((await db.collection(PHOTO_BURSTS).doc('t1').get()).exists, false);
    await db.doc('trips/t1').set(corse);
    await addTripPhotoToBurst(db, 't1', 'lou', t0);
    await deleteTripContents(db, 't1');
    assert.equal((await db.collection(PHOTO_BURSTS).doc('t1').get()).exists, false);
  });
});

describe('notes vocales (récit 104, règle 35)', () => {
  beforeEach(async () => {
    await db.recursiveDelete(db.collection('trips'));
  });

  test('a removed step takes its voice notes, not those of the other steps', async () => {
    const notes = db.collection('trips/t1/voiceNotes');
    await notes.doc('v1').set({ authorId: 'lou', stepId: 's1', seconds: 10 });
    await notes.doc('v2').set({ authorId: 'sam', stepId: 's1', seconds: 20 });
    await notes.doc('v3').set({ authorId: 'lou', stepId: 's2', seconds: 30 });
    assert.equal(await deleteStepVoiceNotes(db, 't1', 's1'), 2);
    assert.deepEqual((await notes.get()).docs.map((d) => d.id), ['v3']);
    assert.equal(await deleteStepVoiceNotes(db, 't1', 's9'), 0);
  });

  test('the file of a removed note is at its own place only', () => {
    assert.equal(voiceNoteFile('t1', 'v1', { authorId: 'lou' }), 'trips/t1/voice/lou/v1.m4a');
    assert.equal(voiceNoteFile('t1', 'v1', { authorId: '../x' }), null);
    assert.equal(voiceNoteFile('t1', 'v-1', { authorId: 'lou' }), null);
    assert.equal(voiceNoteFile('t1', 'v1', undefined), null);
  });
});

describe('film de la journée (récit 108, règle 32)', () => {
  beforeEach(async () => {
    await db.recursiveDelete(db.collection('trips'));
    await db.recursiveDelete(db.collection('members'));
    await db.recursiveDelete(db.collection(PHOTO_BURSTS));
  });

  async function seedMembers() {
    await db.doc('members/lou').set(member('Lou', 'adult', 'uid-lou'));
    await db.doc('members/sam').set(member('Sam'));
    await db.doc('members/noe').set(member('Noé', 'childWithoutApp'));
    await db.doc('members/ines').set(member('Inès'));
    await db.doc('members/max').set(member('Max'));
    for (const id of ['lou', 'sam', 'ines', 'max']) await db.doc(`members/${id}/devices/token-${id}`).set({ updatedAt: new Date() });
  }

  test('the notice says the trip, the day, the place and who published', () => {
    assert.equal(filmDay('2027-07-12'), '12 juillet');
    assert.equal(filmDay('nope'), 'nope');
    const notice = filmNotice('t1', corse, 'Ajaccio, Corse', '2027-07-12', 'Lou');
    assert.equal(notice.title, 'Corse : le film du 12 juillet');
    assert.equal(notice.body, 'Ajaccio, par Lou. Touche pour le voir.');
    assert.equal(notice.tripId, 't1');
    assert.equal(filmNotice('t1', corse, null, '2027-07-12', null).body, 'par un participant. Touche pour le voir.');
  });

  test('the followers with the app are told once, the author left out, and the burst of photos goes', async () => {
    await seedMembers();
    await db.doc('trips/t1').set(corse);
    await db.doc('trips/t1/steps/s1').set(step({ place: 'Ajaccio, Corse' }));
    await addTripPhotoToBurst(db, 't1', 'sam', new Date());
    const { sent, send } = fakeSender();
    const count = await notifyFilm(db, send, 't1', { authorId: 'lou', authorName: 'Lou', stepId: 's1', day: '2027-07-12' });
    // Followers: lou (the author), sam, noe (no app), ines, max.
    assert.deepEqual(sent.map((s) => s.token).sort(), ['token-ines', 'token-max', 'token-sam']);
    assert.equal(sent.every((s) => s.notice.title === 'Corse : le film du 12 juillet'), true);
    assert.equal(count, sent.length);
    assert.equal((await db.collection(PHOTO_BURSTS).doc('t1').get()).exists, false);
  });

  test('a trip hidden from someone never tells them', async () => {
    await seedMembers();
    await db.doc('trips/t1').set({ ...corse, readerIds: ['lou', 'sam'] });
    await db.doc('trips/t1/steps/s1').set(step());
    const { sent, send } = fakeSender();
    await notifyFilm(db, send, 't1', { authorId: 'lou', authorName: 'Lou', stepId: 's1', day: '2027-07-12' });
    assert.deepEqual(sent.map((s) => s.token), ['token-sam']);
  });

  test('nothing for a trip that is gone or a record that is not a film', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await notifyFilm(db, send, 'gone', { authorId: 'lou', stepId: 's1', day: '2027-07-12' }), 0);
    assert.equal(await notifyFilm(db, send, 't1', undefined), 0);
    assert.equal(sent.length, 0);
  });

  test('a removed step takes its films, not those of the other steps', async () => {
    const films = db.collection('trips/t1/films');
    await films.doc('s1_20270712').set({ authorId: 'lou', stepId: 's1', day: '2027-07-12' });
    await films.doc('s1_20270713').set({ authorId: 'sam', stepId: 's1', day: '2027-07-13' });
    await films.doc('s2_20270712').set({ authorId: 'lou', stepId: 's2', day: '2027-07-12' });
    assert.equal(await deleteStepFilms(db, 't1', 's1'), 2);
    assert.deepEqual((await films.get()).docs.map((d) => d.id), ['s2_20270712']);
  });

  test('the file of a removed film is at its own place only', () => {
    assert.equal(filmFile('t1', 's1_20270712', { authorId: 'lou' }), 'trips/t1/films/lou/s1_20270712.mp4');
    assert.equal(filmFile('t1', 's1_20270712', { authorId: '../x' }), null);
    assert.equal(filmFile('t1', 's1', { authorId: 'lou' }), null);
    assert.equal(filmFile('t1', 's1_20270712', undefined), null);
  });
});
