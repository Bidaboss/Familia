// La fiche membre (PRD, récit 7 bis): sizes, shoe size, allergies and tastes. The whole family
// reads it; the member, an adult of the same household for a child, or the admin principal
// changes it, like the photo. Fictional family of helpers.mjs, plus child1 (with the app, h1).
import { after, afterEach, before, beforeEach, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
afterEach(async () => { await env.clearFirestore(); });

const CHILD = { memberId: 'child1', email: 'child1@example.com', uid: 'uid-child1' };

beforeEach(async () => {
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${CHILD.email}`), { memberId: CHILD.memberId });
    await setDoc(doc(db, `members/${CHILD.memberId}`), adultDoc(CHILD, { profile: 'childWithApp', parentIds: [ADULT.memberId] }));
    await setDoc(doc(db, 'memberCards/adult2'), { top: 'M', bottom: '40', shoes: '39', allergies: '', tastes: 'Le thé', updatedBy: 'adult2', updatedAt: new Date() });
  });
});

function card(by, overrides = {}) {
  return { top: 'S', bottom: '38', shoes: '37', allergies: 'Arachides', tastes: 'Les polars', updatedBy: by.memberId, updatedAt: serverTimestamp(), ...overrides };
}

test('the whole family reads a card, nobody else', async () => {
  await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), 'memberCards/adult2')));
  await assertSucceeds(getDoc(doc(as(env, CHILD), 'memberCards/adult2')));
  await assertFails(getDoc(doc(as(env, STRANGER), 'memberCards/adult2')));
});

test('the member fills in their own card, signed with their name', async () => {
  await assertSucceeds(setDoc(doc(as(env, ADULT), 'memberCards/adult1'), card(ADULT)));
  await assertFails(setDoc(doc(as(env, ADULT), 'memberCards/adult1'), card(ADULT, { updatedBy: 'adult2' })));
  await assertSucceeds(setDoc(doc(as(env, CHILD), 'memberCards/child1'), card(CHILD)));
});

test('an adult of the household for a child, or the admin principal; never another adult', async () => {
  await assertSucceeds(setDoc(doc(as(env, OTHER_ADULT), 'memberCards/child1'), card(OTHER_ADULT)));
  await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'memberCards/child1'), card(NEIGHBOUR)));
  await assertFails(setDoc(doc(as(env, ADULT), 'memberCards/adult2'), card(ADULT)));
  await assertFails(setDoc(doc(as(env, CHILD), 'memberCards/adult1'), card(CHILD)));
  await assertSucceeds(setDoc(doc(as(env, ADMIN), 'memberCards/adult3'), card(ADMIN)));
  await assertFails(deleteDoc(doc(as(env, ADULT), 'memberCards/adult2')));
  await assertSucceeds(deleteDoc(doc(as(env, OTHER_ADULT), 'memberCards/adult2')));
});

test('short sizes, bounded texts, no other field, and no card for a member who does not exist', async () => {
  const me = doc(as(env, ADULT), 'memberCards/adult1');
  await assertSucceeds(setDoc(me, card(ADULT, { top: 'x'.repeat(20), allergies: 'x'.repeat(300) })));
  await assertFails(setDoc(me, card(ADULT, { shoes: 'x'.repeat(21) })));
  await assertFails(setDoc(me, card(ADULT, { tastes: 'x'.repeat(301) })));
  await assertFails(setDoc(me, card(ADULT, { shoes: 39 })));
  await assertFails(setDoc(me, card(ADULT, { birthday: '01/01' })));
  const { tastes, ...missing } = card(ADULT);
  await assertFails(setDoc(me, missing));
  await assertFails(setDoc(doc(as(env, ADMIN), 'memberCards/ghost'), card(ADMIN)));
});
