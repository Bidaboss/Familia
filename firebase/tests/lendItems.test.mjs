// Rules of « On se prête » (PRD, récit 51). Fictional family: admin1 (admin principal), adult1,
// adult2 in h1, adult3 in h2; kid1, a child with the app (parent adult1). Who asked or has a
// thing, and since when, live in lendLoans, read by the owner, the borrower and the admin
// principal only (EXPERT-9, PRD rév. 268).
import { after, before, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  collection, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp, setDoc, Timestamp, updateDoc, where, writeBatch,
} from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };
const DAY = 24 * 60 * 60 * 1000;

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
  });
});

/** A valid new thing to lend by [owner]. */
function item(owner, overrides = {}) {
  return {
    name: 'Appareil à raclette',
    note: 'À rendre propre.',
    category: 'kitchen',
    photo: null,
    ownerId: owner.memberId,
    ownerName: owner.memberId,
    lent: false,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

async function stored(id, data, loan = null) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `lendItems/${id}`), {
      ...data, lent: loan !== null, createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
    if (loan) await setDoc(doc(db, `lendLoans/${id}`), { ...loan, updatedAt: Timestamp.fromMillis(0) });
  });
}

/** The ask of [who] to [owner], as the app writes it, with optional dates. */
function ask(owner, who, overrides = {}) {
  return {
    ownerId: owner.memberId, parties: [owner.memberId, who.memberId],
    status: 'asked', borrowerId: who.memberId, borrowerName: who.memberId,
    from: null, until: null, askedAt: serverTimestamp(), lentAt: null, remindedAt: null, updatedAt: serverTimestamp(),
    ...overrides,
  };
}

/** A stored ask of [who] to [owner]. */
const asked = (owner, who, overrides = {}) => ({
  ownerId: owner.memberId, parties: [owner.memberId, who.memberId],
  status: 'asked', borrowerId: who.memberId, borrowerName: who.memberId,
  from: null, until: Timestamp.fromMillis(Date.now() + 5 * DAY), askedAt: Timestamp.fromMillis(0), lentAt: null, remindedAt: null,
  ...overrides,
});

const lentTo = (owner, who) => asked(owner, who, { status: 'lent', lentAt: Timestamp.fromMillis(0) });

const flip = (lent) => ({ lent, updatedAt: serverTimestamp() });

/** « Je voudrais l'emprunter »: the loan and the thing taken, in one batch. */
function askBatch(who, id, loan) {
  const db = as(env, who);
  const batch = writeBatch(db);
  batch.set(doc(db, `lendLoans/${id}`), loan);
  batch.update(doc(db, `lendItems/${id}`), flip(true));
  return batch.commit();
}

/** « Pas cette fois », a dropped ask or « Rendu »: the loan removed and the thing freed, in one batch. */
function releaseBatch(who, id) {
  const db = as(env, who);
  const batch = writeBatch(db);
  batch.delete(doc(db, `lendLoans/${id}`));
  batch.update(doc(db, `lendItems/${id}`), flip(false));
  return batch.commit();
}

describe('add a thing to lend (récit 51)', () => {
  test('an adult adds theirs, and the whole family reads it', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT)));
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), 'lendItems/i1')));
    await assertSucceeds(getDoc(doc(as(env, KID), 'lendItems/i1')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'lendItems/i1')));
  });

  test('a child sees the list but adds nothing', async () => {
    await assertFails(setDoc(doc(as(env, KID), 'lendItems/i1'), item(KID)));
  });

  test('only in their own name, free, in a known category, with no money, and no loan on it', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(OTHER_ADULT)));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { ownerName: 'Quelqu\'un' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { lent: true })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { borrowerId: 'adult2' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { borrowerName: 'adult2' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { status: 'available' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { category: 'money' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { price: 10 })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { name: ' ' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { name: 'x'.repeat(61) })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { note: 'x'.repeat(301) })));
  });

  test('a photo only from the caller\'s own folder of this item', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { photo: 'lendItems/i1/adult1/p1' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i2'), item(ADULT, { photo: 'lendItems/i2/adult2/p1' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i3'), item(ADULT, { photo: 'lendItems/i1/adult1/p1' })));
  });
});

