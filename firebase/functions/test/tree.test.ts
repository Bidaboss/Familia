import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import { Sender } from '../src/store';
import {
  addLeaf, applyChallengePick, CATALOG, celebrateChallenge, countForChallenge, defaultChallenge, deleteTreeDataOf, ensureChallenge, LEAVES_PER_DAY,
  parisMonth, treeLevel, treeMark,
} from '../src/tree';

describe('the tree, without the emulator', () => {
  test('levels only go up with the leaves', () => {
    assert.equal(treeLevel(0), 0);
    assert.equal(treeLevel(19), 0);
    assert.equal(treeLevel(20), 1);
    assert.equal(treeLevel(5000), 6);
  });

  test('a default challenge for each month, December is 20 photos de Noël', () => {
    for (let m = 1; m <= 12; m++) assert.ok(defaultChallenge(`2027-${String(m).padStart(2, '0')}`).target > 0);
    assert.deepEqual(defaultChallenge('2026-12'), { title: '20 photos de Noël', emoji: '🎄', measure: 'photos', target: 20 });
    // 1 December at 0 h 30 in Paris is still 30 November in UTC.
    assert.equal(parisMonth(new Date('2026-11-30T23:30:00Z')), '2026-12');
  });

  test('the list holds every default, each within reach', () => {
    for (const c of Object.values(CATALOG)) assert.ok(c.target > 0 && c.target <= 20 && c.title && c.emoji);
    // The rules (challengePicks) and the app (ChallengeCatalog.kt) keep the same 16 ids.
    assert.equal(Object.keys(CATALOG).length, 16);
  });

  test('the rules accept exactly the ids of the list (challengePicks)', () => {
    const rules = readFileSync(join(__dirname, '..', '..', '..', 'firestore.rules'), 'utf8');
    const block = rules.slice(rules.indexOf('match /challengePicks/{month}'));
    const list = /choiceId in \[([^\]]*)\]/.exec(block)?.[1] ?? '';
    const ids = [...list.matchAll(/'([^']+)'/g)].map((m) => m[1]);
    assert.deepEqual([...ids].sort(), Object.keys(CATALOG).sort());
  });
});

