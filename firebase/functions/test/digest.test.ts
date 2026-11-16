import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { digestNotice, queueDigest, sendDigests } from '../src/digest';
import { Notice } from '../src/logic';
import { giveSouvenir } from '../src/souvenirs';
import { Sender } from '../src/store';

describe('the résumé of the day, without the emulator', () => {
  const quiet = { challengePercent: null, leaves: 0 };

  test('it says what waits, then the gauge and the new leaves, never them alone', () => {
    assert.equal(digestNotice({ souvenirs: [], challengeDone: false, weekly: null }, { challengePercent: 60, leaves: 80 }, 12), null);
    const n = digestNotice({ souvenirs: ['reactions10'], challengeDone: false, weekly: '2026-11-23' }, { challengePercent: 60, leaves: 80 }, 12);
    assert.equal(n?.title, 'Aujourd\'hui dans Familia');
    assert.equal(n?.body, 'Nouveau souvenir, question de la semaine, défi à 60 %, 12 nouvelles feuilles.');
    assert.equal(n?.channel, 'weekly');
    assert.equal(n?.weeklyQuestionId, undefined);
  });

  test('a challenge achieved replaces its gauge; the question alone opens it', () => {
    const done = digestNotice({ souvenirs: ['a', 'b'], challengeDone: true, weekly: null }, { challengePercent: null, leaves: 0 }, 0);
    assert.equal(done?.body, '2 nouveaux souvenirs, défi réussi, l\'arbre fleurit.');
    assert.equal(digestNotice({ souvenirs: [], challengeDone: false, weekly: '2026-11-23' }, quiet, 0)?.weeklyQuestionId, '2026-11-23');
  });
});

describe('the résumé of the day against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'digest-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  // 18 h 30 in Paris on 23/11/2026.
  const evening = new Date('2026-11-23T17:30:00Z');

  function fakeSender() {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  beforeEach(async () => {
    for (const c of ['members', 'engagementDigests', 'familyTree', 'challenges']) await db.recursiveDelete(db.collection(c));
    const batch = db.batch();
    batch.set(db.doc('config/app'), { features: { digest: true, souvenirs: true, tree: true } });
    batch.set(db.doc('members/camille'), { firstName: 'Camille', profile: 'adult', parentIds: [] });
    batch.set(db.doc('members/camille/devices/token-camille'), { updatedAt: evening });
    batch.set(db.doc('familyTree/current'), { leaves: 40 });
    batch.set(db.doc('challenges/2026-11'), { title: '12 petits bonheurs', emoji: '🧡', measure: 'goodNews', target: 10, progress: 6, achievedAt: null });
    await batch.commit();
  });

  test('the first souvenir is told at once, the next ones wait for the résumé', async () => {
    const { sent, send } = fakeSender();
    await giveSouvenir(db, send, 'camille', 'firstGoodNews', evening);
    assert.equal(sent.length, 1);
    assert.equal(sent[0].notice.title, 'Nouveau souvenir');
    await giveSouvenir(db, send, 'camille', 'firstArrival', evening);
    assert.equal(sent.length, 1);
    assert.deepEqual((await db.doc('engagementDigests/camille').get()).get('souvenirs'), ['firstArrival']);
  });

  test('one résumé a day, from 18 h, then nothing until something new waits', async () => {
    const { sent, send } = fakeSender();
    await queueDigest(db, ['camille'], { kind: 'weekly', weekId: '2026-11-23' }, evening);
    assert.equal(await sendDigests(db, send, new Date('2026-11-23T16:00:00Z')), 0); // 17 h in Paris.
    assert.equal(await sendDigests(db, send, evening), 1);
    assert.equal(sent[0].notice.body, 'Question de la semaine, défi à 60 %.');
    // A second run the same evening, and a souvenir after it: they wait for tomorrow.
    await queueDigest(db, ['camille'], { kind: 'souvenir', key: 'reactions10' }, evening);
    assert.equal(await sendDigests(db, send, new Date('2026-11-23T18:00:00Z')), 0);
    await db.doc('familyTree/current').set({ leaves: 47 });
    assert.equal(await sendDigests(db, send, new Date('2026-11-24T17:30:00Z')), 1);
    assert.equal(sent[1].notice.body, 'Nouveau souvenir, défi à 60 %, 7 nouvelles feuilles.');
    assert.equal(await sendDigests(db, send, new Date('2026-11-25T17:30:00Z')), 0);
  });
});
