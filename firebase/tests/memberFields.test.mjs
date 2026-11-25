// Récit 114 (lot A5): the surnom of a member, set by the member or the admin principal only.
// Fictional family from helpers.mjs: admin1 (admin principal), admin2 (chef of h1), adult1, adult2 in h1.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteField, doc, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, SECOND_ADMIN, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
});

describe('surnom (récit 114)', () => {
  test('a member sets and removes their own surnom, 24 characters at most', async () => {
    const mine = doc(as(env, ADULT), 'members/adult1');
    await assertSucceeds(updateDoc(mine, { nickname: 'Mamie' }));
    await assertSucceeds(updateDoc(mine, { nickname: 'x'.repeat(24) }));
    await assertFails(updateDoc(mine, { nickname: 'x'.repeat(25) }));
    await assertFails(updateDoc(mine, { nickname: '' }));
    await assertFails(updateDoc(mine, { nickname: 3 }));
    await assertSucceeds(updateDoc(mine, { nickname: deleteField() }));
  });

  test('with it, never their first name, birth date, profile or household', async () => {
    const mine = doc(as(env, ADULT), 'members/adult1');
    await assertFails(updateDoc(mine, { nickname: 'Mamie', firstName: 'Autre' }));
    await assertFails(updateDoc(mine, { birthDate: '1960-01-01' }));
    await assertFails(updateDoc(mine, { householdId: 'h2' }));
    await assertFails(updateDoc(mine, { profile: 'childWithApp' }));
  });

  test('nobody else sets it but the admin principal, not even a chef of the household', async () => {
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'members/adult1'), { nickname: 'Papi' }));
    await assertFails(updateDoc(doc(as(env, SECOND_ADMIN), 'members/adult1'), { nickname: 'Papi' }));
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'members/adult1'), { nickname: 'Papi' }));
    await assertFails(updateDoc(doc(as(env, ADMIN), 'members/adult1'), { nickname: 'x'.repeat(25) }));
  });
});
