// Lot 3c rules: an admin may store a member's color, one of the 13 colors of the app.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); await seed(env); });

describe('member color', () => {
  test('an admin picks a color between 0 and 12', async () => {
    const db = as(env, ADMIN);
    await assertSucceeds(updateDoc(doc(db, 'members/adult1'), { colorIndex: 0 }));
    await assertSucceeds(updateDoc(doc(db, 'members/adult1'), { colorIndex: 12 }));
  });

  test('a color outside the 13 colors is refused', async () => {
    const db = as(env, ADMIN);
    await assertFails(updateDoc(doc(db, 'members/adult1'), { colorIndex: 13 }));
    await assertFails(updateDoc(doc(db, 'members/adult1'), { colorIndex: -1 }));
    await assertFails(updateDoc(doc(db, 'members/adult1'), { colorIndex: 2.5 }));
    await assertFails(updateDoc(doc(db, 'members/adult1'), { colorIndex: 'red' }));
  });

  test('a member who is not admin cannot change a color, even their own', async () => {
    await assertFails(updateDoc(doc(as(env, ADULT), 'members/adult1'), { colorIndex: 3 }));
  });
});
