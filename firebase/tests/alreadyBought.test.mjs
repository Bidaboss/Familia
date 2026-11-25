// « Déjà acheté, participation bienvenue » (PRD rév. 309, récit 68). Not a surprise: the keeper of
// the wishes reads the total and every first name, never an amount (récit 93); the others only their part.
// Fictional family: adult1, adult2, admin1 in h1, adult3 in h2, kid1 (child with the app, parent
// adult1), kid2 (child without the app, parent adult2).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  collection, deleteDoc, doc, getDoc, getDocs, increment, serverTimestamp, setDoc, Timestamp, updateDoc, writeBatch,
} from 'firebase/firestore';
import { ADULT, NEIGHBOUR, OTHER_ADULT, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };
const OLD = Timestamp.fromMillis(0);
const WISHES = 'giftLists/wishes-adult1';
const BOUGHT = `${WISHES}/items/bought`;

function storedItem(overrides = {}) {
  return {
    title: 'Un vélo', url: null, price: 120, note: null,
    authorId: 'adult1', authorName: 'adult1', visibleToBeneficiary: true,
    createdAt: OLD, updatedAt: OLD, ...overrides,
  };
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
    await setDoc(doc(db, 'members/kid2'), adultDoc({ memberId: 'kid2' }, { email: null, profile: 'childWithoutApp', parentIds: ['adult2'] }));
    const wishes = (beneficiaryId) => ({
      kind: 'wishes', title: 'Mes envies', occasion: null, date: null, beneficiaryId, viewerIds: [],
      creatorId: beneficiaryId, creatorName: beneficiaryId, revealed: false, createdAt: OLD, updatedAt: OLD,
    });
    await setDoc(doc(db, WISHES), wishes('adult1'));
    await setDoc(doc(db, `${WISHES}/items/plain`), storedItem({ title: 'Un livre', price: 15 }));
    await setDoc(doc(db, BOUGHT), storedItem({ alreadyBought: true, contributedTotal: 30 }));
    await setDoc(doc(db, `${BOUGHT}/contributions/adult3`), { amount: 30, memberName: 'adult3', updatedAt: OLD });
    await setDoc(doc(db, `${BOUGHT}/contributors/adult3`), { memberName: 'adult3', updatedAt: OLD });
    // An own list of adult1, where « Déjà acheté » has no place.
    await setDoc(doc(db, 'giftLists/own1'), {
      ...wishes('adult1'), kind: 'own', title: 'Noël', occasion: 'christmas', date: '2026-12-25', viewerIds: ['adult1', 'adult2'],
    });
    await setDoc(doc(db, 'giftLists/own1/items/o1'), storedItem());
    // The wishes of kid2, kept by adult2.
    await setDoc(doc(db, 'giftLists/wishes-kid2'), wishes('kid2'));
    await setDoc(doc(db, 'giftLists/wishes-kid2/items/k1'), storedItem({ authorId: 'adult2', authorName: 'adult2', alreadyBought: true }));
  });
});

/** « Je participe » as the app writes it: the part and the total, in one batch. */
function contribute(db, who, amount, previous = 0, item = BOUGHT) {
  const batch = writeBatch(db);
  batch.set(doc(db, `${item}/contributions/${who.memberId}`), { amount, memberName: who.memberId, updatedAt: serverTimestamp() });
  batch.set(doc(db, `${item}/contributors/${who.memberId}`), { memberName: who.memberId, updatedAt: serverTimestamp() });
  batch.update(doc(db, item), { contributedTotal: increment(amount - previous) });
  return batch.commit();
}

