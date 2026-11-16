// Rules of the accounts of a trip (PRD rév. 193, récit 16 bis). Fictional family: admin1 (admin
// principal), adult1, adult2, admin2 in h1, adult3 in h2, kid1 (child with the app, parent adult1).
// The trip t1 is created by adult1; adult1, adult3 and kid1 take part, adult2 only follows it.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADULT, NEIGHBOUR, OTHER_ADULT, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };
const BOOK = 'trip_t1';
const SHARES = { h1: 2.5, h2: 1 };

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
    await setDoc(doc(db, 'trips/t1'), {
      title: 'Corse', startDate: '2027-07-10', endDate: '2027-07-20',
      participantIds: ['adult1', 'adult3', 'kid1'], readerIds: ['adult1', 'adult2', 'adult3', 'kid1'],
      creatorId: 'adult1', creatorName: 'adult1', createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
  });
});

function book(overrides = {}) {
  return {
    containerType: 'trip', containerId: 't1', creatorId: 'adult1', shares: SHARES, closed: false,
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
  };
}

async function storedBook(overrides = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `expenseBooks/${BOOK}`), {
      ...book(overrides), createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
  });
}

/** A valid expense of 60 € paid by [author] for both households. */
function expense(author, overrides = {}) {
  const household = author === NEIGHBOUR ? 'h2' : 'h1';
  return {
    title: 'Courses', amountCents: 6000, date: '2027-07-12', payerId: author.memberId, payerHouseholdId: household,
    category: 'groceries', mode: 'shares', householdIds: ['h1', 'h2'], amounts: {}, currency: 'EUR',
    originalCents: 6000, rate: 1, authorId: author.memberId, authorName: author.memberId,
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
  };
}

async function storedExpense(id, author, overrides = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `expenseBooks/${BOOK}/expenses/${id}`), {
      ...expense(author, overrides), createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
  });
}

