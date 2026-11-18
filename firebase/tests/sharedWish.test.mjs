// « Cadeau commun avec… » (PRD, récit 73): a wish of « Mes envies » shared with a member of the same
// household. Like its beneficiary, whoever shares it never learns that it is reserved, nor by whom.
// Fictional family: adult1, adult2, admin1 in h1, adult3 in h2, kid2 (child without the app in h1,
// parent adult2).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  arrayUnion, collection, deleteDoc, doc, getDoc, getDocs, increment, query, serverTimestamp, setDoc, Timestamp,
  updateDoc, where, writeBatch,
} from 'firebase/firestore';
import { ADULT, NEIGHBOUR, OTHER_ADULT, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const OLD = Timestamp.fromMillis(0);
const WISHES = 'giftLists/wishes-adult1';
const SHARED = `${WISHES}/items/shared`;
const PLAIN = `${WISHES}/items/plain`;

function storedItem(overrides = {}) {
  return {
    title: 'Un vélo', url: null, price: 120, note: null,
    authorId: 'adult1', authorName: 'adult1', visibleToBeneficiary: true,
    createdAt: OLD, updatedAt: OLD, ...overrides,
  };
}

function wishes(beneficiaryId, overrides = {}) {
  return {
    kind: 'wishes', title: 'Mes envies', occasion: null, date: null, beneficiaryId, viewerIds: [],
    creatorId: beneficiaryId, creatorName: beneficiaryId, revealed: false, createdAt: OLD, updatedAt: OLD, ...overrides,
  };
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'members/kid2'), adultDoc({ memberId: 'kid2' }, { email: null, profile: 'childWithoutApp', parentIds: ['adult2'] }));
    await setDoc(doc(db, WISHES), wishes('adult1', { coBeneficiaryIds: ['adult2'] }));
    await setDoc(doc(db, SHARED), storedItem({ coBeneficiaryId: 'adult2' }));
    await setDoc(doc(db, PLAIN), storedItem({ title: 'Un livre', price: 15 }));
    await setDoc(doc(db, `${WISHES}/items/free`), storedItem({ title: 'Un pull', coBeneficiaryId: 'adult2' }));
    await setDoc(doc(db, `${WISHES}/items/bought`), storedItem({ title: 'Une tente', coBeneficiaryId: 'adult2', alreadyBought: true }));
    // adult3 (another household) reserves the shared wish as a joint gift, and the plain one alone.
    await setDoc(doc(db, `${WISHES}/claims/shared`), { type: 'shared', holderId: 'adult3', holderName: 'adult3', createdAt: OLD });
    await setDoc(doc(db, `${WISHES}/claims/shared/shares/adult3`), { amount: 20, memberName: 'adult3', received: false, updatedAt: OLD });
    await setDoc(doc(db, `${WISHES}/claims/plain`), { type: 'solo', holderId: 'adult3', holderName: 'adult3', createdAt: OLD });
    await setDoc(doc(db, 'giftLists/wishes-adult3'), wishes('adult3'));
    await setDoc(doc(db, 'giftLists/wishes-adult3/items/a1'), storedItem({ authorId: 'adult3', authorName: 'adult3' }));
  });
});

/** A new shared wish as the app writes it: the wish and the list naming who shares it, in one batch. */
function addShared(db, coId, { list = WISHES, id = 'new', name = true } = {}) {
  const batch = writeBatch(db);
  batch.set(doc(db, `${list}/items/${id}`), {
    ...storedItem({ authorId: 'adult1', authorName: 'adult1' }), coBeneficiaryId: coId,
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
  });
  if (name) batch.update(doc(db, list), { coBeneficiaryIds: arrayUnion(coId), updatedAt: serverTimestamp() });
  return batch.commit();
}

describe('sharing a wish', () => {
  test('with a member of the household, the list naming them in the same write', async () => {
    await assertSucceeds(addShared(as(env, ADULT), 'admin1'));
  });

  test('with a child without the app of the household', async () => {
    await assertSucceeds(addShared(as(env, ADULT), 'kid2'));
  });

  test('never without the list naming them', async () => {
    await assertFails(addShared(as(env, ADULT), 'admin1', { name: false }));
  });

  test('never with someone of another household, nor oneself, nor a stranger', async () => {
    await assertFails(addShared(as(env, ADULT), 'adult3'));
    await assertFails(addShared(as(env, ADULT), 'adult1'));
    await assertFails(addShared(as(env, ADULT), 'nobody'));
  });

  test('an existing wish becomes shared', async () => {
    const db = as(env, ADULT);
    const batch = writeBatch(db);
    batch.update(doc(db, PLAIN), { coBeneficiaryId: 'admin1', updatedAt: serverTimestamp() });
    batch.update(doc(db, WISHES), { coBeneficiaryIds: arrayUnion('admin1'), updatedAt: serverTimestamp() });
    await assertSucceeds(batch.commit());
  });

  test('the list never forgets who shares one of its wishes', async () => {
    await assertFails(updateDoc(doc(as(env, ADULT), WISHES), { coBeneficiaryIds: [], updatedAt: serverTimestamp() }));
  });
});

