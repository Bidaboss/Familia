// Question de la semaine: which question comes out (pure logic), then the publication, the count of
// answers and the cleanup against the Firestore emulator (npm run test:functions in firebase/).
// Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import { QUESTION_BANK } from '../src/questionBank';
import { Sender } from '../src/store';
import {
  countAnswers, deleteAnswersOf, FIRST_DAY, inSeason, pickQuestion, publishWeeklyQuestion, removeOldAnswerPhoto, REVEAL_AFTER_MS,
} from '../src/weeklyQuestion';

describe('which question comes out', () => {
  test('the bank holds 60 questions with distinct ids', () => {
    assert.equal(QUESTION_BANK.length, 60);
    assert.equal(new Set(QUESTION_BANK.map((q) => q.id)).size, 60);
  });

  test('the very first is the first of the bank', () => {
    assert.equal(pickQuestion(QUESTION_BANK, new Set(), FIRST_DAY)?.id, 'q01');
  });

  test('a seasonal question comes out in its season, first', () => {
    const q = pickQuestion(QUESTION_BANK, new Set(['q01']), '2026-12-07');
    assert.equal(q?.id, 'q59');
    assert.equal(pickQuestion(QUESTION_BANK, new Set(['q01', 'q59']), '2026-12-28')?.id, 'q60');
    const halloween = QUESTION_BANK.find((x) => x.id === 'q58')!;
    assert.equal(inSeason(halloween, '2027-10-04'), false);
    assert.equal(inSeason(halloween, '2027-10-25'), true);
  });

  test('out of season, a question not asked lately, never twice', () => {
    const asked = new Set<string>(['q01']);
    for (let week = 0; week < 40; week++) {
      const day = new Date(Date.UTC(2027, 2, 1 + 7 * week)).toISOString().slice(0, 10);
      const q = pickQuestion(QUESTION_BANK, asked, day);
      assert.ok(q, `a question for ${day}`);
      assert.ok(!asked.has(q.id));
      assert.ok(inSeason(q, day));
      asked.add(q.id);
    }
  });

  test('the same Monday always draws the same question', () => {
    const recent = new Set(['q01', 'q02']);
    assert.equal(pickQuestion(QUESTION_BANK, recent, '2027-03-08')?.id, pickQuestion(QUESTION_BANK, recent, '2027-03-08')?.id);
  });
});

describe('the photo of an answer', () => {
  test('goes when the answer drops it, changes it or is removed, and stays otherwise', async () => {
    const removed: string[] = [];
    const remove = async (paths: string[]) => { removed.push(...paths); };
    const week = '2026-11-30';
    assert.equal(await removeOldAnswerPhoto(remove, week, 'kid1', { photoId: 'p1' }, { photoId: 'p1' }), false);
    assert.equal(await removeOldAnswerPhoto(remove, week, 'kid1', undefined, { photoId: 'p1' }), false);
    assert.equal(await removeOldAnswerPhoto(remove, week, 'kid1', { photoId: null }, undefined), false);
    assert.deepEqual(removed, []);
    assert.equal(await removeOldAnswerPhoto(remove, week, 'kid1', { photoId: 'p1' }, { photoId: 'p2' }), true);
    assert.deepEqual(removed, ['weeklyQuestions/2026-11-30/kid1/p1.jpg', 'weeklyQuestions/2026-11-30/kid1/p1-thumb.jpg']);
    removed.length = 0;
    assert.equal(await removeOldAnswerPhoto(remove, week, 'kid1', { photoId: 'p2' }, undefined), true);
    assert.deepEqual(removed, ['weeklyQuestions/2026-11-30/kid1/p2.jpg', 'weeklyQuestions/2026-11-30/kid1/p2-thumb.jpg']);
  });
});