describe('the book of accounts', () => {
  test('an adult participant opens it, with the creator of the trip as its creator', async () => {
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), `expenseBooks/${BOOK}`), book()));
  });

  test('nobody else opens it, nor under another id or creator', async () => {
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), `expenseBooks/${BOOK}`), book()));
    await assertFails(setDoc(doc(as(env, KID), `expenseBooks/${BOOK}`), book()));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), `expenseBooks/${BOOK}`), book({ creatorId: 'adult3' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'expenseBooks/trip_t2'), book()));
  });

  test('only the adult participants read it; a missing book reads as missing', async () => {
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), `expenseBooks/${BOOK}`)));
    await storedBook();
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), `expenseBooks/${BOOK}`)));
    await assertFails(getDoc(doc(as(env, OTHER_ADULT), `expenseBooks/${BOOK}`)));
    await assertFails(getDoc(doc(as(env, KID), `expenseBooks/${BOOK}`)));
  });

  test('the creator changes the shares and closes; the shares stay frozen once closed', async () => {
    await storedBook();
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), `expenseBooks/${BOOK}`), { shares: { h1: 1, h2: 1 }, updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}`), { shares: { h1: 2, h2: 1 }, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}`), { creatorId: 'adult3', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}`), { closed: true, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}`), { shares: { h1: 1, h2: 1 }, updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}`)));
  });
});

describe('the expenses', () => {
  beforeEach(async () => { await storedBook(); });

  test('an adult participant adds one, in euros or at a rate typed by hand', async () => {
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), `expenseBooks/${BOOK}/expenses/e1`), expense(NEIGHBOUR)));
    await assertSucceeds(setDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}/expenses/e2`),
      expense(ADULT, { currency: 'CHF', originalCents: 6500, rate: 0.92, amountCents: 5980 })));
    await assertSucceeds(setDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}/expenses/e3`),
      expense(ADULT, { mode: 'amounts', amounts: { h1: 1000, h2: 5000 } })));
  });

  test('a reader of the trip and a child neither add nor read', async () => {
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), `expenseBooks/${BOOK}/expenses/e1`), expense(OTHER_ADULT)));
    await assertFails(setDoc(doc(as(env, KID), `expenseBooks/${BOOK}/expenses/e1`), expense(KID)));
    await storedExpense('e1', ADULT);
    await assertFails(getDoc(doc(as(env, OTHER_ADULT), `expenseBooks/${BOOK}/expenses/e1`)));
    await assertFails(getDoc(doc(as(env, KID), `expenseBooks/${BOOK}/expenses/e1`)));
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), `expenseBooks/${BOOK}/expenses/e1`)));
  });

  test('a wrong payer, household, split or currency is refused', async () => {
    const db = as(env, ADULT);
    const path = `expenseBooks/${BOOK}/expenses/e1`;
    await assertFails(setDoc(doc(db, path), expense(ADULT, { payerId: 'adult2' })));
    await assertFails(setDoc(doc(db, path), expense(ADULT, { payerHouseholdId: 'h2' })));
    await assertFails(setDoc(doc(db, path), expense(ADULT, { householdIds: ['h1', 'h9'] })));
    await assertFails(setDoc(doc(db, path), expense(ADULT, { amounts: { h1: 6000 } })));
    await assertFails(setDoc(doc(db, path), expense(ADULT, { mode: 'amounts', amounts: { h9: 6000 } })));
    await assertFails(setDoc(doc(db, path), expense(ADULT, { rate: 0.9 })));
    await assertFails(setDoc(doc(db, path), expense(ADULT, { amountCents: 0, originalCents: 0 })));
    await assertFails(setDoc(doc(db, path), expense(ADULT, { amountCents: 12.5, originalCents: 12.5 })));
    await assertFails(setDoc(doc(db, path), expense(ADULT, { authorId: 'adult3' })));
  });

  test('the author changes or deletes theirs, the creator of the trip any of them', async () => {
    await storedExpense('e1', NEIGHBOUR);
    await storedExpense('e2', ADULT);
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), `expenseBooks/${BOOK}/expenses/e2`), { title: 'Resto', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), `expenseBooks/${BOOK}/expenses/e2`)));
    await assertSucceeds(updateDoc(doc(as(env, NEIGHBOUR), `expenseBooks/${BOOK}/expenses/e1`), { title: 'Resto', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), `expenseBooks/${BOOK}/expenses/e1`), { authorId: 'adult1', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}/expenses/e1`), { amountCents: 5000, originalCents: 5000, updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}/expenses/e1`)));
  });

  test('nothing changes once the accounts are closed, and the history is the server’s', async () => {
    await storedExpense('e1', ADULT);
    await storedBook({ closed: true });
    await assertFails(setDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}/expenses/e2`), expense(ADULT)));
    await assertFails(updateDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}/expenses/e1`), { title: 'Resto', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}/expenses/e1`)));
    await assertFails(setDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}/expenses/e1/history/x`), { action: 'edited' }));
  });
});

describe('« C\'est remboursé »', () => {
  beforeEach(async () => { await storedBook(); });

  const settlement = (who, overrides = {}) => ({
    fromHouseholdId: 'h2', toHouseholdId: 'h1', amountCents: 2000, confirmedBy: who.memberId,
    confirmedByName: who.memberId, createdAt: serverTimestamp(), ...overrides,
  });

  test('ticked by an adult of the household that received the money, and taken back by them', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}/settlements/s1`), settlement(ADULT)));
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), `expenseBooks/${BOOK}/settlements/s1`)));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}/settlements/s1`)));
  });

  test('never by the household that pays, nor by someone outside the accounts', async () => {
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), `expenseBooks/${BOOK}/settlements/s1`), settlement(NEIGHBOUR)));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), `expenseBooks/${BOOK}/settlements/s1`), settlement(OTHER_ADULT)));
    await assertFails(setDoc(doc(as(env, ADULT), `expenseBooks/${BOOK}/settlements/s1`), settlement(ADULT, { fromHouseholdId: 'h1' })));
  });
});

// PRODUIT-57: the same brick on an event of the planning. The event e1 is written by adult2;
// adult3 and kid1 come, adult1 only answered « Peut-être ».
describe('the accounts of an event', () => {
  const EVENT_BOOK = 'event_e1';
  const eventBook = (overrides = {}) => book({ containerType: 'event', containerId: 'e1', creatorId: 'adult2', ...overrides });

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'events/e1'), { title: 'Week-end au lac', date: '2027-05-08', authorId: 'adult2', authorName: 'adult2' });
      await setDoc(doc(db, 'events/e1/rsvps/adult3'), { status: 'yes', answeredBy: 'adult3', updatedAt: Timestamp.fromMillis(0) });
      await setDoc(doc(db, 'events/e1/rsvps/kid1'), { status: 'yes', answeredBy: 'adult1', updatedAt: Timestamp.fromMillis(0) });
      await setDoc(doc(db, 'events/e1/rsvps/adult1'), { status: 'maybe', answeredBy: 'adult1', updatedAt: Timestamp.fromMillis(0) });
    });
  });

  test('an adult who comes, or the author, opens it, with the author as its creator', async () => {
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), `expenseBooks/${EVENT_BOOK}`), eventBook({ creatorId: 'adult3' })));
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), `expenseBooks/${EVENT_BOOK}`), eventBook()));
    await assertSucceeds(getDoc(doc(as(env, OTHER_ADULT), `expenseBooks/${EVENT_BOOK}`)));
  });

  test('« Peut-être », a child or another container type never use it', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), `expenseBooks/${EVENT_BOOK}`), eventBook()));
    await assertFails(setDoc(doc(as(env, KID), `expenseBooks/${EVENT_BOOK}`), eventBook()));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'expenseBooks/meal_e1'), eventBook({ containerType: 'meal' })));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `expenseBooks/${EVENT_BOOK}`), { ...eventBook(), createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0) });
    });
    await assertFails(getDoc(doc(as(env, ADULT), `expenseBooks/${EVENT_BOOK}`)));
  });

  test('the payer comes to the event; the author may pay too', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `expenseBooks/${EVENT_BOOK}`), { ...eventBook(), createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0) });
    });
    const path = `expenseBooks/${EVENT_BOOK}/expenses`;
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), `${path}/x1`), expense(NEIGHBOUR)));
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), `${path}/x2`), expense(NEIGHBOUR, { payerId: 'adult2', payerHouseholdId: 'h1' })));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), `${path}/x3`), expense(NEIGHBOUR, { payerId: 'adult1', payerHouseholdId: 'h1' })));
  });
});
