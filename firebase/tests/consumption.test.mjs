// Rules of consumption/{day}: the daily use of the database (PRD, récit 1 sexies).
import { after, before, beforeEach, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, setDoc } from 'firebase/firestore';
import { ADMIN, ADULT, as, createEnv, seed } from './helpers.mjs';

let env;

before(async () => {
  env = await createEnv();
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'consumption/2026-11-20'), { day: '2026-11-20', reads: 3000, writes: 400, deletes: 0 });
  });
});

test('the admin principal reads the consumption, nobody else does', async () => {
  await assertSucceeds(getDoc(doc(as(env, ADMIN), 'consumption/2026-11-20')));
  await assertSucceeds(getDocs(collection(as(env, ADMIN), 'consumption')));
  await assertFails(getDoc(doc(as(env, ADULT), 'consumption/2026-11-20')));
  await assertFails(getDocs(collection(as(env, ADULT), 'consumption')));
});

test('only the server writes it, not even the admin principal', async () => {
  const db = as(env, ADMIN);
  await assertFails(setDoc(doc(db, 'consumption/2026-11-21'), { day: '2026-11-21', reads: 0 }));
  await assertFails(deleteDoc(doc(db, 'consumption/2026-11-20')));
});
