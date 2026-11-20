// « À qui le tour ? » (PRD, récit 58) is removed: nobody reads or writes turns/ any more.
// Fictional family from helpers.mjs.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

function turn(author) {
  return {
    title: 'Qui reçoit Noël', householdIds: ['h1', 'h2'], current: 0, history: [], swap: null,
    createdBy: author.memberId, createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
  };
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'turns/t1'), { ...turn(ADULT), createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0) });
  });
});

describe('turns, closed', () => {
  test('nobody reads a rotation any more, not even the admin principal', async () => {
    await assertFails(getDoc(doc(as(env, ADULT), 'turns/t1')));
    await assertFails(getDocs(collection(as(env, ADMIN), 'turns')));
  });

  test('nobody creates, changes or removes one', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), 'turns/t2'), turn(ADULT)));
    await assertFails(updateDoc(doc(as(env, ADULT), 'turns/t1'), { current: 1, updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(as(env, ADMIN), 'turns/t1')));
  });
});
