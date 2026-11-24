// « Documents » private by default (DESIGN-105): the old ones stay shared with those who go, once.
import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { DOCUMENTS_SHARED, oldDocumentSharing, readersToDrop, shareOldDocuments, unshareDocuments } from '../src/documentsShared';

describe('who reads an old document', () => {
  test('its owner first, then those who go, each once', () => {
    assert.deepEqual(oldDocumentSharing(['a', 'b'], 'b'), ['b', 'a']);
    assert.deepEqual(oldDocumentSharing(['a'], 'c'), ['c', 'a']);
    assert.deepEqual(oldDocumentSharing(undefined, 'c'), ['c']);
  });
});

describe('who stops reading when someone leaves', () => {
  test('all who left but the owner of the document', () => {
    assert.deepEqual(readersToDrop(['noe', 'sam'], 'sam'), ['noe']);
    assert.deepEqual(readersToDrop(['noe'], 'lou'), ['noe']);
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

describe('taking a traveller who left off the documents against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'documents-unshared-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('trips'));
    const batch = db.batch();
    batch.set(db.doc('trips/t2'), { title: 'Crète', participantIds: ['lou', 'sam'] });
    batch.set(db.doc('trips/t2/documents/d1'), { ownerId: 'lou', title: 'Billet', sharedWith: ['lou', 'sam', 'noe'] });
    batch.set(db.doc('trips/t2/documents/d2'), { ownerId: 'noe', title: 'Passeport', sharedWith: ['noe', 'lou'] });
    batch.set(db.doc('trips/t2/documents/d3'), { ownerId: 'sam', title: 'Hôtel', sharedWith: ['sam'] });
    await batch.commit();
  });

  test('the traveller who left reads none of the others; their own document stays theirs', async () => {
    assert.equal(await unshareDocuments(db, 't2', ['noe']), 1);
    assert.deepEqual((await db.doc('trips/t2/documents/d1').get()).get('sharedWith'), ['lou', 'sam']);
    assert.deepEqual((await db.doc('trips/t2/documents/d2').get()).get('sharedWith'), ['noe', 'lou']);
    assert.deepEqual((await db.doc('trips/t2/documents/d3').get()).get('sharedWith'), ['sam']);
  });

  test('nobody left: nothing changes', async () => {
    assert.equal(await unshareDocuments(db, 't2', []), 0);
  });
});
