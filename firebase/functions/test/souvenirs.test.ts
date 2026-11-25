// Souvenirs (PRD, récit 34), against the Firestore emulator. Fictional family only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import {
  countGesture, countHelpGiven, deleteSouvenirsOf, giveSouvenir, giveYearSouvenirs, isNewWeeklyAnswer, makeSouvenirsPublicOnce, SOUVENIRS, SOUVENIRS_PUBLIC, souvenirMark,
} from '../src/souvenirs';
import { Sender } from '../src/store';

describe('the list', () => {
  test('keys are unique, and a counted souvenir has its threshold', () => {
    assert.equal(new Set(SOUVENIRS.map((s) => s.key)).size, SOUVENIRS.length);
    assert.ok(SOUVENIRS.every((s) => (s.counter === undefined) === (s.threshold === undefined)));
  });

  test('a real answer counts once: not « Passer », not an edit', () => {
    assert.equal(isNewWeeklyAnswer(undefined, { skipped: false }), true);
    assert.equal(isNewWeeklyAnswer({ skipped: true }, { skipped: false }), true);
    assert.equal(isNewWeeklyAnswer({ skipped: false }, { skipped: false }), false);
    assert.equal(isNewWeeklyAnswer(undefined, { skipped: true }), false);
    assert.equal(isNewWeeklyAnswer({ skipped: false }, undefined), false);
  });
});

