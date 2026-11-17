// Rules of « À qui le tour ? » (PRD, récit 58). Fictional family: admin1 (admin principal),
// adult1, adult2 in h1, adult3 in h2; kid1, a child with the app in h1. h3 has nobody with the app.
import assert from 'node:assert/strict';
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { arrayUnion, deleteDoc, doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'households/h3'), { name: 'Foyer 3' });
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
  });
});

/** A valid new rotation by [author]. */
function turn(author, overrides = {}) {
  return {
    title: 'Qui reçoit Noël',
    householdIds: ['h1', 'h2', 'h3'],
    current: 0,
    history: [],
    swap: null,
    createdBy: author.memberId,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

async function stored(id, data) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `turns/${id}`), {
      ...data, createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
  });
}

const OLD = { householdId: 'h3', label: 'Noël 2025', at: Timestamp.fromMillis(0) };
const done = (current, label = 'Noël 2026') => ({
  history: arrayUnion({ householdId: 'h1', label, at: Timestamp.now() }), current, updatedAt: serverTimestamp(),
});

describe('create a rotation (récit 58)', () => {
  test('an adult creates one, the whole family reads it, a stranger does not', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'turns/t1'), turn(ADULT)));
    await assertSucceeds(getDoc(doc(as(env, KID), 'turns/t1')));
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), 'turns/t1')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'turns/t1')));
  });

  test('a child creates nothing', async () => {
    await assertFails(setDoc(doc(as(env, KID), 'turns/t1'), turn(KID)));
  });

  test('at least two households, each once, starting with the first, in the author\'s name', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'turns/t1'), turn(ADULT, { householdIds: ['h1'] })));
    await assertFails(setDoc(doc(db, 'turns/t1'), turn(ADULT, { householdIds: ['h1', 'h2', 'h1'] })));
    await assertFails(setDoc(doc(db, 'turns/t1'), turn(ADULT, { current: 1 })));
    await assertFails(setDoc(doc(db, 'turns/t1'), turn(ADULT, { history: [OLD] })));
    await assertFails(setDoc(doc(db, 'turns/t1'), turn(ADULT, { createdBy: 'adult2' })));
    await assertFails(setDoc(doc(db, 'turns/t1'), turn(ADULT, { title: '  ' })));
    await assertFails(setDoc(doc(db, 'turns/t1'), turn(ADULT, { count: 3 })));
  });
});

describe('change or remove a rotation', () => {
  beforeEach(async () => { await stored('t1', turn(ADULT, { history: [OLD] })); });

  test('the author changes the title and the order; the admin principal too', async () => {
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'turns/t1'), { title: 'Repas du dimanche', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'turns/t1'), { householdIds: ['h2', 'h1'], current: 1, updatedAt: serverTimestamp() }));
  });

  test('another adult changes nothing, nor the history', async () => {
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'turns/t1'), { title: 'Autre', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'turns/t1'), { history: [], updatedAt: serverTimestamp() }));
  });

  test('the author and the admin principal remove it, nobody else', async () => {
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), 'turns/t1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'turns/t1')));
  });
});

describe('« C\'est fait » and « On passe notre tour »', () => {
  beforeEach(async () => { await stored('t1', turn(NEIGHBOUR, { history: [OLD] })); });

  test('an adult of the household whose turn it is says it is done: the next household\'s turn', async () => {
    await assertSucceeds(updateDoc(doc(as(env, OTHER_ADULT), 'turns/t1'), done(1)));
  });

  test('not a child, not another household, not a jump, not another history', async () => {
    await assertFails(updateDoc(doc(as(env, KID), 'turns/t1'), done(1)));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'turns/t1'), done(2)));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'turns/t1'), {
      history: [{ householdId: 'h1', label: '', at: Timestamp.now() }], current: 1, updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'turns/t1'), {
      history: arrayUnion({ householdId: 'h2', label: '', at: Timestamp.now() }), current: 1, updatedAt: serverTimestamp(),
    }));
  });

  test('no limit on the history: one more line after 150', async () => {
    const many = Array.from({ length: 150 }, (_, i) => ({ householdId: 'h3', label: `Année ${i}`, at: Timestamp.fromMillis(i) }));
    await stored('t1', turn(NEIGHBOUR, { history: many }));
    await assertSucceeds(updateDoc(doc(as(env, OTHER_ADULT), 'turns/t1'), done(1)));
  });

  test('said offline, it may reach the server days later, 6 days later yes, 8 days no, nor in the future', async () => {
    const at = (ms) => ({ history: arrayUnion({ householdId: 'h1', label: '', at: Timestamp.fromMillis(Date.now() + ms) }), current: 1, updatedAt: serverTimestamp() });
    const day = 24 * 3600 * 1000;
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'turns/t1'), at(-8 * day)));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'turns/t1'), at(11 * 60 * 1000)));
    await assertSucceeds(updateDoc(doc(as(env, OTHER_ADULT), 'turns/t1'), at(-6 * day)));
  });

  test('the author of the rotation can say it for the household', async () => {
    await assertSucceeds(updateDoc(doc(as(env, NEIGHBOUR), 'turns/t1'), done(1)));
  });

  test('the household whose turn it is passes: it changes place with the next one', async () => {
    await stored('t1', turn(ADMIN, { current: 1 }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'turns/t1'), { householdIds: ['h1', 'h3', 'h2'], updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'turns/t1'), { householdIds: ['h2', 'h1', 'h3'], updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as(env, NEIGHBOUR), 'turns/t1'), { householdIds: ['h1', 'h3', 'h2'], updatedAt: serverTimestamp() }));
  });
});