describe('the keeper checks « Déjà acheté »', () => {
  test('on a wish of « Mes envies »', async () => {
    await assertSucceeds(updateDoc(doc(as(env, ADULT), `${WISHES}/items/plain`), { alreadyBought: true, updatedAt: serverTimestamp() }));
  });

  test('on a new wish', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), `${WISHES}/items/new`), {
      ...storedItem(), alreadyBought: true, createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
  });

  test('never with a total of its own', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), `${WISHES}/items/new`), {
      ...storedItem(), alreadyBought: true, contributedTotal: 50, createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(as(env, ADULT), BOUGHT), { contributedTotal: 200, updatedAt: serverTimestamp() }));
  });

  test('never on another list than « Mes envies »', async () => {
    await assertFails(updateDoc(doc(as(env, ADULT), 'giftLists/own1/items/o1'), { alreadyBought: true, updatedAt: serverTimestamp() }));
  });

  test('cannot uncheck it while someone takes part, but can once nobody does', async () => {
    await assertFails(updateDoc(doc(as(env, ADULT), BOUGHT), { alreadyBought: false, updatedAt: serverTimestamp() }));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), BOUGHT), { contributedTotal: 0 });
    });
    await assertSucceeds(updateDoc(doc(as(env, ADULT), BOUGHT), { alreadyBought: false, updatedAt: serverTimestamp() }));
  });

  test('editing the wish keeps the total', async () => {
    await assertSucceeds(updateDoc(doc(as(env, ADULT), BOUGHT), { title: 'Un vélo rouge', updatedAt: serverTimestamp() }));
  });
});

describe('« Je participe »', () => {
  // La bascule vers les surprises (plan Cadeaux et Surprise, PR 3): « Je participe » goes into the
  // surprises; here nobody takes part any more, nor changes a part. An old part may be taken back.
  test('since the switch to surprises, nobody takes part nor changes a part', async () => {
    await assertFails(contribute(as(env, OTHER_ADULT), OTHER_ADULT, 45));
    await assertFails(contribute(as(env, NEIGHBOUR), NEIGHBOUR, 50, 30));
  });

  test('an old part is still taken back', async () => {
    const db = as(env, NEIGHBOUR);
    const batch = writeBatch(db);
    batch.delete(doc(db, `${BOUGHT}/contributions/adult3`));
    batch.delete(doc(db, `${BOUGHT}/contributors/adult3`));
    batch.update(doc(db, BOUGHT), { contributedTotal: increment(-30) });
    await assertSucceeds(batch.commit());
  });

  test('a total that does not match the part is refused', async () => {
    await assertFails(contribute(as(env, OTHER_ADULT), OTHER_ADULT, 45, 5));
    const db = as(env, OTHER_ADULT);
    await assertFails(setDoc(doc(db, `${BOUGHT}/contributions/adult2`), { amount: 45, memberName: 'adult2', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, BOUGHT), { contributedTotal: increment(45) }));
    // Taking back one's part without lowering the total.
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), `${BOUGHT}/contributions/adult3`)));
  });

  test('in whole euros, from 1 to 10000', async () => {
    const db = as(env, OTHER_ADULT);
    await assertFails(contribute(db, OTHER_ADULT, 0));
    await assertFails(contribute(db, OTHER_ADULT, 12.5));
    await assertFails(contribute(db, OTHER_ADULT, 10001));
  });

  test('only for oneself and with one\'s own first name', async () => {
    const db = as(env, OTHER_ADULT);
    const batch = writeBatch(db);
    batch.set(doc(db, `${BOUGHT}/contributions/adult3`), { amount: 10, memberName: 'adult3', updatedAt: serverTimestamp() });
    batch.update(doc(db, BOUGHT), { contributedTotal: increment(10) });
    await assertFails(batch.commit());
    const named = writeBatch(db);
    named.set(doc(db, `${BOUGHT}/contributions/adult2`), { amount: 10, memberName: 'adult3', updatedAt: serverTimestamp() });
    named.update(doc(db, BOUGHT), { contributedTotal: increment(10) });
    await assertFails(named.commit());
  });

  test('never on a wish that is not already bought', async () => {
    await assertFails(contribute(as(env, OTHER_ADULT), OTHER_ADULT, 10, 0, `${WISHES}/items/plain`));
  });

  test('never the person, nor a child', async () => {
    await assertFails(contribute(as(env, ADULT), ADULT, 10));
    await assertFails(contribute(as(env, KID), KID, 10));
    // The keeper of a child without the app does not take part in what they already bought.
    await assertFails(contribute(as(env, OTHER_ADULT), OTHER_ADULT, 10, 0, 'giftLists/wishes-kid2/items/k1'));
    // Since the switch to surprises, nobody else either.
    await assertFails(contribute(as(env, NEIGHBOUR), NEIGHBOUR, 10, 0, 'giftLists/wishes-kid2/items/k1'));
  });

  test('« Je le prends » is refused on a wish already bought, and since the switch on any wish', async () => {
    const claim = (itemId) => setDoc(doc(as(env, OTHER_ADULT), `${WISHES}/claims/${itemId}`), {
      type: 'solo', holderId: 'adult2', holderName: 'adult2', createdAt: serverTimestamp(),
    });
    await assertFails(claim('bought'));
    await assertFails(claim('plain'));
  });
});

