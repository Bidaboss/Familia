// Mon carnet de cadeaux (PRD, récit 9 bis): a notebook is read and written by its owner only.
// Fictional family: adult1, adult2, admin1 (admin principal) in h1, adult3 in h2.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const OLD = Timestamp.fromMillis(0);

/** A private idea written by the app. */
function idea(overrides = {}) {
  return {
    source: 'idea', personId: 'adult2', personName: 'adult2', occasion: 'christmas', year: 2026,
    title: 'Une écharpe', url: null, price: 25, note: null, status: 'idea',
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
  };
}

function budget(overrides = {}) {
  return { personId: 'adult2', personName: 'adult2', occasion: 'christmas', year: 2026, amount: 60, updatedAt: serverTimestamp(), ...overrides };
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'notebooks/adult1/entries/e1'), { ...idea(), createdAt: OLD, updatedAt: OLD });
    // A reservation taken up by the Cloud Function.
    await setDoc(doc(db, 'notebooks/adult1/entries/gift_l_i'), {
      source: 'claim', listId: 'l', itemId: 'i', personId: 'adult2', personName: 'adult2', occasion: 'christmas', year: 2026,
      title: 'Un livre', url: null, price: 15, note: null, status: 'idea', createdAt: OLD, updatedAt: OLD,
    });
    await setDoc(doc(db, 'notebooks/adult1/budgets/b1'), { ...budget(), updatedAt: OLD });
  });
});

describe('who reads a notebook', () => {
  test('its owner, and nobody else: not the person it is for, not the admin principal', async () => {
    await assertSucceeds(getDocs(collection(as(env, ADULT), 'notebooks/adult1/entries')));
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'notebooks/adult1/budgets/b1')));
    for (const who of [OTHER_ADULT, ADMIN, STRANGER]) {
      await assertFails(getDocs(collection(as(env, who), 'notebooks/adult1/entries')));
      await assertFails(getDoc(doc(as(env, who), 'notebooks/adult1/entries/e1')));
      await assertFails(getDoc(doc(as(env, who), 'notebooks/adult1/budgets/b1')));
    }
  });
});

describe('private ideas', () => {
  test('the owner adds one for a member, or for someone outside the family by first name', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'notebooks/adult1/entries/a'), idea()));
    await assertSucceeds(setDoc(doc(db, 'notebooks/adult1/entries/b'), idea({ personId: null, personName: 'Mamie Jo', occasion: null, year: null })));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'notebooks/adult1/entries/c'), idea()));
    await assertFails(setDoc(doc(as(env, ADMIN), 'notebooks/adult1/entries/c'), idea()));
  });

  test('an idea is well formed, and is never a reservation', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'notebooks/adult1/entries/a'), idea({ source: 'claim' })));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/entries/b'), idea({ personId: 'nobody' })));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/entries/c'), idea({ title: '' })));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/entries/d'), idea({ status: 'lost' })));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/entries/e'), idea({ url: 'javascript:alert(1)' })));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/entries/f'), idea({ listId: 'l' })));
  });

  test('the owner edits and removes their idea', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(doc(db, 'notebooks/adult1/entries/e1'), { status: 'bought', price: 22, updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(db, 'notebooks/adult1/entries/e1')));
  });
});

describe('a reservation taken up', () => {
  test('the owner follows it, but only the server changes what comes from the list', async () => {
    const db = as(env, ADULT);
    const entry = doc(db, 'notebooks/adult1/entries/gift_l_i');
    await assertSucceeds(updateDoc(entry, { status: 'wrapped', note: 'Caché dans le placard', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(entry, { price: 1, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(entry, { source: 'idea', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(entry));
  });
});

describe('budgets', () => {
  test('the owner sets one per person and occasion', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'notebooks/adult1/budgets/b2'), budget({ personId: null, personName: 'Mamie Jo' })));
    await assertSucceeds(updateDoc(doc(db, 'notebooks/adult1/budgets/b1'), { amount: 80, updatedAt: serverTimestamp() }));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/budgets/b3'), budget({ amount: 0 })));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'notebooks/adult1/budgets/b3'), budget()));
    await assertSucceeds(deleteDoc(doc(db, 'notebooks/adult1/budgets/b1')));
  });
});

// « Mon budget » (récit 57): a total for a whole occasion, strictly private, adults only.
describe('the budget of a whole occasion', () => {
  const total = (overrides = {}) => budget({ personId: null, personName: null, total: true, ...overrides });

  test('the owner sets one per occasion and year, at its one id', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'notebooks/adult1/budgets/total_christmas_2026'), total()));
    await assertSucceeds(updateDoc(doc(db, 'notebooks/adult1/budgets/total_christmas_2026'), { amount: 300, updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(db, 'notebooks/adult1/budgets/total_christmas_2026')));
  });

  test('a total names no person, and has its occasion, its year and its id', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'notebooks/adult1/budgets/x'), total()));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/budgets/total_birthday_2026'), total()));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/budgets/total_christmas_2026'), total({ personId: 'adult2' })));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/budgets/total_christmas_2026'), total({ personName: 'adult2' })));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/budgets/total_none_2026'), total({ occasion: null })));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/budgets/total_christmas_none'), total({ year: null })));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/budgets/total_christmas_2026'), total({ amount: 0 })));
    // A budget for a person always has their name.
    await assertFails(setDoc(doc(db, 'notebooks/adult1/budgets/b4'), budget({ personName: null })));
    await assertFails(setDoc(doc(db, 'notebooks/adult1/budgets/b4'), budget({ total: 'yes' })));
  });

  test('nobody else reads or writes it, the admin principal neither', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'notebooks/adult1/budgets/total_christmas_2026'), { ...total(), updatedAt: OLD });
    });
    for (const who of [OTHER_ADULT, ADMIN, STRANGER]) {
      await assertFails(getDoc(doc(as(env, who), 'notebooks/adult1/budgets/total_christmas_2026')));
      await assertFails(getDocs(collection(as(env, who), 'notebooks/adult1/budgets')));
      await assertFails(setDoc(doc(as(env, who), 'notebooks/adult1/budgets/total_birthday_2027'), total({ occasion: 'birthday', year: 2027 })));
      await assertFails(deleteDoc(doc(as(env, who), 'notebooks/adult1/budgets/total_christmas_2026')));
    }
  });

  test('a child does not have it', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), 'members/adult1'), { profile: 'childWithApp' });
    });
    await assertFails(setDoc(doc(as(env, ADULT), 'notebooks/adult1/budgets/total_christmas_2026'), total()));
  });
});