describe('change or remove a thing (récit 51)', () => {
  test('the owner and the admin principal change it, the loan stays', async () => {
    await stored('i1', item(ADULT), asked(ADULT, OTHER_ADULT));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'lendItems/i1'), { name: 'Raclette 6 personnes', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'lendItems/i1'), { category: 'other', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'lendItems/i1'), { name: 'À moi', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'lendItems/i1'), { ownerId: 'adult2', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'lendItems/i1'), { name: 'Raclette', lent: false, updatedAt: serverTimestamp() }));
  });

  test('the owner and the admin principal remove it, nobody else', async () => {
    await stored('i1', item(ADULT));
    await stored('i2', item(ADULT));
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), 'lendItems/i1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), 'lendItems/i1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'lendItems/i2')));
  });
});

describe('who sees a loan (EXPERT-9)', () => {
  test('the owner, the borrower and the admin principal read it; the rest of the family does not', async () => {
    await stored('i1', item(ADULT), lentTo(ADULT, OTHER_ADULT));
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'lendLoans/i1')));
    await assertSucceeds(getDoc(doc(as(env, OTHER_ADULT), 'lendLoans/i1')));
    await assertSucceeds(getDoc(doc(as(env, ADMIN), 'lendLoans/i1')));
    await assertFails(getDoc(doc(as(env, NEIGHBOUR), 'lendLoans/i1')));
    await assertFails(getDoc(doc(as(env, KID), 'lendLoans/i1')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'lendLoans/i1')));
  });

  test('everyone else reads only that the thing is lent, with no name and no date', async () => {
    await stored('i1', item(ADULT), lentTo(ADULT, OTHER_ADULT));
    const seen = (await getDoc(doc(as(env, NEIGHBOUR), 'lendItems/i1'))).data();
    assert.equal(seen.lent, true);
    for (const field of ['borrowerId', 'borrowerName', 'from', 'until', 'lentAt', 'askedAt', 'status']) assert.equal(field in seen, false);
  });

  test('each reads their own loans with one query; nobody lists the others\'', async () => {
    await stored('i1', item(ADULT), lentTo(ADULT, OTHER_ADULT));
    await stored('i2', item(NEIGHBOUR), asked(NEIGHBOUR, ADULT));
    const mine = (who) => getDocs(query(collection(as(env, who), 'lendLoans'), where('parties', 'array-contains', who.memberId)));
    assert.deepEqual((await assertSucceeds(mine(ADULT))).docs.map((d) => d.id).sort(), ['i1', 'i2']);
    assert.deepEqual((await assertSucceeds(mine(OTHER_ADULT))).docs.map((d) => d.id), ['i1']);
    assert.equal((await assertSucceeds(mine(KID))).size, 0);
    await assertFails(getDocs(collection(as(env, NEIGHBOUR), 'lendLoans')));
    await assertFails(getDocs(query(collection(as(env, NEIGHBOUR), 'lendLoans'), where('parties', 'array-contains', 'adult2'))));
    // The admin principal, for the export, and to see the app as someone else.
    assert.equal((await assertSucceeds(getDocs(collection(as(env, ADMIN), 'lendLoans')))).size, 2);
  });
});

