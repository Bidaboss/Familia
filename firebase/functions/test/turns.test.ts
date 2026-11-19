// « À qui le tour ? » is removed: its data is erased once, the count kept in migrations/turnsErased.
import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { eraseTurns, TURNS_ERASED } from '../src/turns';

describe('erasing the turns against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'turns-erase-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const now = new Date('2026-11-20T09:00:00Z');

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('turns'));
    await db.recursiveDelete(db.collection('migrations'));
    await db.recursiveDelete(db.collection('lendItems'));
    const batch = db.batch();
    batch.set(db.doc('turns/t1'), { title: 'Qui reçoit Noël' });
    batch.set(db.doc('turns/t2'), { title: 'Repas du dimanche' });
    batch.set(db.doc('turns/t2/notes/n1'), { text: 'x' });
    batch.set(db.doc('lendItems/i1'), { name: 'Perceuse' });
    await batch.commit();
  });

  test('every turn goes, subcollections included, and nothing else', async () => {
    assert.equal(await eraseTurns(db, now), 3);
    assert.equal((await db.collection('turns').get()).size, 0);
    assert.equal((await db.doc('turns/t2/notes/n1').get()).exists, false);
    assert.equal((await db.doc('lendItems/i1').get()).exists, true);
    assert.equal((await db.doc(TURNS_ERASED).get()).get('count'), 3);
  });

  test('once only: a second run does nothing', async () => {
    await eraseTurns(db, now);
    await db.doc('turns/t3').set({ title: 'Écrit après' });
    assert.equal(await eraseTurns(db, now), null);
    assert.equal((await db.doc('turns/t3').get()).exists, true);
  });
});