describe('the tree against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'tree-test');
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

  const leaves = async () => Number((await db.doc('familyTree/current').get()).get('leaves') ?? 0);

  beforeEach(async () => {
    for (const c of ['members', 'familyTree', 'challenges', 'challengePicks', 'treeMarks', 'treeDays', 'goodNews']) await db.recursiveDelete(db.collection(c));
    const batch = db.batch();
    batch.set(db.doc('config/app'), { features: { tree: true } });
    for (const [id, profile] of [['camille', 'adult'], ['ines', 'adult'], ['lou', 'childWithoutApp']]) {
      batch.set(db.doc(`members/${id}`), { firstName: id, profile, parentIds: [] });
      batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: now });
    }
    await batch.commit();
  });

  test('a gesture adds one leaf, once, and never more than 5 a day per person', async () => {
    assert.equal(await addLeaf(db, 'camille', treeMark.goodNews('n1'), now), true);
    assert.equal(await addLeaf(db, 'camille', treeMark.goodNews('n1'), now), false);
    for (let i = 2; i <= 8; i++) await addLeaf(db, 'camille', treeMark.reaction(`n${i}`, 'camille'), now);
    assert.equal(await leaves(), LEAVES_PER_DAY);
    // Somebody else still counts, and so does Camille the next day.
    assert.equal(await addLeaf(db, 'ines', treeMark.rsvp('e1', 'ines'), now), true);
    assert.equal(await addLeaf(db, 'camille', treeMark.help('h1'), new Date('2026-12-06T10:00:00Z')), true);
    // A photo of an event album counts once per slot, even taken off and put back.
    const later = new Date('2026-12-07T10:00:00Z');
    assert.equal(await addLeaf(db, 'ines', treeMark.albumPhoto('e1', 'ines_1'), later), true);
    assert.equal(await addLeaf(db, 'ines', treeMark.albumPhoto('e1', 'ines_1'), later), false);
    assert.equal(await leaves(), LEAVES_PER_DAY + 3);
  });

  test('the photos of the month fill the gauge, once per post, and the tree blooms once', async () => {
    assert.equal(await countForChallenge(db, 'n1', 3, now), null);
    assert.equal(await countForChallenge(db, 'n1', 3, now), null);
    assert.equal((await db.doc('challenges/2026-12').get()).get('progress'), 3);
    let achieved = null;
    for (let i = 2; i <= 8 && !achieved; i++) achieved = await countForChallenge(db, `n${i}`, 3, now);
    assert.equal(achieved?.month, '2026-12');
    const challenge = await db.doc('challenges/2026-12').get();
    assert.equal(challenge.get('progress'), 20);
    assert.ok(challenge.get('achievedAt'));
    assert.deepEqual((await db.doc('familyTree/current').get()).get('bloomedMonths'), ['2026-12']);
    assert.equal(await countForChallenge(db, 'n9', 3, now), null);
  });

  test('ensureChallenge keeps a challenge already there', async () => {
    await db.doc('challenges/2026-12').set({ title: 'Autre', emoji: '⭐', measure: 'goodNews', target: 5, progress: 2, achievedAt: null });
    await ensureChallenge(db, '2026-12', now);
    assert.equal((await db.doc('challenges/2026-12').get()).get('title'), 'Autre');
  });

  test('the challenge picked by the admin principal: used when the month starts, or replaces one nobody has moved', async () => {
    await db.doc('challengePicks/2026-12').set({ choiceId: 'kitchen', pickedAt: now });
    await ensureChallenge(db, '2026-12', now);
    const created = await db.doc('challenges/2026-12').get();
    assert.equal(created.get('title'), '10 photos de nos plats');
    assert.equal(created.get('source'), 'admin');
    // Changed again before anyone moved the gauge: replaced.
    await db.doc('challengePicks/2026-12').set({ choiceId: 'proudOf', pickedAt: now });
    assert.equal(await applyChallengePick(db, '2026-12', now), true);
    assert.equal((await db.doc('challenges/2026-12').get()).get('measure'), 'goodNews');
    // Once the gauge has moved, it stays: no gesture counts for nothing.
    await countForChallenge(db, 'n1', 0, now);
    await db.doc('challengePicks/2026-12').set({ choiceId: 'pets', pickedAt: now });
    assert.equal(await applyChallengePick(db, '2026-12', now), false);
    assert.equal((await db.doc('challenges/2026-12').get()).get('choiceId'), 'proudOf');
    // The next month is created straight away from its pick.
    await db.doc('challengePicks/2027-01').set({ choiceId: 'pets', pickedAt: now });
    assert.equal(await applyChallengePick(db, '2027-01', now), true);
    assert.equal((await db.doc('challenges/2027-01').get()).get('progress'), 0);
  });

  test('a challenge achieved: one notice for each member with the app, one « Défi réussi », nobody named', async () => {
    await db.doc('members/ines/settings/notifications').set({ tree: false });
    const { sent, send } = fakeSender();
    const c = { ...defaultChallenge('2026-12'), month: '2026-12' };
    assert.equal(await celebrateChallenge(db, send, c, now), true);
    assert.equal(await celebrateChallenge(db, send, c, now), false);
    assert.deepEqual(sent.map((s) => s.token), ['token-camille']);
    assert.equal(sent[0].notice.channel, 'tree');
    const post = await db.doc('goodNews/challenge-2026-12').get();
    assert.equal(post.get('authorId'), 'familia');
    assert.deepEqual(post.get('mentionIds'), []);
  });

  test('module off: the tree grows quietly, nothing is told or posted', async () => {
    await db.doc('config/app').set({ features: { tree: false } });
    const { sent, send } = fakeSender();
    assert.equal(await celebrateChallenge(db, send, { ...defaultChallenge('2026-12'), month: '2026-12' }, now), false);
    assert.equal(sent.length, 0);
    assert.equal((await db.doc('goodNews/challenge-2026-12').get()).exists, false);
  });

  test('a member removed takes their marks and day counts, the leaves stay (EXPERT-1)', async () => {
    await addLeaf(db, 'camille', treeMark.goodNews('n1'), now);
    await addLeaf(db, 'ines', treeMark.goodNews('n2'), now);
    await deleteTreeDataOf(db, 'camille');
    assert.equal((await db.collection('treeMarks').where('memberId', '==', 'camille').get()).size, 0);
    assert.equal((await db.collection('treeDays').where('memberId', '==', 'camille').get()).size, 0);
    assert.equal((await db.collection('treeMarks').where('memberId', '==', 'ines').get()).size, 1);
    assert.equal(await leaves(), 2);
  });
});