describe('against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'souvenirs-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const now = new Date('2026-12-01T17:00:00Z');

  function fakeSender() {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  beforeEach(async () => {
    for (const c of ['members', 'souvenirCounters', 'souvenirMarks', 'helpRequests']) await db.recursiveDelete(db.collection(c));
    const batch = db.batch();
    batch.set(db.doc('config/app'), { features: { souvenirs: true } });
    for (const [id, profile] of [['camille', 'adult'], ['ines', 'adult'], ['lou', 'childWithoutApp']]) {
      batch.set(db.doc(`members/${id}`), { firstName: id, profile, parentIds: [] });
      batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: now });
    }
    batch.set(db.doc('members/ines/settings/notifications'), { souvenirs: false });
    await batch.commit();
  });

  test('a souvenir is given once, public (récit 114), and its member is told once', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await giveSouvenir(db, send, 'camille', 'firstGoodNews', now), true);
    assert.equal(await giveSouvenir(db, send, 'camille', 'firstGoodNews', now), false);
    const s = await db.doc('members/camille/souvenirs/firstGoodNews').get();
    assert.equal(s.get('shown'), true);
    assert.deepEqual(sent.map((x) => x.token), ['token-camille']);
    assert.equal(sent[0].notice.channel, 'souvenirs');
    assert.equal(sent[0].notice.souvenirKey, 'firstGoodNews');
  });

  test('once: the souvenirs given while private become public, and never again after', async () => {
    await db.recursiveDelete(db.collection('migrations'));
    await db.doc('members/camille/souvenirs/firstGoodNews').set({ key: 'firstGoodNews', earnedAt: now, shown: false });
    await db.doc('members/camille/souvenirs/oneYear').set({ key: 'oneYear', earnedAt: now, shown: true });
    await db.doc('members/ines/souvenirs/firstArrival').set({ key: 'firstArrival', earnedAt: now, shown: false });
    assert.equal(await makeSouvenirsPublicOnce(db, now), 2);
    assert.equal((await db.doc('members/camille/souvenirs/firstGoodNews').get()).get('shown'), true);
    assert.equal((await db.doc('members/ines/souvenirs/firstArrival').get()).get('shown'), true);
    await db.doc('members/ines/souvenirs/helpGiven5').set({ key: 'helpGiven5', earnedAt: now, shown: false });
    assert.equal(await makeSouvenirsPublicOnce(db, now), null);
    assert.equal((await db.doc(SOUVENIRS_PUBLIC).get()).get('count'), 2);
  });

  test('nobody is told when the module is off, the member turned it off or has no app, but the souvenir is kept', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await giveSouvenir(db, send, 'ines', 'firstArrival', now), true);
    assert.equal(await giveSouvenir(db, send, 'lou', 'firstArrival', now), true);
    await db.doc('config/app').set({ features: { souvenirs: false } });
    assert.equal(await giveSouvenir(db, send, 'camille', 'firstArrival', now), true);
    assert.deepEqual(sent, []);
    assert.equal((await db.doc('members/lou/souvenirs/firstArrival').get()).exists, true);
  });

  test('a counted gesture counts once, and the souvenir comes at its threshold', async () => {
    const { send } = fakeSender();
    for (let i = 1; i <= 9; i++) await countGesture(db, send, 'camille', 'reactions', souvenirMark.reaction(`n${i}`, 'camille'), now);
    assert.deepEqual(await countGesture(db, send, 'camille', 'reactions', souvenirMark.reaction('n9', 'camille'), now), []);
    assert.equal((await db.doc('members/camille/souvenirs/reactions10').get()).exists, false);
    assert.deepEqual(await countGesture(db, send, 'camille', 'reactions', souvenirMark.reaction('n10', 'camille'), now), ['reactions10']);
    assert.equal((await db.doc('souvenirCounters/camille').get()).get('reactions'), 10);
  });

  test('the first real answer gives its souvenir at once', async () => {
    const { send } = fakeSender();
    assert.deepEqual(
      await countGesture(db, send, 'camille', 'weeklyAnswers', souvenirMark.weeklyAnswer('2026-11-30', 'camille'), now),
      ['firstWeeklyAnswer'],
    );
  });

  test('a coup de main counts for its volunteer the day after, once', async () => {
    const { send } = fakeSender();
    const base = { title: 'Garder Lou', authorId: 'ines', volunteerId: 'camille', date: '2026-11-30' };
    await db.doc('helpRequests/r1').set({ ...base, status: 'taken' });
    await db.doc('helpRequests/r2').set({ ...base, status: 'open', volunteerId: null });
    await db.doc('helpRequests/r3').set({ ...base, status: 'taken', date: '2026-12-01' });
    assert.equal(await countHelpGiven(db, send, '2026-11-30', now), 1);
    assert.equal(await countHelpGiven(db, send, '2026-11-30', now), 1); // Counted again: the mark stops it.
    assert.equal((await db.doc('souvenirCounters/camille').get()).get('helpGiven'), 1);
  });

  test('a year after the member was added, « Un an dans Familia »', async () => {
    const { send } = fakeSender();
    assert.deepEqual(await giveYearSouvenirs(db, send, new Date()), []);
    const inAYear = new Date(Date.now() + 366 * 24 * 3600 * 1000);
    assert.deepEqual((await giveYearSouvenirs(db, send, inAYear)).sort(), ['camille', 'ines', 'lou']);
    assert.deepEqual(await giveYearSouvenirs(db, send, inAYear), []);
  });

  test('a member removed takes their souvenirs, counters and gesture marks (EXPERT-1)', async () => {
    const { send } = fakeSender();
    await giveSouvenir(db, send, 'camille', 'firstGoodNews', now);
    await countGesture(db, send, 'camille', 'reactions', 'reaction-n1-camille', now);
    await countGesture(db, send, 'ines', 'reactions', 'reaction-n1-ines', now);
    assert.equal((await db.doc('souvenirMarks/reaction-n1-camille').get()).exists, true);
    await deleteSouvenirsOf(db, 'camille');
    assert.equal((await db.collection('members/camille/souvenirs').get()).size, 0);
    assert.equal((await db.doc('souvenirCounters/camille').get()).exists, false);
    assert.equal((await db.collection('souvenirMarks').where('memberId', '==', 'camille').get()).size, 0);
    assert.equal((await db.doc('souvenirMarks/reaction-n1-ines').get()).exists, true);
  });
});
