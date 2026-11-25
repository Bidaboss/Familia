// Récit 114 (l'admin, 25/11 19 h 52): whoever looks after a child, one of its responsible adults or
// the chef of its household, with or without the app, writes its surnom, its card and its wishes;
// never its profile, household or parents, and never anything of another adult.
// Fictional family from helpers.mjs: admin1 (admin principal), admin2 (chef of h1), adult1, adult2
// in h1, adult3 in h2. Added here: kid1 (h1, with the app, parent adult1), kid4 (h2, with the app,
// parent adult1: a shared custody) and kid3 (h2, without the app, parent adult3).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, deleteField, doc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADULT, NEIGHBOUR, OTHER_ADULT, SECOND_ADMIN, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const OLD = Timestamp.fromMillis(0);
const KID_WISHES = 'giftLists/wishes-kid1';

function child(id, householdId, parentIds, profile = 'childWithApp') {
  return adultDoc({ memberId: id, email: profile === 'childWithApp' ? `${id}@example.com` : null }, { householdId, profile, parentIds });
}

function wishes(beneficiaryId) {
  return {
    kind: 'wishes', title: 'Mes envies', occasion: null, date: null, beneficiaryId, viewerIds: [],
    creatorId: beneficiaryId, creatorName: beneficiaryId, revealed: false, createdAt: OLD, updatedAt: OLD,
  };
}

function item(authorId, overrides = {}) {
  return {
    title: 'Un vélo', url: null, price: 120, note: null, authorId, authorName: authorId,
    visibleToBeneficiary: true, createdAt: OLD, updatedAt: OLD, ...overrides,
  };
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'members/kid1'), child('kid1', 'h1', ['adult1']));
    await setDoc(doc(db, 'members/kid4'), child('kid4', 'h2', ['adult1']));
    await setDoc(doc(db, 'members/kid3'), child('kid3', 'h2', ['adult3'], 'childWithoutApp'));
    await setDoc(doc(db, KID_WISHES), wishes('kid1'));
    // Its own wish, reserved by adult3; an idea of adult2 kept from it.
    await setDoc(doc(db, `${KID_WISHES}/items/w1`), item('kid1'));
    await setDoc(doc(db, `${KID_WISHES}/claims/w1`), { type: 'solo', holderId: 'adult3', holderName: 'adult3', createdAt: OLD });
    await setDoc(doc(db, `${KID_WISHES}/items/idea`), item('adult2', { visibleToBeneficiary: false }));
    await setDoc(doc(db, 'giftLists/wishes-kid3'), wishes('kid3'));
    await setDoc(doc(db, 'giftLists/wishes-kid3/items/w1'), item('adult3'));
    await setDoc(doc(db, 'giftLists/wishes-adult2'), wishes('adult2'));
    await setDoc(doc(db, 'giftLists/wishes-adult2/items/w1'), item('adult2'));
  });
});

describe('the surnom of a child (récit 114)', () => {
  test('its responsible adult and the chef of its household set and remove it', async () => {
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'members/kid1'), { nickname: 'Loulou' }));
    await assertSucceeds(updateDoc(doc(as(env, SECOND_ADMIN), 'members/kid1'), { nickname: 'Poussin' }));
    await assertSucceeds(updateDoc(doc(as(env, SECOND_ADMIN), 'members/kid1'), { nickname: deleteField() }));
    // A responsible adult of another household (shared custody) too.
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'members/kid4'), { nickname: 'Titou' }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'members/kid1'), { nickname: 'x'.repeat(25) }));
  });

  test('another adult of the household, or the chef of another one, does not', async () => {
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'members/kid1'), { nickname: 'Loulou' }));
    await assertFails(updateDoc(doc(as(env, SECOND_ADMIN), 'members/kid3'), { nickname: 'Loulou' }));
    await assertFails(updateDoc(doc(as(env, SECOND_ADMIN), 'members/kid4'), { nickname: 'Loulou' }));
  });

  test('never its first name, birth date, profile, household or parents: the admin principal\'s', async () => {
    const kid = doc(as(env, ADULT), 'members/kid1');
    await assertFails(updateDoc(kid, { nickname: 'Loulou', firstName: 'Autre' }));
    await assertFails(updateDoc(kid, { birthDate: '2015-01-01' }));
    await assertFails(updateDoc(kid, { profile: 'childWithoutApp' }));
    await assertFails(updateDoc(kid, { householdId: 'h2' }));
    await assertFails(updateDoc(kid, { parentIds: ['adult1', 'adult2'] }));
    await assertFails(updateDoc(doc(as(env, SECOND_ADMIN), 'members/kid1'), { firstName: 'Autre' }));
  });

  test('never the surnom of another adult, even for the chef of their household', async () => {
    await assertFails(updateDoc(doc(as(env, SECOND_ADMIN), 'members/adult1'), { nickname: 'Papi' }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'members/adult2'), { nickname: 'Papi' }));
  });
});

