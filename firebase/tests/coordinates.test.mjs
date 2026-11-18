// Récit 66: an optional phone for each adult, an optional address for each household.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteField, doc, setDoc, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, NEIGHBOUR, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); await seed(env); });

describe('phone of a member', () => {
  test('an adult sets and removes their own phone', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(doc(db, 'members/adult1'), { phone: '+33 6 12 34 56 78' }));
    await assertSucceeds(updateDoc(doc(db, 'members/adult1'), { phone: deleteField() }));
  });

  test('a phone that is not one is refused', async () => {
    const db = as(env, ADULT);
    await assertFails(updateDoc(doc(db, 'members/adult1'), { phone: 'appelle-moi' }));
    await assertFails(updateDoc(doc(db, 'members/adult1'), { phone: '12' }));
    await assertFails(updateDoc(doc(db, 'members/adult1'), { phone: 612345678 }));
  });

  test('nobody sets the phone of someone else, nor anything else with it', async () => {
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'members/adult1'), { phone: '06 12 34 56 78' }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'members/adult1'), { phone: '06 12 34 56 78', firstName: 'Autre' }));
  });

  test('never a phone for a child', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'members/kid1'), adultDoc({ memberId: 'kid1', email: null }, { profile: 'childWithoutApp', parentIds: ['adult1'] }));
    });
    await assertFails(updateDoc(doc(as(env, ADMIN), 'members/kid1'), { phone: '06 12 34 56 78' }));
  });

  test('the admin principal keeps the phone when editing a member', async () => {
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'members/adult1'), { phone: '06 12 34 56 78' }));
  });
});

describe('address of a household', () => {
  test('who manages the household sets its address', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADMIN), 'households/h1'), { name: 'Foyer 1', address: '1 rue des Lilas, 75000 Paris' }));
  });

  test('a member who does not manage it cannot', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), 'households/h1'), { name: 'Foyer 1', address: '1 rue des Lilas' }));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'households/h1'), { name: 'Foyer 1', address: '1 rue des Lilas' }));
  });

  test('an empty or too long address is refused', async () => {
    const db = as(env, ADMIN);
    await assertFails(setDoc(doc(db, 'households/h1'), { name: 'Foyer 1', address: '' }));
    await assertFails(setDoc(doc(db, 'households/h1'), { name: 'Foyer 1', address: 'a'.repeat(201) }));
  });
});
