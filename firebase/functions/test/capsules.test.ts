// Capsule temporelle (PRD, récit 39): when a capsule opens, against the Firestore emulator
// (npm run test:functions in firebase/). Fictional family only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { CapsuleData, countLeftovers, deleteLeftovers, isDue, openDueCapsules } from '../src/capsules';
import { Notice } from '../src/logic';
import { Sender } from '../src/store';

describe('when a capsule opens', () => {
  const capsule = (openAt: string, openedFor: string[] = []): CapsuleData => ({ recipientIds: ['ines'], openAt, openedFor, authorName: 'Camille' });

  test('on its day in Paris or later, once', () => {
    assert.equal(isDue(capsule('2027-06-26'), '2027-06-25'), false);
    assert.equal(isDue(capsule('2027-06-26'), '2027-06-26'), true);
    assert.equal(isDue(capsule('2027-06-26'), '2027-07-01'), true);
    assert.equal(isDue(capsule('2027-06-26', ['ines']), '2027-06-26'), false);
  });
});

describe('against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'capsules-test');
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

  // Saturday 26 June 2027, 8 h 30 in Paris (6:30 UTC).
  const morning = new Date('2027-06-26T06:30:00Z');
  // The same day at 0 h 30 in Paris (22:30 UTC the day before): still the night.
  const night = new Date('2027-06-25T22:30:00Z');

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('members'));
    await db.recursiveDelete(db.collection('capsules'));
    await db.recursiveDelete(db.collection('capsuleLeftovers'));
    const batch = db.batch();
    batch.set(db.doc('config/app'), { features: { capsules: true } });
    for (const id of ['camille', 'ines', 'noe']) {
      batch.set(db.doc(`members/${id}`), { firstName: id, profile: 'adult', parentIds: [] });
      batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: new Date() });
    }
    const sealed = { text: 'Pour tes 12 ans', photoCount: 0, authorId: 'camille', authorName: 'Camille', openedFor: [], createdAt: new Date(0) };
    batch.set(db.doc('capsules/today'), { ...sealed, recipientIds: ['ines', 'noe'], openAt: '2027-06-26' });
    batch.set(db.doc('capsules/later'), { ...sealed, recipientIds: ['ines'], openAt: '2027-06-27' });
    await batch.commit();
  });

  test('the capsule of the day waits for 8 h: nothing opens nor rings in the night', async () => {
    const { sent, send } = fakeSender();
    assert.deepEqual(await openDueCapsules(db, send, night), []);
    assert.deepEqual(sent, []);
    assert.deepEqual((await db.doc('capsules/today').get()).get('openedFor'), []);
  });

  test('the capsule of the day opens for its recipients, who are told once', async () => {
    const { sent, send } = fakeSender();
    assert.deepEqual(await openDueCapsules(db, send, morning), ['today']);
    const opened = await db.doc('capsules/today').get();
    assert.deepEqual(opened.get('openedFor'), ['ines', 'noe']);
    assert.ok(opened.get('openedAt'));
    assert.deepEqual((await db.doc('capsules/later').get()).get('openedFor'), []);
    assert.deepEqual(sent.map((s) => s.token), ['token-ines', 'token-noe']);
    assert.equal(sent[0].notice.channel, 'capsules');
    assert.equal(sent[0].notice.capsuleId, 'today');

    assert.deepEqual(await openDueCapsules(db, send, morning), []);
    assert.equal(sent.length, 2);
  });

  test('with the module off, every capsule waits sealed', async () => {
    await db.doc('config/app').set({ features: { capsules: false } });
    const { sent, send } = fakeSender();
    assert.deepEqual(await openDueCapsules(db, send, morning), []);
    assert.equal(sent.length, 0);
  });

  test('an opened capsule is not read again at the next runs', async () => {
    const { send } = fakeSender();
    await db.doc('capsules/old').set({ text: 'x', photoCount: 0, authorId: 'camille', authorName: 'Camille', recipientIds: ['ines'], openAt: '2027-01-01', openedFor: ['ines'], createdAt: new Date(0) });
    assert.deepEqual(await openDueCapsules(db, send, morning), ['today']);
    assert.deepEqual((await db.doc('capsules/old').get()).get('openedFor'), ['ines']);
  });

  test('an author who leaves: their sealed capsules are counted, still open on their day', async () => {
    await db.doc('members/camille').delete();
    assert.equal(await countLeftovers(db, 'camille', 'Camille', morning), 2);
    const left = await db.doc('capsuleLeftovers/camille').get();
    assert.equal(left.get('memberName'), 'Camille');
    assert.equal(left.get('deleteAsked'), false);
    assert.equal(await countLeftovers(db, 'noe', 'Noé', morning), 0);
    assert.equal((await db.doc('capsuleLeftovers/noe').get()).exists, false);

    const { sent, send } = fakeSender();
    assert.deepEqual(await openDueCapsules(db, send, morning), ['today']);
    assert.equal(sent.length, 2);
    assert.equal((await db.doc('capsuleLeftovers/camille').get()).get('count'), 1);
  });

  test('once the admin principal asks, the sealed ones go unread, the opened ones stay', async () => {
    await db.doc('members/camille').delete();
    await countLeftovers(db, 'camille', 'Camille', morning);
    await openDueCapsules(db, fakeSender().send, morning);
    assert.equal(await deleteLeftovers(db, 'camille'), 0, 'nothing before the request');
    await db.doc('capsuleLeftovers/camille').update({ deleteAsked: true });
    assert.equal(await deleteLeftovers(db, 'camille'), 1);
    assert.equal((await db.doc('capsules/later').get()).exists, false);
    assert.equal((await db.doc('capsules/today').get()).exists, true);
    assert.equal((await db.doc('capsuleLeftovers/camille').get()).exists, false);
  });

  test('a member back in the family keeps their capsules', async () => {
    await db.doc('capsuleLeftovers/camille').set({ memberName: 'Camille', count: 2, deleteAsked: true, updatedAt: morning });
    assert.equal(await deleteLeftovers(db, 'camille'), 0);
    assert.equal((await db.doc('capsules/later').get()).exists, true);
    assert.equal((await db.doc('capsuleLeftovers/camille').get()).exists, false);
  });
});