describe('exchange places', () => {
  const ask = { fromId: 'h2', toId: 'h1', fromIndex: 1, toIndex: 0, byId: 'adult3', byName: 'adult3' };
  beforeEach(async () => { await stored('t1', turn(ADMIN)); });

  test('an adult proposes for their own household, in their own name, with the right places', async () => {
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'turns/t1'), { swap: { ...ask, toIndex: 2 }, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'turns/t1'), { swap: ask, updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as(env, NEIGHBOUR), 'turns/t1'), { swap: ask, updatedAt: serverTimestamp() }));
  });

  test('the other household accepts: the two change places', async () => {
    await stored('t1', turn(ADMIN, { swap: ask }));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'turns/t1'), { householdIds: ['h2', 'h1', 'h3'], swap: null, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, KID), 'turns/t1'), { householdIds: ['h2', 'h1', 'h3'], swap: null, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'turns/t1'), { householdIds: ['h3', 'h2', 'h1'], swap: null, updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'turns/t1'), { householdIds: ['h2', 'h1', 'h3'], swap: null, updatedAt: serverTimestamp() }));
  });

  test('the other household refuses, or the proposer withdraws', async () => {
    await stored('t1', turn(ADMIN, { swap: ask }));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'turns/t1'), { swap: null, updatedAt: serverTimestamp() }));
    await stored('t1', turn(ADMIN, { swap: ask }));
    await assertSucceeds(updateDoc(doc(as(env, NEIGHBOUR), 'turns/t1'), { swap: null, updatedAt: serverTimestamp() }));
  });

  test('one proposal at a time', async () => {
    await stored('t1', turn(ADMIN, { swap: ask }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'turns/t1'), {
      swap: { fromId: 'h1', toId: 'h3', fromIndex: 0, toIndex: 2, byId: 'adult1', byName: 'adult1' }, updatedAt: serverTimestamp(),
    }));
  });
});

// Le 21, two phones of the same household may say « C'est fait » at the same second, or an
// exchange may be withdrawn while the other household accepts it: the server lets one change hold,
// made from the rotation as it is, and the app tells the other one (TurnDetailViewModel).
describe('at the same moment (passe « en même temps »)', () => {
  async function current() {
    let data;
    await env.withSecurityRulesDisabled(async (ctx) => { data = (await getDoc(doc(ctx.firestore(), 'turns/t1'))).data(); });
    return data;
  }

  test('two « C\'est fait » in the same second: exactly one holds, the turn moves once', async () => {
    await stored('t1', turn(NEIGHBOUR, { history: [OLD] }));
    const results = await Promise.allSettled([
      updateDoc(doc(as(env, OTHER_ADULT), 'turns/t1'), done(1, 'Noël 2026')),
      updateDoc(doc(as(env, ADULT), 'turns/t1'), done(1, 'Noël')),
    ]);
    assert.equal(results.filter((r) => r.status === 'fulfilled').length, 1);
    const t = await current();
    assert.equal(t.current, 1);
    assert.equal(t.history.length, 2);
  });

  test('a « C\'est fait » sent again once the network is back, after another one, is refused', async () => {
    await stored('t1', turn(NEIGHBOUR, { history: [OLD] }));
    await assertSucceeds(updateDoc(doc(as(env, OTHER_ADULT), 'turns/t1'), done(1)));
    await assertFails(updateDoc(doc(as(env, ADULT), 'turns/t1'), done(1)));
    assert.equal((await current()).history.length, 2);
  });

  test('an exchange withdrawn while the other household accepts it: never accepted after', async () => {
    const ask = { fromId: 'h2', toId: 'h1', fromIndex: 1, toIndex: 0, byId: NEIGHBOUR.memberId, byName: NEIGHBOUR.memberId };
    // The turn of h3: the late « D'accord » of h1 cannot pass for « On passe notre tour » either.
    // (When the exchange is between the household whose turn it is and the next one, the same
    // write is a « On passe notre tour » of that household, which it may always say.)
    await stored('t1', turn(ADMIN, { swap: ask, current: 2 }));
    await assertSucceeds(updateDoc(doc(as(env, NEIGHBOUR), 'turns/t1'), { swap: null, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'turns/t1'), { householdIds: ['h2', 'h1', 'h3'], swap: null, updatedAt: serverTimestamp() }));
    assert.deepEqual((await current()).householdIds, ['h1', 'h2', 'h3']);
  });
});
