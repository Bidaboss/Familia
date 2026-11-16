import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import { isSunday, recapNotice, sendWeeklyRecap, weekNews } from '../src/recap';
import { Sender } from '../src/store';

describe('the recap of Sunday, without the emulator', () => {
  test('it says what the screen holds, or a word when the week was quiet', () => {
    const n = recapNotice('2026-11-22', { photos: 7, birthdays: 1, challengePercent: 60, challengeDone: false, trips: 2 });
    assert.equal(n.title, 'La semaine de la famille');
    assert.equal(n.body, '7 photos, 1 anniversaire à venir, défi à 60 %, 2 voyages en cours.');
    assert.equal(n.channel, 'recap');
    assert.equal(n.recapWeek, '2026-11-22');
    const quiet = recapNotice('2026-11-22', { photos: 0, birthdays: 0, challengePercent: null, challengeDone: true, trips: 0 });
    assert.equal(quiet.body, 'Défi réussi.');
    assert.equal(recapNotice('2026-11-22', { photos: 0, birthdays: 0, challengePercent: null, challengeDone: false, trips: 0 }).body,
      'Les nouvelles de la famille, en un coup d\'œil.');
  });

  test('Sunday is the day in Paris', () => {
    assert.equal(isSunday(new Date('2026-11-22T17:00:00Z')), true);
    assert.equal(isSunday(new Date('2026-11-22T23:30:00Z')), false); // Already Monday in Paris.
    assert.equal(isSunday(new Date('2026-11-21T17:00:00Z')), false);
  });
});

describe('the recap of Sunday against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'recap-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  // 18 h in Paris, Sunday 22/11/2026.
  const sunday = new Date('2026-11-22T17:00:00Z');

  function fakeSender() {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  beforeEach(async () => {
    for (const c of ['members', 'weeklyRecaps', 'goodNews', 'trips', 'challenges']) await db.recursiveDelete(db.collection(c));
    const batch = db.batch();
    batch.set(db.doc('config/app'), { features: { recap: true, tree: true } });
    for (const [id, profile, birthDate] of [['camille', 'adult', '1980-11-30'], ['ines', 'adult', '1985-06-01'], ['lou', 'childWithoutApp', '2020-12-05']]) {
      batch.set(db.doc(`members/${id}`), { firstName: id, profile, parentIds: [], birthDate });
      batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: sunday });
    }
    batch.set(db.doc('goodNews/n1'), { photoIds: ['a', 'b'], createdAt: new Date('2026-11-20T10:00:00Z') });
    batch.set(db.doc('goodNews/n0'), { photoIds: ['c'], createdAt: new Date('2026-11-10T10:00:00Z') });
    batch.set(db.doc('trips/t1'), { startDate: '2026-11-20', endDate: '2026-11-25', readerIds: ['camille', 'ines'] });
    batch.set(db.doc('trips/t2'), { startDate: '2026-12-20', endDate: '2026-12-27', readerIds: ['camille', 'ines'] });
    // A surprise for Inès: she is not among its readers.
    batch.set(db.doc('trips/t3'), { startDate: '2026-11-21', endDate: '2026-11-23', readerIds: ['camille'] });
    batch.set(db.doc('challenges/2026-11'), { title: 'x', emoji: '', measure: 'goodNews', target: 10, progress: 4, achievedAt: null });
    await batch.commit();
  });

  test('the news of the week: its photos, the birthdays of the next 14 days, the gauge, the trips under way', async () => {
    assert.deepEqual(await weekNews(db, sunday, 'ines'), { photos: 2, birthdays: 2, challengePercent: 40, challengeDone: false, trips: 1 });
  });

  test('once a Sunday, to the members with the app, not those who turned it off', async () => {
    await db.doc('members/ines/settings/notifications').set({ recap: false });
    const { sent, send } = fakeSender();
    assert.equal(await sendWeeklyRecap(db, send, new Date('2026-11-21T17:00:00Z')), null); // Saturday.
    assert.equal(await sendWeeklyRecap(db, send, sunday), 1);
    assert.deepEqual(sent.map((s) => s.token), ['token-camille']);
    assert.equal(sent[0].notice.body, '2 photos, 2 anniversaires à venir, défi à 40 %, 2 voyages en cours.');
    assert.equal(await sendWeeklyRecap(db, send, new Date('2026-11-22T17:30:00Z')), null);
    assert.equal(sent.length, 1);
  });

  test('a trip is counted only for the members who may see it, so a surprise stays hidden', async () => {
    assert.equal((await weekNews(db, sunday, 'camille')).trips, 2);
    assert.equal((await weekNews(db, sunday, 'ines')).trips, 1);
    const { sent, send } = fakeSender();
    assert.equal(await sendWeeklyRecap(db, send, sunday), 2);
    const byToken = Object.fromEntries(sent.map((s) => [s.token, s.notice.body]));
    assert.equal(byToken['token-camille'], '2 photos, 2 anniversaires à venir, défi à 40 %, 2 voyages en cours.');
    assert.equal(byToken['token-ines'], '2 photos, 2 anniversaires à venir, défi à 40 %, 1 voyage en cours.');
  });

  test('module off: nothing is sent', async () => {
    await db.doc('config/app').set({ features: { recap: false } });
    const { sent, send } = fakeSender();
    assert.equal(await sendWeeklyRecap(db, send, sunday), null);
    assert.equal(sent.length, 0);
  });
});
