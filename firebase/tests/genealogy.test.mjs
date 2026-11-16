// Rules of the family tree (PRD, récit 40). Fictional family: admin1 (admin principal) and admin2
// (chef de famille) in h1 with adult1 and adult2; adult3 alone in h2.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, updateDoc, writeBatch } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, SECOND_ADMIN, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
});

/** A relative without an account, added to [householdId] by [by]. */
function kin(by, householdId = 'h1', overrides = {}) {
  return {
    firstName: 'Jeanne',
    lastName: 'Martin',
    living: false,
    born: '1921',
    died: '1999-03-04',
    note: 'Elle faisait les meilleures crêpes.',
    householdId,
    updatedBy: by.memberId,
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

function link(by, kind, aId, bId) {
  return { kind, aId, bId, updatedBy: by.memberId, updatedAt: serverTimestamp() };
}

async function seedKin(id, householdId) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `kin/${id}`), { ...kin(ADMIN, householdId), updatedAt: new Date(0) });
  });
}

describe('family tree (récit 40)', () => {
  test('the admin principal and the chef of the household add a relative; everyone reads it', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADMIN), 'kin/k1'), kin(ADMIN, 'h2')));
    await assertSucceeds(setDoc(doc(as(env, SECOND_ADMIN), 'kin/k2'), kin(SECOND_ADMIN)));
    await assertFails(setDoc(doc(as(env, SECOND_ADMIN), 'kin/k3'), kin(SECOND_ADMIN, 'h2')));
    await assertFails(setDoc(doc(as(env, ADULT), 'kin/k4'), kin(ADULT)));
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), 'kin/k2')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'kin/k2')));
  });

  test('a relative has a first name, a living one has no date of death', async () => {
    const db = as(env, ADMIN);
    await assertSucceeds(setDoc(doc(db, 'kin/ok'), { firstName: 'Sacha', living: true, householdId: 'h1', updatedBy: 'admin1', updatedAt: serverTimestamp() }));
    await assertFails(setDoc(doc(db, 'kin/k1'), kin(ADMIN, 'h1', { living: true })));
    await assertFails(setDoc(doc(db, 'kin/k2'), kin(ADMIN, 'h1', { firstName: '  ' })));
    await assertFails(setDoc(doc(db, 'kin/k3'), kin(ADMIN, 'h1', { born: '04/03/1921' })));
    await assertFails(setDoc(doc(db, 'kin/k4'), kin(ADMIN, 'h1', { note: 'x'.repeat(501) })));
    await assertFails(setDoc(doc(db, 'kin/k5'), kin(ADMIN, 'h1', { updatedBy: 'adult1' })));
    await assertFails(setDoc(doc(db, 'kin/k6'), kin(ADMIN, 'h9')));
    await assertFails(setDoc(doc(db, 'kin/k7'), kin(ADMIN, 'h1', { email: 'x@example.com' })));
  });

  test('a chef edits and removes the relatives of their household only', async () => {
    await seedKin('mine', 'h1');
    await seedKin('theirs', 'h2');
    const chef = as(env, SECOND_ADMIN);
    await assertSucceeds(updateDoc(doc(chef, 'kin/mine'), { note: 'Née à Lyon', updatedBy: 'admin2', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(chef, 'kin/mine'), { householdId: 'h2', updatedBy: 'admin2', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(chef, 'kin/theirs'), { note: 'x', updatedBy: 'admin2', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(chef, 'kin/theirs')));
    await assertFails(deleteDoc(doc(as(env, ADULT), 'kin/mine')));
    await assertSucceeds(deleteDoc(doc(chef, 'kin/mine')));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'kin/theirs')));
  });

  test('links join two people of the tree, once; a chef joins partners on either side, a parent only to a child of their household', async () => {
    await seedKin('k1', 'h1');
    await seedKin('k2', 'h2');
    const chef = as(env, SECOND_ADMIN);
    await assertSucceeds(setDoc(doc(chef, 'kinLinks/parent_k1_adult1'), link(SECOND_ADMIN, 'parent', 'k1', 'adult1')));
    await assertSucceeds(setDoc(doc(chef, 'kinLinks/partner_adult1_adult3'), link(SECOND_ADMIN, 'partner', 'adult1', 'adult3')));
    await assertFails(setDoc(doc(chef, 'kinLinks/parent_k2_adult3'), link(SECOND_ADMIN, 'parent', 'k2', 'adult3')));
    await assertSucceeds(setDoc(doc(as(env, ADMIN), 'kinLinks/parent_k2_adult3'), link(ADMIN, 'parent', 'k2', 'adult3')));
    await assertFails(setDoc(doc(as(env, ADULT), 'kinLinks/parent_adult1_adult2'), link(ADULT, 'parent', 'adult1', 'adult2')));
    // PRD rév. 195: a chef never declares someone of their household parent of another household's child.
    await assertFails(setDoc(doc(chef, 'kinLinks/parent_adult1_adult3'), link(SECOND_ADMIN, 'parent', 'adult1', 'adult3')));
    await assertSucceeds(setDoc(doc(chef, 'kinLinks/parent_adult3_adult2'), link(SECOND_ADMIN, 'parent', 'adult3', 'adult2')));

    const admin = as(env, ADMIN);
    await assertFails(setDoc(doc(admin, 'kinLinks/partner_adult3_adult2'), link(ADMIN, 'partner', 'adult3', 'adult2')), 'partners in order');
    await assertFails(setDoc(doc(admin, 'kinLinks/parent_adult1_adult2x'), link(ADMIN, 'parent', 'adult1', 'adult2')), 'named after the link');
    await assertFails(setDoc(doc(admin, 'kinLinks/parent_adult1_adult1'), link(ADMIN, 'parent', 'adult1', 'adult1')));
    await assertFails(setDoc(doc(admin, 'kinLinks/parent_ghost_adult1'), link(ADMIN, 'parent', 'ghost', 'adult1')));
    await assertFails(setDoc(doc(admin, 'kinLinks/cousin_adult1_adult2'), link(ADMIN, 'cousin', 'adult1', 'adult2')));
    await assertFails(updateDoc(doc(admin, 'kinLinks/parent_k1_adult1'), { updatedAt: serverTimestamp() }));

    await assertSucceeds(getDoc(doc(as(env, OTHER_ADULT), 'kinLinks/parent_k1_adult1')));
    await assertFails(deleteDoc(doc(as(env, ADULT), 'kinLinks/parent_k1_adult1')));
    await assertFails(deleteDoc(doc(chef, 'kinLinks/parent_k2_adult3')));
    await assertSucceeds(deleteDoc(doc(chef, 'kinLinks/parent_k1_adult1')));
  });

  test('a relative and their first link go in one batch', async () => {
    const db = as(env, SECOND_ADMIN);
    const batch = writeBatch(db);
    batch.set(doc(db, 'kin/new'), kin(SECOND_ADMIN));
    batch.set(doc(db, 'kinLinks/parent_new_adult2'), link(SECOND_ADMIN, 'parent', 'new', 'adult2'));
    await assertSucceeds(batch.commit());
  });

  test('each member keeps their own words for the people of the tree', async () => {
    const mine = { words: { adult2: 'bonus-maman', k1: 'Mamie Jeanne' }, updatedAt: serverTimestamp() };
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'kinWords/adult1'), mine));
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'kinWords/adult1')));
    await assertFails(getDoc(doc(as(env, OTHER_ADULT), 'kinWords/adult1')));
    await assertFails(getDoc(doc(as(env, ADMIN), 'kinWords/adult1')));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'kinWords/adult1'), mine));
    await assertFails(setDoc(doc(as(env, ADULT), 'kinWords/adult1'), { ...mine, extra: true }));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), 'kinWords/adult1')));
  });
});