describe('the card of a child (récit 114)', () => {
  const card = (by) => ({ tastes: 'Les dinosaures', allergies: 'Arachide', updatedBy: by.memberId, updatedAt: serverTimestamp() });

  test('its responsible adult writes it, from another household too', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'memberCards/kid4'), card(ADULT)));
    await assertSucceeds(setDoc(doc(as(env, SECOND_ADMIN), 'memberCards/kid1'), card(SECOND_ADMIN)));
  });

  test('never the card of another adult, nor of a child of another household it does not look after', async () => {
    await assertFails(setDoc(doc(as(env, SECOND_ADMIN), 'memberCards/adult1'), card(SECOND_ADMIN)));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'memberCards/kid3'), card(OTHER_ADULT)));
  });
});

describe('the wishes of a child (récit 114)', () => {
  const edit = { title: 'Un vélo rouge', updatedAt: serverTimestamp() };

  test('its responsible adult and the chef of its household edit its wish, even reserved', async () => {
    await assertSucceeds(updateDoc(doc(as(env, ADULT), `${KID_WISHES}/items/w1`), edit));
    await assertSucceeds(updateDoc(doc(as(env, SECOND_ADMIN), `${KID_WISHES}/items/w1`), { price: 90, updatedAt: serverTimestamp() }));
    // Of a child without the app in another household, by its own responsible adult (as before).
    await assertSucceeds(updateDoc(doc(as(env, NEIGHBOUR), 'giftLists/wishes-kid3/items/w1'), edit));
  });

  test('the author, the visibility and the date stay those of the wish', async () => {
    const wish = doc(as(env, ADULT), `${KID_WISHES}/items/w1`);
    await assertFails(updateDoc(wish, { ...edit, authorId: 'adult1', authorName: 'adult1' }));
    await assertFails(updateDoc(wish, { ...edit, visibleToBeneficiary: false }));
    await assertFails(updateDoc(wish, { ...edit, createdAt: serverTimestamp() }));
  });

  test('an idea kept from the child stays its author\'s', async () => {
    await assertFails(updateDoc(doc(as(env, ADULT), `${KID_WISHES}/items/idea`), edit));
    await assertFails(deleteDoc(doc(as(env, ADULT), `${KID_WISHES}/items/idea`)));
  });

  test('its responsible adult and the chef of its household remove its wish', async () => {
    await assertSucceeds(deleteDoc(doc(as(env, SECOND_ADMIN), `${KID_WISHES}/items/w1`)));
  });

  test('another adult of the household, the chef of another one, or anyone on an adult\'s wishes does not', async () => {
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), `${KID_WISHES}/items/w1`), edit));
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), `${KID_WISHES}/items/w1`)));
    await assertFails(updateDoc(doc(as(env, SECOND_ADMIN), 'giftLists/wishes-kid3/items/w1'), edit));
    await assertFails(updateDoc(doc(as(env, SECOND_ADMIN), 'giftLists/wishes-adult2/items/w1'), edit));
    await assertFails(deleteDoc(doc(as(env, SECOND_ADMIN), 'giftLists/wishes-adult2/items/w1')));
  });
});
