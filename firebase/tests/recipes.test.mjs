// Rules of the recipe book (PRD, récit 38). Fictional family: admin1 (admin principal), adult1,
// adult2 in h1, adult3 in h2.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
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
});
