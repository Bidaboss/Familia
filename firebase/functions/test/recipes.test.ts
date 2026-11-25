// Récit 114 (lot A1): the old recipes get a category and their person of origin as text.
import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { dropOriginMember, migrateRecipesOnce, RECIPE_MIGRATION, recipeChange } from '../src/recipes';

const names = new Map([['ines', 'Inès']]);

describe('what an old recipe needs', () => {
  test('no category, or an unknown one: « divers »', () => {
    assert.deepEqual(recipeChange({ origin: '' }, names), { category: 'divers' });
    assert.deepEqual(recipeChange({ category: 'goûter' }, names), { category: 'divers' });
    assert.equal(recipeChange({ category: 'plat', originMemberId: null }, names), null);
  });

  test('a person of origin becomes the text of its origin, unless a text is there already', () => {
    assert.deepEqual(recipeChange({ category: 'plat', originMemberId: 'ines', origin: '' }, names), { originMemberId: null, origin: 'Inès' });
    assert.deepEqual(recipeChange({ category: 'plat', originMemberId: 'ines', origin: 'Mamie Jeanne' }, names), { originMemberId: null });
  });

  test('a person who left: the id goes, the text stays as it was', () => {
    assert.deepEqual(recipeChange({ category: 'dessert', originMemberId: 'gone', origin: '' }, names), { originMemberId: null });
  });
});

describe('the recipes against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'recipes-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const now = new Date('2026-11-25T18:00:00Z');

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('recipes'));
    await db.recursiveDelete(db.collection('members'));
    await db.recursiveDelete(db.collection('migrations'));
    const batch = db.batch();
    batch.set(db.doc('members/ines'), { firstName: 'Inès' });
    batch.set(db.doc('recipes/r1'), { title: 'Tarte', origin: '', originMemberId: 'ines', authorId: 'noe' });
    batch.set(db.doc('recipes/r2'), { title: 'Soupe', origin: 'Mamie Jeanne', originMemberId: null, authorId: 'noe', category: 'entree' });
    batch.set(db.doc('recipes/r3'), { title: 'Crêpes', origin: '', originMemberId: null, authorId: 'noe' });
    await batch.commit();
  });

  test('once: the old recipes get « divers » and their origin as text; a recipe already right is left', async () => {
    assert.equal(await migrateRecipesOnce(db, now), 2);
    const r1 = (await db.doc('recipes/r1').get()).data();
    assert.equal(r1?.category, 'divers');
    assert.equal(r1?.origin, 'Inès');
    assert.equal(r1?.originMemberId, null);
    assert.ok('originMemberId' in (r1 ?? {}), 'the field stays, null: the rules ask for it');
    assert.equal((await db.doc('recipes/r2').get()).get('category'), 'entree');
    assert.equal((await db.doc('recipes/r3').get()).get('category'), 'divers');
    assert.equal((await db.doc(RECIPE_MIGRATION).get()).get('count'), 2);
    await db.doc('recipes/r4').set({ title: 'Flan', origin: '', originMemberId: null, authorId: 'noe' });
    assert.equal(await migrateRecipesOnce(db, now), null);
    assert.equal((await db.doc('recipes/r4').get()).get('category'), undefined);
  });

  test('a recipe written by an older app with a person of origin: the person becomes its origin text', async () => {
    const ref = db.doc('recipes/r1');
    assert.equal(await dropOriginMember(db, ref, (await ref.get()).data()), true);
    const r1 = (await ref.get()).data();
    assert.equal(r1?.origin, 'Inès');
    assert.equal(r1?.originMemberId, null);
    // Its category is left to the app and the reading.
    assert.equal(r1?.category, undefined);
    // Written again by the trigger itself: nothing more to do.
    assert.equal(await dropOriginMember(db, ref, r1), false);
  });

  test('a later edit wins: the recipe no longer names that person, nothing changes', async () => {
    const ref = db.doc('recipes/r1');
    const seen = (await ref.get()).data();
    await ref.update({ originMemberId: null, origin: 'Tante Rose' });
    assert.equal(await dropOriginMember(db, ref, seen), false);
    assert.equal((await ref.get()).get('origin'), 'Tante Rose');
  });
});
