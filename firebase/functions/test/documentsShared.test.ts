// « Documents » private by default (DESIGN-105): the old ones stay shared with those who go, once.
import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { DOCUMENTS_SHARED, oldDocumentSharing, shareOldDocuments } from '../src/documentsShared';

describe('who reads an old document', () => {
  test('its owner first, then those who go, each once', () => {
    assert.deepEqual(oldDocumentSharing(['a', 'b'], 'b'), ['b', 'a']);
    assert.deepEqual(oldDocumentSharing(['a'], 'c'), ['c', 'a']);
    assert.deepEqual(oldDocumentSharing(undefined, 'c'), ['c']);
  });
});

describe('sharing the old documents against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'documents-shared-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const now = new Date('2026-11-24T20:00:00Z');

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('trips'));
    await db.recursiveDelete(db.collection('migrations'));
    const batch = db.batch();
    batch.set(db.doc('trips/t1'), { title: 'Corse', participantIds: ['lou', 'sam'] });
    batch.set(db.doc('trips/t1/documents/d1'), { ownerId: 'sam', title: 'Billet' });
    batch.set(db.doc('trips/t1/documents/d2'), { ownerId: 'lou', title: 'Passeport', sharedWith: ['lou'] });
    await batch.commit();
  });

  test('an old document gets those who go; a document already shared keeps its choice', async () => {
    assert.equal(await shareOldDocuments(db, now), 1);
    assert.deepEqual((await db.doc('trips/t1/documents/d1').get()).get('sharedWith'), ['sam', 'lou']);
    assert.deepEqual((await db.doc('trips/t1/documents/d2').get()).get('sharedWith'), ['lou']);
    assert.equal((await db.doc(DOCUMENTS_SHARED).get()).get('count'), 1);
  });

  test('once only: a second run does nothing', async () => {
    await shareOldDocuments(db, now);
    await db.doc('trips/t1/documents/d3').set({ ownerId: 'sam', title: 'Écrit après' });
    assert.equal(await shareOldDocuments(db, now), null);
    assert.equal((await db.doc('trips/t1/documents/d3').get()).get('sharedWith'), undefined);
  });
});
