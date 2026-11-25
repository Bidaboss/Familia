// La gazette, les publications (récit 121): a removed one takes its photos and its thread.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { clearEndedPostPlaces, gazettePostPhotoPaths, onGazettePostRemoved } from '../src/gazettePosts';

const app = initializeApp({ projectId: 'demo-familia' }, 'gazette-posts');
const db = getFirestore(app);
after(async () => { await deleteApp(app); });

beforeEach(async () => {
  await db.recursiveDelete(db.collection('gazetteThreads'));
});

describe('the files of a publication', () => {
  test('each photo and its thumbnail, in the folder of its author', () => {
    assert.deepEqual(gazettePostPhotoPaths('p1', 'camille', ['a', 'b']), [
      'gazettePosts/camille/p1/a.jpg', 'gazettePosts/camille/p1/a-thumb.jpg',
      'gazettePosts/camille/p1/b.jpg', 'gazettePosts/camille/p1/b-thumb.jpg',
    ]);
  });
});

describe('a removed publication, against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');

  test('its photos, its J\'aime and its comments go; the others stay', async () => {
    await db.doc('gazetteThreads/post:p1').set({ reactions: { camille: '💛' }, commentCount: 1 });
    await db.doc('gazetteThreads/post:p1/comments/c1').set({ text: 'Bravo', authorId: 'camille' });
    await db.doc('gazetteThreads/post:p2').set({ reactions: { camille: '💛' } });
    const removed: string[] = [];
    await onGazettePostRemoved(db, async (paths) => { removed.push(...paths); }, 'p1', { authorId: 'sacha', photoIds: ['a'] });
    assert.deepEqual(removed, ['gazettePosts/sacha/p1/a.jpg', 'gazettePosts/sacha/p1/a-thumb.jpg']);
    assert.equal((await db.doc('gazetteThreads/post:p1').get()).exists, false);
    assert.equal((await db.collection('gazetteThreads/post:p1/comments').get()).size, 0);
    assert.equal((await db.doc('gazetteThreads/post:p2').get()).exists, true);
  });

  test('a word without photo, or a failing Storage: the thread goes all the same, then the failure is raised', async () => {
    await db.doc('gazetteThreads/post:p3').set({ reactions: { camille: '💛' } });
    await assert.rejects(
      onGazettePostRemoved(db, async () => { throw new Error('storage down'); }, 'p3', { authorId: 'sacha', photoIds: [] }),
      /storage down/,
    );
    assert.equal((await db.doc('gazetteThreads/post:p3').get()).exists, false);
  });
});

describe('« Dire où je suis »: the point goes after 2 h, against the emulator', () => {
  const at = (h: number) => Timestamp.fromMillis(Date.UTC(2026, 10, 25, h));

  test('a position older than 2 h loses its point and keeps its town; a newer one stays', async () => {
    await db.recursiveDelete(db.collection('gazettePosts'));
    await db.doc('gazettePosts/old').set({ text: '', loc: { label: 'Annecy', lat: 45.9, lng: 6.1 }, locLive: true, createdAt: at(10) });
    await db.doc('gazettePosts/new').set({ text: '', loc: { label: 'Lyon', lat: 45.7, lng: 4.8 }, locLive: true, createdAt: at(12) });
    await db.doc('gazettePosts/stopped').set({ text: 'Coucou', loc: { label: 'Nice' }, createdAt: at(9) });
    const cleared = await clearEndedPostPlaces(db, new Date(Date.UTC(2026, 10, 25, 12, 30)));
    assert.equal(cleared, 1);
    const old = (await db.doc('gazettePosts/old').get()).data();
    assert.deepEqual(old?.loc, { label: 'Annecy' });
    assert.equal(old?.locLive, undefined);
    assert.deepEqual((await db.doc('gazettePosts/new').get()).get('loc'), { label: 'Lyon', lat: 45.7, lng: 4.8 });
    assert.deepEqual((await db.doc('gazettePosts/stopped').get()).get('loc'), { label: 'Nice' });
    // Run again: nothing left to erase.
    assert.equal(await clearEndedPostPlaces(db, new Date(Date.UTC(2026, 10, 25, 12, 30))), 0);
  });
});
