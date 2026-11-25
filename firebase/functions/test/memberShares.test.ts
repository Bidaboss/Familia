// Retirer un membre (récit 114, lot A2): everything they shared goes with them, nothing of the others.
import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { deleteSharedBy } from '../src/memberShares';

describe('what a removed member shared, against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'member-shares-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const roots = ['recipes', 'goodNews', 'goodDeals', 'whoIsIt', 'events', 'trips', 'memberPhotos', 'capsules'];

  beforeEach(async () => {
    for (const root of roots) await db.recursiveDelete(db.collection(root));
    const b = db.batch();
    // noe leaves; ines stays.
    b.set(db.doc('recipes/r1'), { title: 'Tarte', authorId: 'noe' });
    b.set(db.doc('recipes/r2'), { title: 'Soupe', authorId: 'ines' });
    b.set(db.doc('recipes/r2/comments/c1'), { text: 'Bon', authorId: 'noe' });
    b.set(db.doc('recipes/r2/comments/c2'), { text: 'Oui', authorId: 'ines' });
    b.set(db.doc('goodNews/g1'), { text: 'Bac', authorId: 'noe' });
    b.set(db.doc('goodNews/g2'), { text: 'Permis', authorId: 'ines' });
    b.set(db.doc('goodNews/g2/comments/c1'), { text: 'Bravo', authorId: 'noe' });
    b.set(db.doc('goodDeals/d1'), { kind: 'deal', authorId: 'noe' });
    b.set(db.doc('whoIsIt/w1'), { authorId: 'noe' });
    b.set(db.doc('whoIsIt/w2'), { authorId: 'ines' });
    b.set(db.doc('events/e1'), { title: 'Repas', createdBy: 'noe' });
    b.set(db.doc('events/e1/photos/noe_1'), { authorId: 'noe', slot: 1 });
    b.set(db.doc('events/e1/photos/ines_1'), { authorId: 'ines', slot: 1 });
    b.set(db.doc('events/e1/comments/c1'), { text: 'Je viens', authorId: 'noe' });
    b.set(db.doc('trips/t1'), { title: 'Corse', creatorId: 'noe' });
    b.set(db.doc('trips/t1/photos/p1'), { authorId: 'noe' });
    b.set(db.doc('trips/t1/photos/p2'), { authorId: 'ines' });
    b.set(db.doc('trips/t1/words/s1'), { text: 'Superbe', authorId: 'noe' });
    b.set(db.doc('trips/t1/words/s1/comments/c1'), { text: 'Oui !', authorId: 'ines' });
    b.set(db.doc('trips/t1/words/s2'), { text: 'Pluie', authorId: 'ines' });
    b.set(db.doc('trips/t1/words/s2/comments/c1'), { text: 'Dommage', authorId: 'noe' });
    b.set(db.doc('trips/t1/words/s2/comments/c2'), { text: 'Bof', authorId: 'ines' });
    b.set(db.doc('trips/t1/words/s2/commentMutes/noe'), { at: 1 });
    b.set(db.doc('trips/t1/films/f1'), { authorId: 'noe' });
    b.set(db.doc('memberPhotos/noe'), { jpeg: 'x' });
    b.set(db.doc('memberPhotos/ines'), { jpeg: 'y' });
    b.set(db.doc('capsules/k1'), { authorId: 'noe' });
    await b.commit();
  });

  const exists = async (path: string) => (await db.doc(path).get()).exists;

  test('their recipes, photos, comments, posts and profile photo go, with their folders', async () => {
    const folders: string[] = [];
    const result = await deleteSharedBy(db, 'noe', async (prefix) => { folders.push(prefix); });

    for (const gone of ['recipes/r1', 'recipes/r2/comments/c1', 'goodNews/g1', 'goodNews/g2/comments/c1', 'goodDeals/d1',
      'whoIsIt/w1', 'events/e1/photos/noe_1', 'events/e1/comments/c1', 'trips/t1/photos/p1', 'trips/t1/words/s1',
      'trips/t1/films/f1', 'memberPhotos/noe', 'trips/t1/words/s1/comments/c1', 'trips/t1/words/s2/comments/c1',
      'trips/t1/words/s2/commentMutes/noe']) {
      assert.equal(await exists(gone), false, gone);
    }
    assert.equal(result.documents, 14);
    assert.deepEqual(folders.sort(), ['events/e1/noe/', 'goodNews/noe/', 'trips/t1/noe/', 'whoIsIt/noe/']);
  });

  test('what the others shared stays, and so do the events and trips they created, and their capsules', async () => {
    await deleteSharedBy(db, 'noe', async () => {});
    for (const kept of ['recipes/r2', 'recipes/r2/comments/c2', 'goodNews/g2', 'whoIsIt/w2', 'events/e1',
      'events/e1/photos/ines_1', 'trips/t1', 'trips/t1/photos/p2', 'trips/t1/words/s2', 'trips/t1/words/s2/comments/c2',
      'memberPhotos/ines', 'capsules/k1']) {
      assert.equal(await exists(kept), true, kept);
    }
  });

  test('a folder that fails does not stop the rest, and the failure is raised at the end', async () => {
    await assert.rejects(deleteSharedBy(db, 'noe', async (prefix) => {
      if (prefix.startsWith('goodNews/')) throw new Error('storage down');
    }), /storage down/);
    assert.equal(await exists('trips/t1/photos/p1'), false);
    assert.equal(await exists('memberPhotos/noe'), false);
  });
});
