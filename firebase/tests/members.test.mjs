// Lot 2 rules: only family members (recognised by their verified Google address) can read the
// family; the only write is a member linking their own Firebase account on first sign-in.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';
import { ADULT, OTHER_ADULT, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); await seed(env); });

describe('reading the family', () => {
  test('a visitor who is not signed in reads nothing', async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, 'members/adult1')));
    await assertFails(getDoc(doc(db, 'households/h1')));
    await assertFails(getDoc(doc(db, `memberEmails/${ADULT.email}`)));
  });

  test('a Google account outside the family reads nothing about the family', async () => {
    const db = as(env, STRANGER);
    await assertFails(getDoc(doc(db, 'members/adult1')));
    await assertFails(getDoc(doc(db, 'households/h1')));
    await assertFails(getDoc(doc(db, `memberEmails/${ADULT.email}`)));
  });

  test('a Google account outside the family may check its own address, and finds nothing', async () => {
    const snap = await assertSucceeds(getDoc(doc(as(env, STRANGER), `memberEmails/${STRANGER.email}`)));
    if (snap.exists()) throw new Error('stranger should not be in the family');
  });

  test('an unverified address is treated as outside the family', async () => {
    const db = as(env, ADULT, { verified: false });
    await assertFails(getDoc(doc(db, 'members/adult1')));
  });

  test('a member reads members, households and the email index', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(getDoc(doc(db, 'members/adult2')));
    await assertSucceeds(getDoc(doc(db, 'households/h1')));
    await assertSucceeds(getDoc(doc(db, `memberEmails/${OTHER_ADULT.email}`)));
  });

  test('a member is recognised whatever the casing of the Google address', async () => {
    const db = as(env, { uid: ADULT.uid, email: 'Adult1@Example.COM' });
    await assertSucceeds(getDoc(doc(db, 'members/adult1')));
  });

  test('collections not opened yet stay closed', async () => {
    await assertFails(getDoc(doc(as(env, ADULT), 'giftSpaces/g1')));
  });
});

describe('linking the account on first sign-in', () => {
  test('a member links their own Firebase account', async () => {
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'members/adult1'), { uid: ADULT.uid }));
  });

  test('a member cannot link an account other than their own', async () => {
    await assertFails(updateDoc(doc(as(env, ADULT), 'members/adult1'), { uid: 'someone-else' }));
  });

  test('a Google account outside the family cannot write any uid', async () => {
    await assertFails(updateDoc(doc(as(env, STRANGER), 'members/adult1'), { uid: STRANGER.uid }));
  });

  test('a member cannot link another member', async () => {
    await assertFails(updateDoc(doc(as(env, ADULT), 'members/adult2'), { uid: ADULT.uid }));
  });

  test('a linked account cannot be replaced', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(doc(db, 'members/adult1'), { uid: ADULT.uid }));
    await assertFails(updateDoc(doc(db, 'members/adult1'), { uid: ADULT.uid + '-bis' }));
  });

  test('linking cannot be used to change other fields, such as becoming admin', async () => {
    await assertFails(updateDoc(doc(as(env, ADULT), 'members/adult1'), { uid: ADULT.uid, isAdmin: true }));
  });
});

describe('managing members is reserved to admins', () => {
  test('a member who is not admin creates or deletes nothing', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'members/new'), { firstName: 'x' }));
    await assertFails(deleteDoc(doc(db, 'members/adult2')));
    await assertFails(setDoc(doc(db, 'households/h2'), { name: 'x' }));
    await assertFails(setDoc(doc(db, `memberEmails/${STRANGER.email}`), { memberId: 'adult1' }));
  });

  test('a stranger cannot add themselves to the family', async () => {
    await assertFails(setDoc(doc(as(env, STRANGER), `memberEmails/${STRANGER.email}`), { memberId: 'adult1' }));
  });
});
