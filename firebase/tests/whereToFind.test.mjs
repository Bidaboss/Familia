// « Où trouver le cadeau ? » (correctif E6): free text on a wish, a shop, a town or a site, 120
// characters at most. Fictional family: adult1 keeps their « Mes envies ».
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADULT, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const OLD = Timestamp.fromMillis(0);
const WISHES = 'giftLists/wishes-adult1';

function newItem(overrides = {}) {
  return {
    title: 'Un casque', url: null, price: 49.9, note: null,
    authorId: 'adult1', authorName: 'adult1', visibleToBeneficiary: true,
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
  };
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, WISHES), {
      kind: 'wishes', title: 'Mes envies', occasion: null, date: null, beneficiaryId: 'adult1', viewerIds: [],
      creatorId: 'adult1', creatorName: 'adult1', revealed: false, createdAt: OLD, updatedAt: OLD,
    });
    await setDoc(doc(db, `${WISHES}/items/w1`), { ...newItem(), createdAt: OLD, updatedAt: OLD });
  });
});

describe('« Où trouver le cadeau ? »', () => {
  test('a new wish says where to find it', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), `${WISHES}/items/new`), newItem({ whereToFind: 'En ligne · boutique-exemple.fr' })));
  });

  test('a wish changes it, or drops it', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(doc(db, `${WISHES}/items/w1`), { whereToFind: 'Le marché de Noël', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db, `${WISHES}/items/w1`), { whereToFind: null, updatedAt: serverTimestamp() }));
  });

  test('never more than 120 characters', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), `${WISHES}/items/new`), newItem({ whereToFind: 'x'.repeat(121) })));
  });

  test('never anything but text', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), `${WISHES}/items/new`), newItem({ whereToFind: 42 })));
  });
});