describe('« Je voudrais l\'emprunter » (récit 51)', () => {
  test('an adult who is not the owner asks, in their own name, with or without dates', async () => {
    await stored('i1', item(ADULT));
    await stored('i2', item(ADULT));
    await assertSucceeds(askBatch(NEIGHBOUR, 'i1', ask(ADULT, NEIGHBOUR)));
    await assertSucceeds(askBatch(OTHER_ADULT, 'i2', ask(ADULT, OTHER_ADULT, {
      from: Timestamp.fromMillis(Date.now() + DAY), until: Timestamp.fromMillis(Date.now() + 4 * DAY),
    })));
  });

  test('the ask and the thing taken go together, never one without the other', async () => {
    await stored('i1', item(ADULT));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'lendLoans/i1'), ask(ADULT, NEIGHBOUR)));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'lendItems/i1'), flip(true)));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'lendLoans/none'), ask(ADULT, NEIGHBOUR)));
  });

  test('never the owner, a child, someone else\'s name, nor a thing already asked or lent', async () => {
    await stored('i1', item(ADULT));
    await assertFails(askBatch(ADULT, 'i1', ask(ADULT, ADULT)));
    await assertFails(askBatch(KID, 'i1', ask(ADULT, KID)));
    await assertFails(askBatch(NEIGHBOUR, 'i1', ask(ADULT, OTHER_ADULT)));
    await assertFails(askBatch(NEIGHBOUR, 'i1', ask(ADULT, NEIGHBOUR, { borrowerName: 'Autre' })));
    await assertFails(askBatch(NEIGHBOUR, 'i1', ask(ADULT, NEIGHBOUR, { lentAt: serverTimestamp() })));
    await assertFails(askBatch(NEIGHBOUR, 'i1', ask(ADULT, NEIGHBOUR, { status: 'lent' })));
    await assertFails(askBatch(NEIGHBOUR, 'i1', ask(OTHER_ADULT, NEIGHBOUR)));
    await assertFails(askBatch(NEIGHBOUR, 'i1', ask(ADULT, NEIGHBOUR, { parties: ['adult1', 'adult3', 'adult2'] })));
    await stored('i2', item(ADULT), asked(ADULT, OTHER_ADULT));
    await assertFails(askBatch(NEIGHBOUR, 'i2', ask(ADULT, NEIGHBOUR)));
  });

  test('dates within a year, the return not before the start', async () => {
    await stored('i1', item(ADULT));
    await assertFails(askBatch(NEIGHBOUR, 'i1', ask(ADULT, NEIGHBOUR, {
      from: Timestamp.fromMillis(Date.now() + 5 * DAY), until: Timestamp.fromMillis(Date.now() + 2 * DAY),
    })));
    await assertFails(askBatch(NEIGHBOUR, 'i1', ask(ADULT, NEIGHBOUR, { until: Timestamp.fromMillis(Date.now() + 400 * DAY) })));
    await assertFails(askBatch(NEIGHBOUR, 'i1', ask(ADULT, NEIGHBOUR, { until: Timestamp.fromMillis(Date.now() - 3 * DAY) })));
    await assertFails(askBatch(NEIGHBOUR, 'i1', ask(ADULT, NEIGHBOUR, { until: 'demain' })));
  });
});

describe('« D\'accord », « Pas cette fois » and « Rendu » (récit 51)', () => {
  const accept = { status: 'lent', lentAt: serverTimestamp(), updatedAt: serverTimestamp() };

  test('the owner says « D\'accord »: the thing is lent to the one who asked', async () => {
    await stored('i1', item(ADULT), asked(ADULT, OTHER_ADULT));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'lendLoans/i1'), accept));
    await assertFails(updateDoc(doc(as(env, ADULT), 'lendLoans/i1'), { ...accept, borrowerId: 'adult3' }));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'lendLoans/i1'), accept));
    await assertFails(updateDoc(doc(as(env, ADULT), 'lendLoans/i1'), accept));
  });

  test('« Pas cette fois » by the owner, or the borrower drops the ask; nobody else', async () => {
    await stored('i1', item(ADULT), asked(ADULT, OTHER_ADULT));
    await stored('i2', item(ADULT), asked(ADULT, OTHER_ADULT));
    await assertFails(releaseBatch(NEIGHBOUR, 'i1'));
    await assertFails(releaseBatch(ADMIN, 'i1'));
    await assertSucceeds(releaseBatch(ADULT, 'i1'));
    await assertSucceeds(releaseBatch(OTHER_ADULT, 'i2'));
  });

  test('« Rendu » by the owner or the borrower frees the thing, loan and flag together', async () => {
    await stored('i1', item(ADULT), lentTo(ADULT, OTHER_ADULT));
    await stored('i2', item(ADULT), lentTo(ADULT, OTHER_ADULT));
    await assertFails(releaseBatch(NEIGHBOUR, 'i1'));
    await assertFails(deleteDoc(doc(as(env, ADULT), 'lendLoans/i1')));
    await assertFails(updateDoc(doc(as(env, ADULT), 'lendItems/i1'), flip(false)));
    await assertSucceeds(releaseBatch(OTHER_ADULT, 'i1'));
    await assertSucceeds(releaseBatch(ADULT, 'i2'));
  });

  test('a thing left « Prêté » with no loan: its owner frees it, nobody else', async () => {
    await stored('i1', item(ADULT), null);
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), 'lendItems/i1'), { lent: true });
    });
    await assertFails(releaseBatch(NEIGHBOUR, 'i1'));
    await assertFails(releaseBatch(OTHER_ADULT, 'i1'));
    await assertSucceeds(releaseBatch(ADULT, 'i1'));
  });

  test('nobody writes the reminder but the server, nor changes the dates of a loan', async () => {
    await stored('i1', item(ADULT), lentTo(ADULT, OTHER_ADULT));
    await assertFails(updateDoc(doc(as(env, ADULT), 'lendLoans/i1'), { remindedAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'lendLoans/i1'), { until: Timestamp.fromMillis(Date.now() + 30 * DAY), updatedAt: serverTimestamp() }));
  });
});
