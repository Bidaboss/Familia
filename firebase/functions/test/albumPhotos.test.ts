// Supprimer une photo d'album (récit 121). Runs against the Firestore emulator (npm run
// test:functions in firebase/). Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { albumDayThreadId, albumPhotoFiles, cleanAfterAlbumPhoto, parisDay } from '../src/albumPhotos';

assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');

const app = initializeApp({ projectId: 'demo-familia' }, 'album-photos-test');
const db = getFirestore(app);

after(() => deleteApp(app));

/** 20/11/2026 at 10 h in Paris (9 h UTC), and 23 h 30 in Paris the same day (22 h 30 UTC). */
const MORNING = Timestamp.fromDate(new Date('2026-11-20T09:00:00Z'));
const LATE = Timestamp.fromDate(new Date('2026-11-20T22:30:00Z'));
const NEXT_DAY = Timestamp.fromDate(new Date('2026-11-21T09:00:00Z'));

describe('the files of a removed photo', () => {
  test('its photo and its thumbnail, at its own place only', () => {
    assert.deepEqual(albumPhotoFiles('trips', 't1', { authorId: 'lou', slot: 7 }), ['trips/t1/lou/7.jpg', 'trips/t1/lou/7-thumb.jpg']);
    assert.deepEqual(albumPhotoFiles('events', 'e1', { authorId: 'noe', slot: 30 }), ['events/e1/noe/30.jpg', 'events/e1/noe/30-thumb.jpg']);
  });

  test('none for a record the rules would never let through', () => {
    assert.deepEqual(albumPhotoFiles('trips', 't1', undefined), []);
    assert.deepEqual(albumPhotoFiles('trips', 't1', { authorId: '../x', slot: 1 }), []);
    assert.deepEqual(albumPhotoFiles('trips', 't1', { authorId: 'lou', slot: 31 }), []);
    assert.deepEqual(albumPhotoFiles('trips', 't1', { authorId: 'lou', slot: 1.5 }), []);
    assert.deepEqual(albumPhotoFiles('trips', 't1', { authorId: 'lou', slot: '1' }), []);
    assert.deepEqual(albumPhotoFiles('trips', 'a/b', { authorId: 'lou', slot: 1 }), []);
  });
});

describe('the card of the gazette of its day', () => {
  test('the day is the one of Paris, as the cards of the app', () => {
    assert.equal(parisDay(MORNING.toDate()), '2026-11-20');
    assert.equal(parisDay(LATE.toDate()), '2026-11-20');
    assert.equal(parisDay(new Date('2026-11-20T23:30:00Z')), '2026-11-21');
    assert.equal(albumDayThreadId('trips', 't1', { createdAt: MORNING }), 'album:trips:t1:2026-11-20');
    assert.equal(albumDayThreadId('events', 'e1', { createdAt: LATE }), 'album:events:e1:2026-11-20');
    assert.equal(albumDayThreadId('events', 'e1', {}), null);
  });
});

describe('what a removed photo takes with it', () => {
  beforeEach(async () => {
    await db.recursiveDelete(db.collection('trips'));
    await db.recursiveDelete(db.collection('events'));
    await db.recursiveDelete(db.collection('gazetteThreads'));
  });

  async function thread(id: string) {
    await db.doc(`gazetteThreads/${id}`).set({ reactions: { lou: 'heart' }, commentCount: 1, last: [] });
    await db.doc(`gazetteThreads/${id}/comments/c1`).set({ authorId: 'lou', text: 'Belle journée', createdAt: MORNING });
  }

  test('its place in the words of the steps of the trip, nothing else of them', async () => {
    await db.doc('trips/t1').set({ title: 'Corse' });
    await db.doc('trips/t1/words/s1').set({ text: 'Plage', photoIds: ['lou_1', 'lou_2'], hearts: { sam: true } });
    await db.doc('trips/t1/words/s2').set({ text: 'Port', photoIds: ['sam_1'], hearts: {} });
    await cleanAfterAlbumPhoto(db, 'trips', 't1', 'lou_1', { authorId: 'lou', slot: 1, createdAt: MORNING });
    assert.deepEqual((await db.doc('trips/t1/words/s1').get()).data(), { text: 'Plage', photoIds: ['lou_2'], hearts: { sam: true } });
    assert.deepEqual((await db.doc('trips/t1/words/s2').get()).get('photoIds'), ['sam_1']);
  });

  test('the card of its day, with its J\'aime and comments, once no photo of that day is left', async () => {
    await db.doc('events/e1').set({ title: 'Repas' });
    await db.doc('events/e1/photos/lou_2').set({ authorId: 'lou', slot: 2, createdAt: LATE });
    await thread('album:events:e1:2026-11-20');
    await thread('album:events:e1:2026-11-21');

    // Another photo of the same day is left: the card stays.
    await cleanAfterAlbumPhoto(db, 'events', 'e1', 'lou_1', { authorId: 'lou', slot: 1, createdAt: MORNING });
    assert.ok((await db.doc('gazetteThreads/album:events:e1:2026-11-20').get()).exists);

    await db.doc('events/e1/photos/lou_2').delete();
    await cleanAfterAlbumPhoto(db, 'events', 'e1', 'lou_2', { authorId: 'lou', slot: 2, createdAt: LATE });
    assert.equal((await db.doc('gazetteThreads/album:events:e1:2026-11-20').get()).exists, false);
    assert.equal((await db.collection('gazetteThreads/album:events:e1:2026-11-20/comments').get()).size, 0);
    // The card of another day is not touched.
    assert.ok((await db.doc('gazetteThreads/album:events:e1:2026-11-21').get()).exists);
  });

  test('a photo of the next day does not keep the card of this one', async () => {
    await db.doc('trips/t1').set({ title: 'Corse' });
    await db.doc('trips/t1/photos/sam_1').set({ authorId: 'sam', slot: 1, createdAt: NEXT_DAY });
    await thread('album:trips:t1:2026-11-20');
    await cleanAfterAlbumPhoto(db, 'trips', 't1', 'lou_1', { authorId: 'lou', slot: 1, createdAt: MORNING });
    assert.equal((await db.doc('gazetteThreads/album:trips:t1:2026-11-20').get()).exists, false);
  });

  test('nothing is read when the whole album is going (its trip removed)', async () => {
    await thread('album:trips:t9:2026-11-20');
    await cleanAfterAlbumPhoto(db, 'trips', 't9', 'lou_1', { authorId: 'lou', slot: 1, createdAt: MORNING });
    // The cleanup of the trip removes its cards itself (deleteGazetteThreadsOfThing).
    assert.ok((await db.doc('gazetteThreads/album:trips:t9:2026-11-20').get()).exists);
  });
});
