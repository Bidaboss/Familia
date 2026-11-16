// Vœux d'anniversaire rules (PRD, récit 36, avis EXPERT n° 11). Fictional family from helpers.mjs:
// adult1 is celebrated, adult2 and adult3 write to them.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';
import { ADULT, NEIGHBOUR, OTHER_ADULT, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const YEAR = 2026;
const cardId = (by, forId = ADULT.memberId) => `${YEAR}-${forId}-${by.memberId}`;

function card(by, overrides = {}) {
  return {
    forMemberId: ADULT.memberId, authorId: by.memberId, authorName: by.memberId, text: 'Joyeux anniversaire !',
    year: YEAR, createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
  };
}

/** The Bonne nouvelle that Familia publishes on the morning of the birthday. */
async function birthdayStarted() {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `goodNews/birthday-${YEAR}-${ADULT.memberId}`), { text: 'x', birthdayOf: ADULT.memberId });
  });
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
});

describe('birthday cards', () => {
  test('on the day, a member writes one card to the person, signed with their name', async () => {
    await birthdayStarted();
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(setDoc(doc(db, `birthdayCards/${cardId(OTHER_ADULT)}`), card(OTHER_ADULT)));
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), `birthdayCards/${cardId(OTHER_ADULT)}`)));
    await assertFails(getDoc(doc(as(env, STRANGER), `birthdayCards/${cardId(OTHER_ADULT)}`)));
  });

  test('not before the day, not to oneself, not for someone else, not under another name', async () => {
    const db = as(env, OTHER_ADULT);
    await assertFails(setDoc(doc(db, `birthdayCards/${cardId(OTHER_ADULT)}`), card(OTHER_ADULT)));
    await birthdayStarted();
    await assertFails(setDoc(doc(as(env, ADULT), `birthdayCards/${cardId(ADULT)}`), card(ADULT)));
    await assertFails(setDoc(doc(db, `birthdayCards/${cardId(NEIGHBOUR)}`), card(NEIGHBOUR)));
    await assertFails(setDoc(doc(db, `birthdayCards/${cardId(OTHER_ADULT)}`), card(OTHER_ADULT, { authorName: 'Mamie' })));
    await assertFails(setDoc(doc(db, `birthdayCards/other-id`), card(OTHER_ADULT)));
  });

  test('a short word only, and nothing else', async () => {
    await birthdayStarted();
    const db = as(env, OTHER_ADULT);
    const ref = doc(db, `birthdayCards/${cardId(OTHER_ADULT)}`);
    await assertFails(setDoc(ref, card(OTHER_ADULT, { text: '  ' })));
    await assertFails(setDoc(ref, card(OTHER_ADULT, { text: 'a'.repeat(281) })));
    await assertFails(setDoc(ref, card(OTHER_ADULT, { count: 3 })));
    await assertFails(setDoc(ref, card(OTHER_ADULT, { year: '2026' })));
  });

  test('the author changes or removes their card; nobody else does', async () => {
    await birthdayStarted();
    await assertSucceeds(setDoc(doc(as(env, OTHER_ADULT), `birthdayCards/${cardId(OTHER_ADULT)}`), card(OTHER_ADULT)));
    await assertFails(updateDoc(doc(as(env, ADULT), `birthdayCards/${cardId(OTHER_ADULT)}`), { text: 'Changé', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(as(env, ADULT), `birthdayCards/${cardId(OTHER_ADULT)}`)));
    const mine = doc(as(env, OTHER_ADULT), `birthdayCards/${cardId(OTHER_ADULT)}`);
    await assertFails(updateDoc(mine, { forMemberId: NEIGHBOUR.memberId, updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(mine, { text: 'Bon anniversaire !', updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(mine));
  });

  test('a member can turn the wishes notifications off', async () => {
    const settings = doc(as(env, ADULT), `members/${ADULT.memberId}/settings/notifications`);
    await assertSucceeds(setDoc(settings, { birthdayWishes: false }));
    await assertFails(setDoc(settings, { birthdayWishes: 'non' }));
  });

  test('nobody fakes the Bonne nouvelle of Familia', async () => {
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), `goodNews/birthday-${YEAR}-${ADULT.memberId}`), {
      text: 'x', photoIds: ['p1'], authorId: OTHER_ADULT.memberId, authorName: OTHER_ADULT.memberId, forMemberId: OTHER_ADULT.memberId,
      householdId: 'h1', mentionIds: [], reactions: {}, hiddenByIds: [], birthdayOf: ADULT.memberId,
      createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
  });
});
