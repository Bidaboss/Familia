import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { applyHostedChallenge, hostNotice, hostOf, nextMonth, ofMonth, remindHost } from '../src/hostChallenge';
import { Notice } from '../src/logic';
import { Sender } from '../src/store';
import { celebrateChallenge, countForChallenge, ensureChallenge } from '../src/tree';

describe('the challenge of a foyer, without the emulator', () => {
  test('the months are named the French way, and follow each other', () => {
    assert.equal(ofMonth('2026-12'), 'de décembre');
    assert.equal(ofMonth('2027-04'), 'd\'avril');
    assert.equal(ofMonth('2027-08'), 'd\'août');
    assert.equal(ofMonth('2027-10'), 'd\'octobre');
    assert.equal(nextMonth('2026-12'), '2027-01');
    assert.equal(nextMonth('2027-01'), '2027-02');
    assert.equal(hostNotice('2026-12').body, 'C\'est à vous de lancer le défi de décembre ! Préparez-le ensemble dans l\'appli, ou passez votre tour.');
  });
});

describe('the challenge of a foyer against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'host-challenge-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const now = new Date('2026-12-05T10:00:00Z');

  function fakeSender() {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  const published = (householdId: string, overrides: Record<string, unknown> = {}) => ({
    householdId, status: 'published', kind: 'photo', title: '15 photos de nos balades', emoji: '📸',
    illustration: 'camera', target: 3, updatedBy: 'camille', updatedAt: now, ...overrides,
  });

  beforeEach(async () => {
    for (const c of ['members', 'challenges', 'challengePicks', 'challengeRotation', 'householdChallenges', 'treeMarks', 'familyTree', 'goodNews', 'households']) {
      await db.recursiveDelete(db.collection(c));
    }
    const batch = db.batch();
    batch.set(db.doc('config/app'), { features: { tree: true } });
    batch.set(db.doc('challengeRotation/current'), { householdIds: ['h1', 'h2'], startMonth: '2026-12', updatedAt: now });
    for (const [id, householdId, profile] of [['camille', 'h1', 'adult'], ['ines', 'h1', 'adult'], ['lou', 'h1', 'childWithApp'], ['zoe', 'h1', 'childWithoutApp'], ['paul', 'h2', 'adult']]) {
      batch.set(db.doc(`members/${id}`), { firstName: id, householdId, profile, parentIds: [] });
      batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: now });
    }
    await batch.commit();
  });

  test('the foyers take their turn a month each, from the start of the order', async () => {
    assert.equal(await hostOf(db, '2026-11'), null);
    assert.equal(await hostOf(db, '2026-12'), 'h1');
    assert.equal(await hostOf(db, '2027-01'), 'h2');
    assert.equal(await hostOf(db, '2027-02'), 'h1');
    await db.doc('challengeRotation/current').delete();
    assert.equal(await hostOf(db, '2026-12'), null);
  });

  test('seven days before its month, the foyer is told once, not those who turned the tree off', async () => {
    await db.doc('challengeRotation/current').set({ householdIds: ['h2', 'h1'], startMonth: '2026-12', updatedAt: now });
    await db.doc('members/ines/settings/notifications').set({ tree: false });
    const { sent, send } = fakeSender();
    // 24/12 at 18 h in Paris: too early.
    assert.equal(await remindHost(db, send, new Date('2026-12-24T17:00:00Z')), null);
    // 25/12: h1 has January.
    assert.equal(await remindHost(db, send, new Date('2026-12-25T17:00:00Z')), 'h1');
    assert.deepEqual(sent.map((s) => s.token).sort(), ['token-camille', 'token-lou']);
    assert.equal(sent[0].notice.title, 'Défi de janvier');
    assert.equal(await remindHost(db, send, new Date('2026-12-25T17:30:00Z')), null);
    assert.equal(sent.length, 2);
  });

  test('no notice when the foyer already published or passed, or the tree is off', async () => {
    const { sent, send } = fakeSender();
    const day = new Date('2026-12-25T17:00:00Z');
    await db.doc('householdChallenges/2027-01').set(published('h2', { status: 'skipped' }));
    assert.equal(await remindHost(db, send, day), null);
    await db.doc('householdChallenges/2027-01').delete();
    await db.doc('config/app').set({ features: { tree: false } });
    assert.equal(await remindHost(db, send, day), null);
    assert.equal(sent.length, 0);
  });

  test('published before the month: it becomes the challenge of the month, counting the linked Bonnes nouvelles', async () => {
    await db.doc('householdChallenges/2027-01').set(published('h2'));
    assert.equal(await applyHostedChallenge(db, '2027-01', now), false); // Not yet its month.
    const january = new Date('2027-01-03T10:00:00Z');
    await ensureChallenge(db, '2027-01', january);
    const created = await db.doc('challenges/2027-01').get();
    assert.equal(created.get('title'), '15 photos de nos balades');
    assert.equal(created.get('measure'), 'linked');
    assert.equal(created.get('householdId'), 'h2');
    assert.equal(created.get('source'), 'household');
    // Only a post linked to this challenge, with a photo, moves the gauge.
    assert.equal(await countForChallenge(db, 'n1', 2, january), null);
    assert.equal(await countForChallenge(db, 'n2', 0, january, '2027-01'), null);
    assert.equal(await countForChallenge(db, 'n3', 1, january, '2026-12'), null);
    assert.equal((await db.doc('challenges/2027-01').get()).get('progress'), 0);
    await countForChallenge(db, 'n4', 3, january, '2027-01');
    await countForChallenge(db, 'n4', 3, january, '2027-01');
    assert.equal((await db.doc('challenges/2027-01').get()).get('progress'), 1);
    await countForChallenge(db, 'n5', 1, january, '2027-01');
    const achieved = await countForChallenge(db, 'n6', 1, january, '2027-01');
    assert.equal(achieved?.month, '2027-01');
    // The souvenir of the challenge cites the foyer that launched it, as its host.
    await db.doc('households/h2').set({ name: 'les Martin' });
    const { sent, send } = fakeSender();
    assert.equal(await celebrateChallenge(db, send, achieved!, january), true);
    assert.equal((await db.doc('goodNews/challenge-2027-01').get()).get('text'),
      '📸 Défi réussi : 15 photos de nos balades, lancé par les Martin. Bravo à toute la famille !');
    assert.match(sent[0].notice.body, /lancé par les Martin :/);
  });

  test('published during its month: it replaces the challenge nobody moved, never the admin\'s pick', async () => {
    await ensureChallenge(db, '2026-12', now);
    await db.doc('householdChallenges/2026-12').set(published('h1'));
    assert.equal(await applyHostedChallenge(db, '2026-12', now), true);
    assert.equal((await db.doc('challenges/2026-12').get()).get('measure'), 'linked');
    // Once the gauge has moved, it stays.
    await db.doc('householdChallenges/2026-12').set(published('h1', { title: 'Autre' }));
    await countForChallenge(db, 'n1', 1, now, '2026-12');
    assert.equal(await applyHostedChallenge(db, '2026-12', now), false);
    assert.equal((await db.doc('challenges/2026-12').get()).get('title'), '15 photos de nos balades');
    // The admin principal's choice wins, when the month starts too.
    await db.doc('challengePicks/2027-02').set({ choiceId: 'kitchen', pickedAt: now });
    await db.doc('householdChallenges/2027-02').set(published('h1'));
    const february = new Date('2027-02-02T10:00:00Z');
    assert.equal(await applyHostedChallenge(db, '2027-02', february), false);
    await ensureChallenge(db, '2027-02', february);
    assert.equal((await db.doc('challenges/2027-02').get()).get('source'), 'admin');
  });

  test('while the tree is off, a challenge published during its month waits', async () => {
    await ensureChallenge(db, '2026-12', now);
    await db.doc('householdChallenges/2026-12').set(published('h1'));
    await db.doc('config/app').set({ features: { tree: false } });
    assert.equal(await applyHostedChallenge(db, '2026-12', now), false);
    assert.equal((await db.doc('challenges/2026-12').get()).get('source'), 'default');
  });

  test('a draft or a turn passed leaves the default challenge', async () => {
    await db.doc('householdChallenges/2026-12').set(published('h1', { status: 'draft' }));
    await ensureChallenge(db, '2026-12', now);
    assert.equal((await db.doc('challenges/2026-12').get()).get('source'), 'default');
    assert.equal(await applyHostedChallenge(db, '2026-12', now), false);
  });
});
