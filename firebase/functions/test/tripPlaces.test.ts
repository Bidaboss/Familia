// Places of a destination (récit 103). Runs against the Firestore emulator (npm run test:functions in firebase/).
// Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { deleteStepPlaces } from '../src/tripPlaces';
import { deleteTripContents } from '../src/trips';

assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');

const app = initializeApp({ projectId: 'demo-familia' }, 'trip-places-test');
const db = getFirestore(app);

after(() => deleteApp(app));

describe('places of a destination', () => {
  beforeEach(async () => {
    await db.recursiveDelete(db.collection('trips'));
  });

  test('a deleted destination takes its places, the others stay', async () => {
    await db.doc('trips/t1/places/p1').set({ stepId: 's1', kind: 'restaurant', name: 'Le Port' });
    await db.doc('trips/t1/places/p2').set({ stepId: 's1', kind: 'lodging', name: 'Camping' });
    await db.doc('trips/t1/places/p3').set({ stepId: 's2', kind: 'spot', name: 'Citadelle' });
    assert.equal(await deleteStepPlaces(db, 't1', 's1'), 2);
    assert.equal((await db.doc('trips/t1/places/p1').get()).exists, false);
    assert.equal((await db.doc('trips/t1/places/p2').get()).exists, false);
    assert.equal((await db.doc('trips/t1/places/p3').get()).exists, true);
    assert.equal(await deleteStepPlaces(db, 't1', 's1'), 0);
  });

  test('deleting the trip deletes its places', async () => {
    await db.doc('trips/t1/places/p1').set({ stepId: 's1', kind: 'restaurant', name: 'Le Port' });
    await deleteTripContents(db, 't1');
    assert.equal((await db.collection('trips/t1/places').get()).size, 0);
  });
});
