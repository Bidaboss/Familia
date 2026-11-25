// « Ajouter une idée » after the prototype (correctif E11 à E13): a private idea keeps the picture of
// its link and « Où trouver le cadeau ? », as a wish does. Fictional family of helpers.mjs.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADULT, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const OLD = Timestamp.fromMillis(0);

function idea(overrides = {}) {
  return {
    source: 'idea', personId: 'adult2', personName: 'adult2', occasion: null, year: null,
    title: 'Un casque', url: 'https://boutique-exemple.fr/casque', price: 49.9, note: null, status: 'idea',
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
  };
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'notebooks/adult1/entries/e1'), { ...idea(), createdAt: OLD, updatedAt: OLD });
  });
});

describe('the picture of the link and « Où trouver le cadeau ? »', () => {
  test('a new idea keeps both', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'notebooks/adult1/entries/a'), idea({
      imageUrl: 'https://cdn.boutique-exemple.fr/casque.jpg', whereToFind: 'En ligne · boutique-exemple.fr',
    })));
  });

  test('an idea of an older app, without them, still saves', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'notebooks/adult1/entries/b'), idea()));
  });

  test('an idea changes them, or drops them', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(doc(db, 'notebooks/adult1/entries/e1'), {
      imageUrl: 'https://cdn.boutique-exemple.fr/casque.jpg', whereToFind: 'Le marché de Noël', updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(db, 'notebooks/adult1/entries/e1'), { imageUrl: null, whereToFind: null, updatedAt: serverTimestamp() }));
  });

  test('only an https picture, and never more than 120 characters to say where', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'notebooks/adult1/entries/c'), idea({ imageUrl: 'http://cdn.boutique-exemple.fr/casque.jpg' })));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/entries/c'), idea({ whereToFind: 'x'.repeat(121) })));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/entries/c'), idea({ whereToFind: 42 })));
  });
});

describe('its photo (E11), kept in the private folder of its author', () => {
  test('an idea names a photo of its own folder, changes it or drops it', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'notebooks/adult1/entries/p'), idea({ photo: 'notebooks/adult1/photos/abc123' })));
    await assertSucceeds(updateDoc(doc(db, 'notebooks/adult1/entries/e1'), { photo: 'notebooks/adult1/photos/def456', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db, 'notebooks/adult1/entries/e1'), { photo: null, updatedAt: serverTimestamp() }));
  });

  test('never a photo of another member, nor of another folder', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'notebooks/adult1/entries/q'), idea({ photo: 'notebooks/adult2/photos/abc123' })));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/entries/q'), idea({ photo: 'giftLists/wishes-adult2/shared/adult1/p1' })));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/entries/q'), idea({ photo: 'notebooks/adult1/photos/../x' })));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/entries/q'), idea({ photo: 42 })));
  });
});