describe('against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'weekly-test');
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

  // Monday 30 November 2026 at 9 h in Paris.
  const monday = new Date('2026-11-30T08:00:00Z');

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('members'));
    await db.recursiveDelete(db.collection('weeklyQuestions'));
    const batch = db.batch();
    batch.set(db.doc('config/app'), { features: { weeklyQuestion: true } });
    for (const [id, profile] of [['camille', 'adult'], ['ines', 'adult'], ['noe', 'childWithApp'], ['lou', 'childWithoutApp']]) {
      batch.set(db.doc(`members/${id}`), { firstName: id, profile, parentIds: [] });
      batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: new Date() });
    }
    batch.set(db.doc('members/ines/settings/notifications'), { weekly: false });
    await batch.commit();
  });

  test('Monday 9 h: the question is published and the family with the app is told', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await publishWeeklyQuestion(db, send, monday), '2026-11-30');
    const q = await db.doc('weeklyQuestions/2026-11-30').get();
    assert.equal(q.get('bankId'), 'q01');
    assert.equal(q.get('answerCount'), 0);
    assert.equal(q.get('revealAt').toMillis() - q.get('publishedAt').toMillis(), REVEAL_AFTER_MS);
    assert.deepEqual(sent.map((s) => s.token), ['token-camille', 'token-noe']);
    assert.equal(sent[0].notice.channel, 'weekly');
    assert.equal(sent[0].notice.weeklyQuestionId, '2026-11-30');
  });

  test('nothing before the first Monday, while the module is off, or twice the same Monday', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await publishWeeklyQuestion(db, send, new Date('2026-11-23T08:00:00Z')), null);
    assert.equal(await publishWeeklyQuestion(db, send, new Date('2026-11-30T07:30:00Z')), null, '8 h 30 is too early');
    assert.equal(await publishWeeklyQuestion(db, send, new Date('2026-12-01T08:00:00Z')), null, 'not on Tuesday');
    await db.doc('config/app').set({ features: { weeklyQuestion: false } });
    assert.equal(await publishWeeklyQuestion(db, send, monday), null);
    await db.doc('config/app').set({ features: { weeklyQuestion: true } });
    assert.equal(await publishWeeklyQuestion(db, send, monday), '2026-11-30');
    assert.equal(await publishWeeklyQuestion(db, send, monday), null);
    assert.equal(sent.length, 2);
  });

  test('two runs at the same time tell the family once', async () => {
    const { sent, send } = fakeSender();
    const results = await Promise.all([publishWeeklyQuestion(db, send, monday), publishWeeklyQuestion(db, send, monday)]);
    assert.deepEqual(results.filter((r) => r !== null), ['2026-11-30']);
    assert.equal(sent.length, 2);
  });

  test('the next Monday brings another question', async () => {
    const { send } = fakeSender();
    await publishWeeklyQuestion(db, send, monday);
    await publishWeeklyQuestion(db, send, new Date('2026-12-07T08:00:00Z'));
    const next = await db.doc('weeklyQuestions/2026-12-07').get();
    assert.notEqual(next.get('bankId'), 'q01');
  });

  test('the count holds the answers, not « Passer »', async () => {
    const { send } = fakeSender();
    await publishWeeklyQuestion(db, send, monday);
    const answers = db.collection('weeklyQuestions/2026-11-30/answers');
    await answers.doc('camille').set({ memberId: 'camille', skipped: false, text: 'Les crêpes de Mamie' });
    await answers.doc('noe').set({ memberId: 'noe', skipped: false, text: 'La pêche avec Papi' });
    await answers.doc('ines').set({ memberId: 'ines', skipped: true, text: null });
    assert.equal(await countAnswers(db, '2026-11-30'), 2);
    assert.equal((await db.doc('weeklyQuestions/2026-11-30').get()).get('answerCount'), 2);
  });

  test('a member who leaves takes their answers', async () => {
    const { send } = fakeSender();
    await publishWeeklyQuestion(db, send, monday);
    await db.doc('weeklyQuestions/2026-11-30/answers/camille').set({ memberId: 'camille', skipped: false, text: 'Oui' });
    await db.doc('weeklyQuestions/2026-11-30/answers/noe').set({ memberId: 'noe', skipped: false, text: 'Oui' });
    assert.equal(await deleteAnswersOf(db, 'camille'), 1);
    assert.equal((await db.doc('weeklyQuestions/2026-11-30/answers/camille').get()).exists, false);
    assert.equal((await db.doc('weeklyQuestions/2026-11-30/answers/noe').get()).exists, true);
  });
});
