// Surprises (brief Cadeaux et Surprise du 25/11): the beneficiary never reads their surprise, even as
// admin principal; only an adult starts one; anyone else with the app may join it.
// Fictional family: adult1, adult2, admin1 (admin principal), admin2 in h1, adult3 in h2, kid1 (child
// with the app, parent adult1), kid2 (child without the app, parent adult2).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  arrayUnion, collection, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp, setDoc, Timestamp, updateDoc, where, writeBatch,
} from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };
const OLD = Timestamp.fromMillis(0);

/** A stored surprise for admin1 (the admin principal), made by adult2 with adult1. */
function stored(overrides = {}) {
  return {
    beneficiaryId: 'admin1', title: 'Les 40 ans', occasion: 'birthday', date: '2027-02-03',
    memberIds: ['adult2', 'adult1'], creatorId: 'adult2', creatorName: 'adult2', status: 'preparing',
    createdAt: OLD, updatedAt: OLD, ...overrides,
  };
}

/** A new surprise as the app writes it. */
function fresh(by, overrides = {}) {
  return {
    beneficiaryId: 'admin1', title: 'Noël', occasion: 'christmas', date: '2026-12-25',
    memberIds: [by.memberId], creatorId: by.memberId, creatorName: by.memberId, status: 'preparing',
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
  };
}

function openOf(s) {
  return { beneficiaryId: s.beneficiaryId, title: s.title, creatorName: s.creatorName, updatedAt: serverTimestamp() };
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
    await setDoc(doc(db, 'members/kid2'), adultDoc({ memberId: 'kid2' }, { email: null, profile: 'childWithoutApp', parentIds: ['adult2'] }));
    await setDoc(doc(db, 'surprises/s1'), stored());
    await setDoc(doc(db, 'openSurprises/s1'), { beneficiaryId: 'admin1', title: 'Les 40 ans', creatorName: 'adult2', updatedAt: OLD });
  });
});

describe('reading', () => {
  test('a member reads the surprise, the beneficiary never, even as admin principal', async () => {
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'surprises/s1')));
    await assertFails(getDoc(doc(as(env, ADMIN), 'surprises/s1')));
    await assertFails(getDoc(doc(as(env, NEIGHBOUR), 'surprises/s1')));
  });

  test('my surprises are listed by membership', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(getDocs(query(collection(db, 'surprises'), where('memberIds', 'array-contains', 'adult1'))));
    await assertFails(getDocs(collection(db, 'surprises')));
  });

  test('the family but the beneficiary sees that a surprise exists, to join it', async () => {
    const open = (who, id) => getDocs(query(collection(as(env, who), 'openSurprises'), where('beneficiaryId', '==', id)));
    await assertSucceeds(open(NEIGHBOUR, 'admin1'));
    await assertFails(open(ADMIN, 'admin1'));
  });
});

describe('creating', () => {
  test('an adult starts a surprise for someone else, with its open line', async () => {
    const db = as(env, NEIGHBOUR);
    const s = fresh(NEIGHBOUR);
    const batch = writeBatch(db);
    batch.set(doc(db, 'surprises/n1'), s);
    batch.set(doc(db, 'openSurprises/n1'), openOf(s));
    await assertSucceeds(batch.commit());
  });

  test('never for myself, never with the beneficiary inside, never by a child', async () => {
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'surprises/n2'), fresh(NEIGHBOUR, { beneficiaryId: 'adult3' })));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'surprises/n3'), fresh(NEIGHBOUR, { memberIds: ['adult3', 'admin1'] })));
    await assertFails(setDoc(doc(as(env, KID), 'surprises/n4'), fresh(KID)));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'surprises/n5'), fresh(NEIGHBOUR, { beneficiaryId: 'nobody' })));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'surprises/n6'), fresh(NEIGHBOUR, { memberIds: ['adult1'] })));
  });

  test('the open line follows the surprise and only its creator writes it', async () => {
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'openSurprises/s1'), { ...openOf(stored()), title: 'Autre' }));
    await assertFails(setDoc(doc(as(env, ADULT), 'openSurprises/s1'), openOf(stored())));
  });
});

describe('changing', () => {
  test('the creator changes the settings, never the beneficiary', async () => {
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(updateDoc(doc(db, 'surprises/s1'), { title: 'Noël', occasion: 'christmas', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'surprises/s1'), { beneficiaryId: 'adult3', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'surprises/s1'), { memberIds: ['adult2', 'admin1'], updatedAt: serverTimestamp() }));
  });

  test('another member does not change the settings', async () => {
    await assertFails(updateDoc(doc(as(env, ADULT), 'surprises/s1'), { title: 'Noël', updatedAt: serverTimestamp() }));
  });

  test('anyone else with the app joins, only themselves', async () => {
    await assertSucceeds(updateDoc(doc(as(env, NEIGHBOUR), 'surprises/s1'), { memberIds: arrayUnion('adult3'), updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as(env, KID), 'surprises/s1'), { memberIds: arrayUnion('kid1'), updatedAt: serverTimestamp() }));
  });

  test('the beneficiary never joins, nobody adds someone else', async () => {
    await assertFails(updateDoc(doc(as(env, ADMIN), 'surprises/s1'), { memberIds: arrayUnion('admin1'), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'surprises/s1'), { memberIds: arrayUnion('kid1'), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'surprises/s1'), {
      memberIds: arrayUnion('adult3'), title: 'Autre', updatedAt: serverTimestamp(),
    }));
  });
});

describe('deleting', () => {
  test('only the creator deletes, with its open line', async () => {
    await assertFails(deleteDoc(doc(as(env, ADULT), 'surprises/s1')));
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), 'openSurprises/s1')));
    const db = as(env, OTHER_ADULT);
    const batch = writeBatch(db);
    batch.delete(doc(db, 'surprises/s1'));
    batch.delete(doc(db, 'openSurprises/s1'));
    await assertSucceeds(batch.commit());
  });
});
