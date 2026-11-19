// « Pensez à moi » (PRD, récit 43) is retired (PRODUIT-136): its data stays, closed to new writes.
// Fictional family from helpers.mjs.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { Timestamp, deleteDoc, doc, getDoc, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';
import { ADULT, OTHER_ADULT, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

function moment(by, overrides = {}) {
  return {
    forMemberId: by.memberId, authorId: by.memberId, authorName: by.memberId, title: 'Examen de piano',
    day: '2026-12-12', status: 'accepted', createdAt: serverTimestamp(), ...overrides,
  };
}

function cheer(by, momentId = 'm1', forMemberId = ADULT.memberId) {
  return { momentId, forMemberId, authorId: by.memberId, authorName: by.memberId, text: 'Tu vas y arriver !', createdAt: serverTimestamp() };
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'moments/m1'), {
      ...moment(ADULT), createdAt: Timestamp.fromMillis(0), startedAt: Timestamp.fromMillis(Date.now() - 3600 * 1000),
    });
    await setDoc(doc(db, 'moments/waiting'), { ...moment(ADULT, { status: 'pending' }), createdAt: Timestamp.fromMillis(0) });
    await setDoc(doc(db, 'momentCheers/m1-adult2'), { ...cheer(OTHER_ADULT), createdAt: Timestamp.fromMillis(0) });
  });
});

describe('moments, closed to new writes', () => {
  test('nobody notes a moment any more, nor accepts one', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), 'moments/new'), moment(ADULT)));
    await assertFails(updateDoc(doc(as(env, ADULT), 'moments/waiting'), { status: 'accepted' }));
  });

  test('nobody writes an encouragement any more, even on a moment started today', async () => {
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'momentCheers/m1-adult2b'), cheer(OTHER_ADULT)));
  });

  test('what is there is still read by the family, and removed by its author or the person', async () => {
    await assertSucceeds(getDoc(doc(as(env, OTHER_ADULT), 'moments/m1')));
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'momentCheers/m1-adult2')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'moments/m1')));
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), 'moments/m1')));
    await assertSucceeds(deleteDoc(doc(as(env, OTHER_ADULT), 'momentCheers/m1-adult2')));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), 'moments/m1')));
  });
});
