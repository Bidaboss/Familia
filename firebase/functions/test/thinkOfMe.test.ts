// « Pensez à moi » is retired (PRODUIT-136): only the cleanup of its data remains.
import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { deleteCheersOf, deleteMomentsOf } from '../src/thinkOfMe';

describe('the data of Pensez à moi against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'think-of-me-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const morning = new Date('2026-12-12T09:00:00Z');

  const moment = (forMemberId: string, overrides: Record<string, unknown> = {}) => ({
    forMemberId, authorId: forMemberId, authorName: forMemberId, title: 'Examen de piano', day: '2026-12-12',
    status: 'accepted', createdAt: morning, ...overrides,
  });

  beforeEach(async () => {
    for (const c of ['moments', 'momentCheers']) await db.recursiveDelete(db.collection(c));
    const batch = db.batch();
    batch.set(db.doc('moments/m1'), moment('camille'));
    batch.set(db.doc('moments/later'), moment('ines', { day: '2026-12-13' }));
    await batch.commit();
  });

  test('a moment removed takes its encouragements, and only them', async () => {
    await db.doc('momentCheers/m1-ines').set({ momentId: 'm1', forMemberId: 'camille', authorId: 'ines', authorName: 'Inès', text: 'x' });
    await db.doc('momentCheers/later-noe').set({ momentId: 'later', forMemberId: 'ines', authorId: 'noe', authorName: 'Noé', text: 'y' });
    await deleteCheersOf(db, 'm1');
    assert.equal((await db.doc('momentCheers/m1-ines').get()).exists, false);
    assert.equal((await db.doc('momentCheers/later-noe').get()).exists, true);
  });

  test('a member removed takes their moments and encouragements', async () => {
    await db.doc('momentCheers/m1-ines').set({ momentId: 'm1', forMemberId: 'camille', authorId: 'ines', authorName: 'Inès', text: 'x' });
    await deleteMomentsOf(db, 'camille');
    assert.equal((await db.doc('moments/m1').get()).exists, false);
    assert.equal((await db.doc('momentCheers/m1-ines').get()).exists, false);
    assert.equal((await db.doc('moments/later').get()).exists, true);
  });
});
