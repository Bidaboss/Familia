// Cadeaux et Surprise: what the server keeps (average of the ratings, total of the parts), against the
// Firestore emulator (npm run test:functions in firebase/). Fictional family only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import {
  deleteProposalRatings, deleteSurpriseContent, deleteTakeShares, updateRatingAverage, updateTakeTotal, validAmount, validStars,
} from '../src/surprises';

describe('logic', () => {
  test('a rating counts from 1 to 5 whole stars, a part in whole euros above 0', () => {
    assert.deepEqual([0, 1, 3, 5, 6, 2.5, '4', null].map(validStars), [null, 1, 3, 5, null, null, 4, null]);
    assert.deepEqual([0, -5, 10, 12.5, '20', undefined].map(validAmount), [null, null, 10, null, 20, null]);
  });
});

assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');

const app = initializeApp({ projectId: 'demo-familia' }, 'surprises-test');
const db = getFirestore(app);
after(() => deleteApp(app));

const surprise = {
  beneficiaryId: 'camille', title: 'Noël de Camille', occasion: 'christmas', date: '2026-12-25',
  memberIds: ['ines', 'jade'], creatorId: 'ines', creatorName: 'Inès', status: 'preparing',
  createdAt: new Date(), updatedAt: new Date(),
};

describe('functions', () => {
  beforeEach(async () => {
    await db.recursiveDelete(db.collection('surprises'));
    const batch = db.batch();
    batch.set(db.doc('surprises/s1'), surprise);
    batch.set(db.doc('surprises/s1/proposals/p1'), { kind: 'wish', title: 'Un livre', removed: false });
    batch.set(db.doc('surprises/s1/takes/p1'), { kind: 'shared', ownerId: 'ines', boughtBy: null, goal: 60 });
    await batch.commit();
  });

  test('the average of a proposal: sum and count, the wrong ratings left out', async () => {
    const ratings = db.collection('surprises/s1/proposals/p1/ratings');
    await ratings.doc('ines').set({ stars: 4 });
    await ratings.doc('jade').set({ stars: 5 });
    await ratings.doc('pirate').set({ stars: 9 });
    await updateRatingAverage(db, 's1', 'p1');
    let p = await db.doc('surprises/s1/proposals/p1').get();
    assert.equal(p.get('ratingSum'), 9);
    assert.equal(p.get('ratingCount'), 2);
    await ratings.doc('jade').set({ stars: 2 }); // Changed her mind.
    await updateRatingAverage(db, 's1', 'p1');
    p = await db.doc('surprises/s1/proposals/p1').get();
    assert.equal(p.get('ratingSum'), 6);
    assert.equal(p.get('ratingCount'), 2);
    await ratings.doc('ines').delete();
    await updateRatingAverage(db, 's1', 'p1');
    p = await db.doc('surprises/s1/proposals/p1').get();
    assert.equal(p.get('ratingSum'), 2);
    assert.equal(p.get('ratingCount'), 1);
    await updateRatingAverage(db, 's1', 'missing'); // A removed proposal: nothing to keep.
    assert.equal((await db.doc('surprises/s1/proposals/missing').get()).exists, false);
  });

  test('ratings in the same second: the count has them all', async () => {
    const ratings = db.collection('surprises/s1/proposals/p1/ratings');
    const given: Record<string, number> = { a: 1, b: 2, c: 3, d: 4, e: 5 };
    await Promise.all(Object.entries(given).map(async ([id, stars]) => {
      await ratings.doc(id).set({ stars });
      await updateRatingAverage(db, 's1', 'p1');
    }));
    await updateRatingAverage(db, 's1', 'p1');
    const p = await db.doc('surprises/s1/proposals/p1').get();
    assert.equal(p.get('ratingSum'), 15);
    assert.equal(p.get('ratingCount'), 5);
  });

  test('the total of a joint gift, in whole euros, over the goal allowed', async () => {
    const shares = db.collection('surprises/s1/takes/p1/shares');
    await shares.doc('ines').set({ amount: 40 });
    await shares.doc('jade').set({ amount: 30 });
    await shares.doc('odd').set({ amount: 12.5 });
    await updateTakeTotal(db, 's1', 'p1');
    let t = await db.doc('surprises/s1/takes/p1').get();
    assert.equal(t.get('sharesTotal'), 70); // Above the goal of 60: kept, the app warns.
    assert.equal(t.get('sharesCount'), 2);
    await shares.doc('jade').delete();
    await updateTakeTotal(db, 's1', 'p1');
    t = await db.doc('surprises/s1/takes/p1').get();
    assert.equal(t.get('sharesTotal'), 40);
    assert.equal(t.get('sharesCount'), 1);
    await updateTakeTotal(db, 's1', 'missing');
    assert.equal((await db.doc('surprises/s1/takes/missing').get()).exists, false);
  });

  test('parts in the same second: the total counts them all', async () => {
    const shares = db.collection('surprises/s1/takes/p1/shares');
    const amounts: Record<string, number> = { p1: 10, p2: 20, p3: 30, p4: 40 };
    await Promise.all(Object.entries(amounts).map(async ([id, amount]) => {
      await shares.doc(id).set({ amount });
      await updateTakeTotal(db, 's1', 'p1');
    }));
    await updateTakeTotal(db, 's1', 'p1');
    const t = await db.doc('surprises/s1/takes/p1').get();
    assert.equal(t.get('sharesTotal'), 100);
    assert.equal(t.get('sharesCount'), 4);
  });

  test('a removed proposal, a released gift, a removed surprise leave nothing behind', async () => {
    await db.doc('surprises/s1/proposals/p1/ratings/ines').set({ stars: 4 });
    await db.doc('surprises/s1/takes/p1/shares/ines').set({ amount: 20 });
    await db.doc('surprises/s1/members/ines').set({ validated: false });
    await deleteProposalRatings(db, 's1', 'p1');
    assert.equal((await db.collection('surprises/s1/proposals/p1/ratings').get()).size, 0);
    await deleteTakeShares(db, 's1', 'p1');
    assert.equal((await db.collection('surprises/s1/takes/p1/shares').get()).size, 0);
    await db.doc('surprises/s1/proposals/p2/ratings/jade').set({ stars: 3 });
    await db.doc('surprises/s1').delete();
    await deleteSurpriseContent(db, 's1');
    for (const c of ['members', 'proposals', 'takes']) {
      assert.equal((await db.collection(`surprises/s1/${c}`).get()).size, 0, c);
    }
    assert.equal((await db.collection('surprises/s1/proposals/p2/ratings').get()).size, 0);
  });
});