describe('who reads the parts', () => {
  test('the person reads the first names, never an amount', async () => {
    await assertSucceeds(getDocs(collection(as(env, ADULT), `${BOUGHT}/contributors`)));
    await assertFails(getDocs(collection(as(env, ADULT), `${BOUGHT}/contributions`)));
    await assertFails(getDoc(doc(as(env, ADULT), `${BOUGHT}/contributions/adult3`)));
  });

  test('the keeper of a child without the app too', async () => {
    await assertSucceeds(getDocs(collection(as(env, OTHER_ADULT), 'giftLists/wishes-kid2/items/k1/contributors')));
    await assertFails(getDocs(collection(as(env, OTHER_ADULT), 'giftLists/wishes-kid2/items/k1/contributions')));
  });

  test('the others only their own part, and the total on the wish', async () => {
    await assertFails(getDocs(collection(as(env, OTHER_ADULT), `${BOUGHT}/contributions`)));
    await assertFails(getDoc(doc(as(env, OTHER_ADULT), `${BOUGHT}/contributions/adult3`)));
    await assertFails(getDocs(collection(as(env, OTHER_ADULT), `${BOUGHT}/contributors`)));
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), `${BOUGHT}/contributions/adult3`)));
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), `${BOUGHT}/contributors/adult3`)));
    await assertSucceeds(getDoc(doc(as(env, OTHER_ADULT), BOUGHT)));
  });

  test('a first name alone is written for oneself, with the part, and carries no amount', async () => {
    const db = as(env, OTHER_ADULT);
    // Alone, without the part, it would be a name with nothing behind it.
    await assertFails(setDoc(doc(db, `${BOUGHT}/contributors/adult2`), { memberName: 'adult2', updatedAt: serverTimestamp() }));
    // With an amount in it, it would leak the part.
    const leaking = writeBatch(db);
    leaking.set(doc(db, `${BOUGHT}/contributions/adult2`), { amount: 10, memberName: 'adult2', updatedAt: serverTimestamp() });
    leaking.set(doc(db, `${BOUGHT}/contributors/adult2`), { memberName: 'adult2', amount: 10, updatedAt: serverTimestamp() });
    leaking.update(doc(db, BOUGHT), { contributedTotal: increment(10) });
    await assertFails(leaking.commit());
    // For somebody else.
    const other = writeBatch(db);
    other.set(doc(db, `${BOUGHT}/contributions/adult2`), { amount: 10, memberName: 'adult2', updatedAt: serverTimestamp() });
    other.set(doc(db, `${BOUGHT}/contributors/adult3`), { memberName: 'adult3', updatedAt: serverTimestamp() });
    other.update(doc(db, BOUGHT), { contributedTotal: increment(10) });
    await assertFails(other.commit());
  });

  test('an old part without its name gets none any more: the old system is closed', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `${BOUGHT}/contributions/adult2`), { amount: 5, memberName: 'adult2', updatedAt: OLD });
    });
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), `${BOUGHT}/contributors/adult2`), { memberName: 'adult2', updatedAt: serverTimestamp() }));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), `${BOUGHT}/contributors/adult2`), { memberName: 'adult2', updatedAt: serverTimestamp() }));
  });

  test('taking one\'s name back is not possible while the part stands', async () => {
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), `${BOUGHT}/contributors/adult3`)));
  });
});
