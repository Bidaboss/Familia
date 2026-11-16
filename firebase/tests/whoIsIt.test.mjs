// Rules of « Qui est-ce ? » (PRD, récit 43): an old photo, the family guesses who and when, the
// answer shows once one has guessed. Fictional family: admin1 (admin principal), adult1 and adult2
// in h1, adult3 in h2.
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

const post = (author, overrides = {}) => ({
  authorId: author.memberId, authorName: author.memberId, hint: 'Un mariage, au printemps', createdAt: serverTimestamp(), ...overrides,
});
const guess = (who, overrides = {}) => ({ who: 'Mamie Jeanne', year: 1962, name: who.memberId, createdAt: serverTimestamp(), ...overrides });

/** A post of adult1 with its answer, written without the rules. */
async function stored() {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'whoIsIt/w1'), { ...post(ADULT), createdAt: Timestamp.fromMillis(0) });
    await setDoc(doc(ctx.firestore(), 'whoIsIt/w1/answer/main'), { who: 'Mamie Jeanne', year: 1961 });
  });
}

describe('« Qui est-ce ? » (récit 43)', () => {
  test('a member posts a photo in their own name, and the whole family sees it', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'whoIsIt/w1'), post(ADULT)));
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), 'whoIsIt/w1')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'whoIsIt/w1')));
    await assertFails(setDoc(doc(as(env, ADULT), 'whoIsIt/w2'), post(OTHER_ADULT)));
    await assertFails(setDoc(doc(as(env, ADULT), 'whoIsIt/w3'), post(ADULT, { hint: 'x'.repeat(141) })));
    await assertFails(setDoc(doc(as(env, ADULT), 'whoIsIt/w4'), post(ADULT, { score: 3 })));
  });

  test('only the author writes the answer, and a post is never edited', async () => {
    await stored();
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'whoIsIt/w1/answer/main'), { who: 'Mamie Jeanne et Papi Louis', year: 1961, personId: 'kin1' }));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'whoIsIt/w1/answer/main'), { who: 'Moi', year: 1990 }));
    await assertFails(setDoc(doc(as(env, ADULT), 'whoIsIt/w1/answer/main'), { who: '', year: 1961 }));
    await assertFails(setDoc(doc(as(env, ADULT), 'whoIsIt/w1/answer/main'), { who: 'Mamie', year: 1700 }));
    await assertFails(setDoc(doc(as(env, ADULT), 'whoIsIt/w1/answer/other'), { who: 'Mamie', year: 1961 }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'whoIsIt/w1'), { hint: 'Autre' }));
  });

  test('nobody reads the answer before guessing; after, the answer and the guesses show', async () => {
    await stored();
    const other = as(env, OTHER_ADULT);
    await assertFails(getDoc(doc(other, 'whoIsIt/w1/answer/main')));
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'whoIsIt/w1/answer/main')));
    await assertSucceeds(setDoc(doc(other, 'whoIsIt/w1/guesses/adult2'), guess(OTHER_ADULT)));
    await assertSucceeds(getDoc(doc(other, 'whoIsIt/w1/answer/main')));
    await assertFails(getDoc(doc(as(env, NEIGHBOUR), 'whoIsIt/w1/guesses/adult2')));
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'whoIsIt/w1/guesses/adult2')));
  });

  test('one guess in one\'s own name, « Je ne sais pas » allowed, never changed, not by the author', async () => {
    await stored();
    const other = as(env, OTHER_ADULT);
    await assertFails(setDoc(doc(other, 'whoIsIt/w1/guesses/adult3'), guess(NEIGHBOUR)));
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), 'whoIsIt/w1/guesses/adult3'), guess(NEIGHBOUR, { who: '', year: null })));
    await assertSucceeds(setDoc(doc(other, 'whoIsIt/w1/guesses/adult2'), guess(OTHER_ADULT)));
    await assertFails(updateDoc(doc(other, 'whoIsIt/w1/guesses/adult2'), { year: 1963 }));
    await assertFails(deleteDoc(doc(other, 'whoIsIt/w1/guesses/adult2')));
    await assertFails(setDoc(doc(as(env, ADULT), 'whoIsIt/w1/guesses/adult1'), guess(ADULT)));
    await assertFails(setDoc(doc(as(env, ADMIN), 'whoIsIt/w1/guesses/admin1'), guess(ADMIN, { year: 1800 })));
  });

  test('the author or the admin principal removes a post, nobody else', async () => {
    await stored();
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), 'whoIsIt/w1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'whoIsIt/w1')));
    await stored();
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), 'whoIsIt/w1')));
  });
});
