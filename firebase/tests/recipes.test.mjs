// Rules of the recipe book (PRD, récit 38). Fictional family: admin1 (admin principal), adult1,
// adult2 in h1, adult3 in h2.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
});

/** A valid new recipe written by [author]. */
function recipe(author, overrides = {}) {
  return {
    title: 'Gâteau aux pommes',
    origin: 'Mamie Jeanne',
    originMemberId: null,
    servings: 6,
    ingredients: '4 pommes\n150 g de farine\n3 œufs',
    steps: 'Couper les pommes.\nMélanger le reste.\nCuire 40 minutes.',
    tags: ['dessert'],
    authorId: author.memberId,
    authorName: author.memberId,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

async function stored(id, author) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `recipes/${id}`), {
      ...recipe(author),
      createdAt: Timestamp.fromMillis(0),
      updatedAt: Timestamp.fromMillis(0),
    });
  });
}

describe('recipes (récit 38)', () => {
  test('a member writes a recipe in their own name, and the whole family reads it', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'recipes/r1'), recipe(ADULT)));
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), 'recipes/r1')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'recipes/r1')));
  });

  test('the origin may be a member of the family, never an unknown one', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'recipes/r1'), recipe(ADULT, { originMemberId: 'adult2', origin: '' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'recipes/r2'), recipe(ADULT, { originMemberId: 'nobody' })));
  });

  test('not in the name of someone else, and not an empty or oversized recipe', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'recipes/r1'), recipe(OTHER_ADULT)));
    await assertFails(setDoc(doc(db, 'recipes/r2'), recipe(ADULT, { title: '' })));
    await assertFails(setDoc(doc(db, 'recipes/r3'), recipe(ADULT, { ingredients: '' })));
    await assertFails(setDoc(doc(db, 'recipes/r4'), recipe(ADULT, { steps: 'x'.repeat(6001) })));
    await assertFails(setDoc(doc(db, 'recipes/r5'), recipe(ADULT, { servings: 0 })));
    await assertFails(setDoc(doc(db, 'recipes/r6'), recipe(ADULT, { tags: ['a', 'b', 'c', 'd', 'e', 'f', 'g'] })));
    await assertFails(setDoc(doc(db, 'recipes/r7'), recipe(ADULT, { secret: true })));
  });

  test('the author or the admin principal edits and removes it, nobody else', async () => {
    await stored('r1', ADULT);
    const edit = { title: 'Gâteau de Mamie', updatedAt: serverTimestamp() };
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'recipes/r1'), edit));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'recipes/r1'), edit));
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'recipes/r1'), edit));
    await assertFails(updateDoc(doc(as(env, ADULT), 'recipes/r1'), { authorId: 'adult2', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), 'recipes/r1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'recipes/r1')));
  });

  test('every tag is a short text', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'recipes/r1'), recipe(ADULT, { tags: ['dessert', 'Noël', 'x'.repeat(24)] })));
    await assertFails(setDoc(doc(db, 'recipes/r2'), recipe(ADULT, { tags: ['dessert', 42] })));
    await assertFails(setDoc(doc(db, 'recipes/r3'), recipe(ADULT, { tags: [''] })));
    await assertFails(setDoc(doc(db, 'recipes/r4'), recipe(ADULT, { tags: ['a', 'b', 'c', 'd', 'e', 'x'.repeat(25)] })));
  });

  test('a recipe whose origin left the family can still be changed, but not pointed at someone unknown', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'recipes/r1'), {
        ...recipe(ADULT, { originMemberId: 'gone', origin: '' }),
        createdAt: Timestamp.fromMillis(0),
        updatedAt: Timestamp.fromMillis(0),
      });
    });
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(doc(db, 'recipes/r1'), { title: 'Gâteau de Mamie', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'recipes/r1'), { originMemberId: 'nobody', updatedAt: serverTimestamp() }));
  });

  test('the author or the admin principal adds up to 3 photos, and the family sees them', async () => {
    await stored('r1', ADULT);
    const photo = (author, slot) => ({ authorId: author.memberId, authorName: author.memberId, slot, createdAt: serverTimestamp() });
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'recipes/r1/photos/adult1_1'), photo(ADULT, 1)));
    await assertSucceeds(setDoc(doc(as(env, ADMIN), 'recipes/r1/photos/admin1_3'), photo(ADMIN, 3)));
    await assertFails(setDoc(doc(as(env, ADULT), 'recipes/r1/photos/adult1_4'), photo(ADULT, 4)));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'recipes/r1/photos/adult2_1'), photo(OTHER_ADULT, 1)));
    await assertSucceeds(getDocs(collection(as(env, NEIGHBOUR), 'recipes/r1/photos')));
    await assertFails(getDocs(collection(as(env, STRANGER), 'recipes/r1/photos')));
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), 'recipes/r1/photos/adult1_1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'recipes/r1/photos/adult1_1')));
  });

  test('every member comments in their own name; the author, the recipe author or the admin removes it', async () => {
    await stored('r1', ADULT);
    const comment = (author, text = 'Avec moins de sucre, parfait.') => ({
      text, authorId: author.memberId, authorName: author.memberId, createdAt: serverTimestamp(),
    });
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), 'recipes/r1/comments/c1'), comment(NEIGHBOUR)));
    await assertSucceeds(setDoc(doc(as(env, OTHER_ADULT), 'recipes/r1/comments/c2'), comment(OTHER_ADULT)));
    await assertSucceeds(setDoc(doc(as(env, OTHER_ADULT), 'recipes/r1/comments/c3'), comment(OTHER_ADULT)));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'recipes/r1/comments/c4'), comment(NEIGHBOUR)));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'recipes/r1/comments/c5'), comment(OTHER_ADULT, '')));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'recipes/r1/comments/c6'), comment(OTHER_ADULT, 'x'.repeat(501))));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'recipes/nope/comments/c7'), comment(OTHER_ADULT)));
    await assertFails(setDoc(doc(as(env, STRANGER), 'recipes/r1/comments/c8'), { ...comment(OTHER_ADULT), authorId: 'stranger' }));
    await assertSucceeds(getDocs(collection(as(env, NEIGHBOUR), 'recipes/r1/comments')));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'recipes/r1/comments/c1'), { text: 'Changé' }));
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), 'recipes/r1/comments/c2')));
    await assertSucceeds(deleteDoc(doc(as(env, NEIGHBOUR), 'recipes/r1/comments/c1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), 'recipes/r1/comments/c2')));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'recipes/r1/comments/c3')));
  });
});