describe('whoever shares the wish never learns it is reserved', () => {
  test('they cannot list the reservations of the list', async () => {
    await assertFails(getDocs(collection(as(env, OTHER_ADULT), `${WISHES}/claims`)));
  });

  test('they cannot read the reservation of their shared wish, nor its parts', async () => {
    const db = as(env, OTHER_ADULT);
    await assertFails(getDoc(doc(db, `${WISHES}/claims/shared`)));
    await assertFails(getDoc(doc(db, `${WISHES}/claims/shared/shares/adult3`)));
    await assertFails(getDocs(collection(db, `${WISHES}/claims/shared/shares`)));
  });

  test('a reservation of a wish they do not share stays readable one by one', async () => {
    await assertSucceeds(getDoc(doc(as(env, OTHER_ADULT), `${WISHES}/claims/plain`)));
  });

  test('they cannot reserve it, join it, nor take part in it', async () => {
    const db = as(env, OTHER_ADULT);
    await assertFails(setDoc(doc(db, `${WISHES}/claims/free`), {
      type: 'solo', holderId: 'adult2', holderName: 'adult2', createdAt: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(db, `${WISHES}/claims/shared/shares/adult2`), {
      amount: 10, memberName: 'adult2', received: false, updatedAt: serverTimestamp(),
    }));
    const batch = writeBatch(db);
    batch.set(doc(db, `${WISHES}/items/bought/contributions/adult2`), { amount: 10, memberName: 'adult2', updatedAt: serverTimestamp() });
    batch.update(doc(db, `${WISHES}/items/bought`), { contributedTotal: increment(10) });
    await assertFails(batch.commit());
  });

  test('the beneficiary still reads no reservation', async () => {
    const db = as(env, ADULT);
    await assertFails(getDocs(collection(db, `${WISHES}/claims`)));
    await assertFails(getDoc(doc(db, `${WISHES}/claims/shared`)));
  });

  test('the rest of the family reads them as before', async () => {
    const db = as(env, NEIGHBOUR);
    await assertSucceeds(getDocs(collection(db, `${WISHES}/claims`)));
    await assertSucceeds(getDoc(doc(db, `${WISHES}/claims/shared`)));
  });

  test('editing the reserved wish is never refused: a refusal would tell', async () => {
    await assertSucceeds(updateDoc(doc(as(env, OTHER_ADULT), SHARED), { title: 'Un vélo bleu', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), SHARED), { title: 'Un vélo vert', updatedAt: serverTimestamp() }));
  });
});

describe('editing a shared wish', () => {
  test('whoever shares it edits it, without changing who shares it, nor removing it', async () => {
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(updateDoc(doc(db, `${WISHES}/items/free`), { price: 90, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, `${WISHES}/items/free`), { coBeneficiaryId: null, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, `${WISHES}/items/free`), { authorId: 'adult2', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(db, `${WISHES}/items/free`)));
  });

  test('nobody else of the family edits it', async () => {
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), `${WISHES}/items/free`), { price: 1, updatedAt: serverTimestamp() }));
  });

  test('a wish they do not share stays its author\'s', async () => {
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), PLAIN), { price: 1, updatedAt: serverTimestamp() }));
  });

  test('the parent of a child who shares a wish edits it for them', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await updateDoc(doc(db, WISHES), { coBeneficiaryIds: ['adult2', 'kid2'] });
      await setDoc(doc(db, `${WISHES}/items/withKid`), storedItem({ coBeneficiaryId: 'kid2' }));
    });
    await assertSucceeds(updateDoc(doc(as(env, OTHER_ADULT), `${WISHES}/items/withKid`), { price: 50, updatedAt: serverTimestamp() }));
  });
});

describe('the shared wishes in the list of the other', () => {
  test('anyone reads the wishes shared with a member, by the query of the app', async () => {
    const shared = (db) => getDocs(query(collection(db, `${WISHES}/items`),
      where('coBeneficiaryId', '==', 'adult2'), where('visibleToBeneficiary', '==', true)));
    await assertSucceeds(shared(as(env, OTHER_ADULT)));
    await assertSucceeds(shared(as(env, NEIGHBOUR)));
    await assertSucceeds(shared(as(env, ADULT)));
  });

  test('a list nobody shares a wish of keeps its reservations open to the family, as before', async () => {
    await assertSucceeds(getDocs(collection(as(env, OTHER_ADULT), 'giftLists/wishes-adult3/claims')));
  });
});
